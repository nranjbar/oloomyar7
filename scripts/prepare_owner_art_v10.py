"""Import the owner's complete archive and encode reviewed, full-resolution derivatives.

Run only with the delivered original archive. Scientific images are never resized.
Small source diagrams, extracted PDF figures and UI icons remain lossless. Larger
illustrations use adaptive WebP with measured luminance SSIM >= .995, otherwise
lossless. Both original and derivative hashes are retained, without claiming that
lossy derivatives have identical source pixels.
"""
from pathlib import Path
from PIL import Image
import argparse, hashlib, io, json, re, zipfile
import numpy as np
from scipy.ndimage import uniform_filter

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
IMAGES = ASSETS / 'images'
sha = lambda b: hashlib.sha256(b).hexdigest()

def luma_ssim(a, b):
    weights = np.array([.2126, .7152, .0722], dtype=np.float32)
    a = (np.asarray(a.convert('RGB'), dtype=np.float32) * weights).sum(axis=2)
    b = (np.asarray(b.convert('RGB'), dtype=np.float32) * weights).sum(axis=2)
    ua, ub = uniform_filter(a, 7), uniform_filter(b, 7)
    va = np.maximum(0, (uniform_filter(a*a, 7)-ua*ua)*49/48)
    vb = np.maximum(0, (uniform_filter(b*b, 7)-ub*ub)*49/48)
    cov = (uniform_filter(a*b, 7)-ua*ub)*49/48
    score = ((2*ua*ub+6.5025)*(2*cov+58.5225))/((ua*ua+ub*ub+6.5025)*(va+vb+58.5225))
    return float(score[3:-3,3:-3].mean())

def encode(im, *, scientific=True):
    original = im.convert('RGBA')
    candidates = []
    # Retain alpha values exactly; WebP alpha is independently lossless.
    lossless = io.BytesIO(); original.save(lossless, 'WEBP', lossless=True, quality=100, method=6, exact=True)
    candidates.append((lossless.getvalue(), {'mode':'lossless','quality':100,'lumaSsim':1.0}))
    if scientific and (max(im.size) < 650 or len(lossless.getvalue()) < 120000):
        return candidates[0]
    threshold = .995 if scientific else .99
    for quality in ([94,98,100] if scientific else [90,94,98]):
        output = io.BytesIO(); original.save(output,'WEBP',quality=quality,method=6,exact=True)
        encoded = output.getvalue(); decoded = Image.open(io.BytesIO(encoded)).convert('RGBA')
        assert np.array_equal(np.asarray(original)[:,:,3], np.asarray(decoded)[:,:,3])
        score = luma_ssim(original, decoded)
        if score >= threshold:
            candidates.append((encoded, {'mode':'lossy','quality':quality,'lumaSsim':round(score,8)}))
            break
    return min(candidates, key=lambda c:len(c[0]))

def write(archive, resume_log=None, baseline_profile=None):
    manifest_path = ROOT/'qa/source/owner-art-2026-10-21.json'
    m = json.loads(manifest_path.read_text())
    before = {p.name:p.stat().st_size for p in IMAGES.glob('*.webp')}
    if baseline_profile:
        before={r['name']:r['bytes'] for r in json.loads(Path(baseline_profile).read_text())}
    cached={}
    if resume_log:
        for line in Path(resume_log).read_text().splitlines():
            row=json.loads(line)
            if 'asset' in row:cached[row['asset']]=row
    records = []
    with zipfile.ZipFile(archive) as z:
        by_name = {Path(n).name:n for n in z.namelist() if n.lower().endswith('.png')}
        existing = {(r['question'],r['role']):r for r in m['scientific']}
        for filename, member in sorted(by_name.items()):
            match = re.fullmatch(r'(g7_ch\d\d_(?:q|mcq)\d\d)(?:_[abc])?_(answer|question)\.png', filename)
            if not match: continue
            question, role = match.groups()
            if role != 'answer' and question not in ('g7_ch04_q13','g7_ch06_q10'): continue
            if (question,role) not in existing:
                row = dict(question=question,role=role,asset='owner_20261021_'+Path(filename).stem+'.webp',
                    originalRepositoryPath=None,sourceFilename=member,deliveredSource=member,
                    applyFullQuestionFigure=role=='question')
                m['scientific'].append(row);existing[(question,role)]=row
        for row in m['scientific']:
            original_path = row['sourceFilename']
            member = by_name.get(Path(original_path).name)
            assert member, original_path
            raw = z.read(member); im=Image.open(io.BytesIO(raw)).convert('RGBA')
            assert not row.get('sourceSha256') or row['sourceSha256']==sha(raw), member
            original_rgba_hash=sha(im.tobytes())
            target=IMAGES/row['asset']
            previous=cached.get(row['asset'])
            if previous and target.exists():
                encoded=target.read_bytes();reviewed=Image.open(io.BytesIO(encoded)).convert('RGBA')
                assert reviewed.size==im.size and len(encoded)==previous['bytes']
                assert previous['sourceBytes']==len(raw)
                score=1.0 if reviewed.tobytes()==im.tobytes() else luma_ssim(im,reviewed)
                assert score>=.995 and reviewed.getchannel('A').tobytes()==im.getchannel('A').tobytes()
                settings={k:previous[k]for k in ['mode','quality','lumaSsim']}
                assert abs(score-settings['lumaSsim'])<.000001
            else:
                encoded, settings=encode(im)
                target.write_bytes(encoded)
            decoded=Image.open(target).convert('RGBA')
            row.update(sourceSha256=sha(raw),sourceRgbaSha256=original_rgba_hash,
                width=im.width,height=im.height,webpSha256=sha(encoded),
                decodedRgbaSha256=sha(decoded.tobytes()),encoding=settings,
                sourceBytes=len(raw),encodedBytes=len(encoded))
            records.append(dict(asset=row['asset'],role=row['role'],sourceBytes=len(raw),
                previousBytes=before.get(row['asset'],0),bytes=len(encoded),**settings))
            print(json.dumps(records[-1]),flush=True)
    # All remaining raster images are considered. Keep source/PDF figures and icons
    # exact; only non-scientific chapter covers and mascots are eligible for lossy.
    for p in sorted(IMAGES.glob('*.webp')):
        if p.name in {r['asset'] for r in m['scientific']}: continue
        if p.name.startswith(('chapter_','mascot_')):
            original=p.read_bytes();im=Image.open(p).convert('RGBA');data,settings=encode(im,scientific=False)
            if len(data)<len(original):p.write_bytes(data)
            else:settings=dict(mode='retained-original',lumaSsim=1.0)
            records.append(dict(asset=p.name,role='decorative',previousBytes=len(original),bytes=p.stat().st_size,**settings))
        else:
            records.append(dict(asset=p.name,role='retained-lossless',previousBytes=p.stat().st_size,bytes=p.stat().st_size,mode='unchanged',lumaSsim=1.0))
    assert len(m['scientific'])==125
    assert sum(r['role']=='answer' for r in m['scientific'])==71
    m.update(archiveSha256=sha(Path(archive).read_bytes()),
        provenance='Owner originals are retained in Grade7_Images_Review-All.zip. Full resolution is preserved. Small scientific figures remain pixel-identical lossless WebP; large illustrations use measured adaptive WebP (luminance SSIM >= 0.995) or lossless. Original and reviewed derivative pixel hashes are recorded separately.',
        optimizationPolicy=dict(scientificMinLumaSsim=.995,scientificResize=False,alpha='exact',smallDiagrams='lossless',pdfFigures='unchanged lossless',uiIcons='unchanged lossless'))
    text=json.dumps(m,ensure_ascii=False,indent=2)+'\n'
    manifest_path.write_text(text);(ASSETS/'content/owner-scientific-art.json').write_text(text)
    dimensions={p.name:list(Image.open(p).size) for p in sorted(IMAGES.glob('*.webp'))}
    (ASSETS/'content/image-dimensions.json').write_text(json.dumps(dimensions,indent=2)+'\n')
    report=dict(policy=m['optimizationPolicy'],archiveSha256=m['archiveSha256'],
        beforeAssets=len(before),beforeImageBytes=sum(before.values()),
        afterAssets=len(dimensions),afterImageBytes=sum(p.stat().st_size for p in IMAGES.glob('*.webp')),
        addedAnswerImages=64,addedQuestionImages=2,assets=records)
    (ROOT/'qa/source/image-optimization-v10.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='assets'}),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('archive',type=Path)
    parser.add_argument('--resume-log',type=Path);parser.add_argument('--baseline-profile',type=Path)
    args=parser.parse_args();write(args.archive,args.resume_log,args.baseline_profile)
