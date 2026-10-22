"""Import the 26 chapter 11–15 answers and the owner's corrected 7/14 answer.

Only these 27 derivatives are encoded. Existing figures, dimensions, lossless
source crops and UI icons are retained. Originals are identified by SHA-256;
their supplied archives need not be packaged in the offline Android app.
"""
from pathlib import Path
from PIL import Image
import argparse, hashlib, io, json, re, zipfile
from prepare_owner_art_v10 import encode, luma_ssim

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'app/src/main/assets'
sha = lambda b: hashlib.sha256(b).hexdigest()

def write(archive, corrected):
    manifest_path = ROOT / 'qa/source/owner-art-2026-10-21.json'
    m = json.loads(manifest_path.read_text())
    report_path = ROOT / 'qa/source/image-optimization-v11.json'
    checkpoint = ROOT / 'tmp/owner-v11/encoding-checkpoint.json'
    checkpoint.parent.mkdir(parents=True, exist_ok=True)
    cache = json.loads(checkpoint.read_text()) if checkpoint.exists() else {}
    before = cache.setdefault('baseline', {p.name:p.stat().st_size for p in (A/'images').glob('*.webp')})
    cache.setdefault('encoded', {})
    with zipfile.ZipFile(archive) as z:
        members = {Path(n).name:n for n in z.namelist() if re.fullmatch(r'g7_ch(?:11|12|13|14|15)_(?:q|mcq)\d{2}_answer.png',Path(n).name)}
        assert len(members) == 26, sorted(members)
        inputs = [(Path(n).stem.removesuffix('_answer'), z.read(member), member, 'answers11-15.zip') for n, member in sorted(members.items())]
    inputs.append(('g7_ch07_q14', corrected.read_bytes(), corrected.name, corrected.name))
    index = {(r['question'],r['role']):r for r in m['scientific']}
    records = []
    for cid, raw, member, source in inputs:
        original = Image.open(io.BytesIO(raw)).convert('RGBA')
        row = index.get((cid,'answer'))
        if row is None:
            row = dict(question=cid, role='answer', asset=f'owner_20261022_{cid}_answer.webp', applyFullQuestionFigure=False)
            m['scientific'].append(row)
        target = A/'images'/row['asset']
        cached = cache['encoded'].get(cid)
        if cached and cached['sourceSha256'] == sha(raw) and target.exists() and sha(target.read_bytes()) == cached['webpSha256']:
            encoded, settings = target.read_bytes(), cached['encoding']
        else:
            encoded, settings = encode(original)
            target.write_bytes(encoded)
        decoded = Image.open(io.BytesIO(encoded)).convert('RGBA')
        assert decoded.size == original.size
        assert decoded.getchannel('A').tobytes() == original.getchannel('A').tobytes()
        assert settings['lumaSsim'] >= .995
        row.update(originalRepositoryPath=None, sourceFilename=member,
            deliveredSource=f'{source}:{member}' if source != member else source,
            sourceSha256=sha(raw),sourceRgbaSha256=sha(original.tobytes()),
            width=original.width,height=original.height,webpSha256=sha(encoded),
            decodedRgbaSha256=sha(decoded.tobytes()),encoding=settings,
            sourceBytes=len(raw),encodedBytes=len(encoded))
        cache['encoded'][cid] = row.copy()
        checkpoint.write_text(json.dumps(cache,ensure_ascii=False,indent=2)+'\n')
        record = dict(question=cid,asset=row['asset'],sourceBytes=len(raw),
            previousBytes=before.get(row['asset'],0),bytes=len(encoded),**settings)
        records.append(record)
        print(json.dumps(record),flush=True)
    assert len(m['scientific']) == 151
    assert sum(r['role']=='answer' for r in m['scientific']) == 97
    m['additionalDeliveries'] = [dict(filename=archive.name,sha256=sha(archive.read_bytes()),answerImages=26),
        dict(filename=corrected.name,sha256=sha(corrected.read_bytes()),replaces='g7_ch07_q14:answer')]
    m['provenance'] += (' Additional delivery on 2026-10-22: answers11-15.zip (26 answers) and corrected g7_ch07_q14_answer.png.'
        if 'Additional delivery on 2026-10-22' not in m['provenance'] else '')
    text=json.dumps(m,ensure_ascii=False,indent=2)+'\n'
    manifest_path.write_text(text);(A/'content/owner-scientific-art.json').write_text(text)
    dimensions={p.name:list(Image.open(p).size) for p in sorted((A/'images').glob('*.webp'))}
    (A/'content/image-dimensions.json').write_text(json.dumps(dimensions,indent=2)+'\n')
    report=dict(policy=m['optimizationPolicy'],deliveries=m['additionalDeliveries'],
        beforeAssets=len(before),beforeImageBytes=sum(before.values()),afterAssets=len(dimensions),
        afterImageBytes=sum(p.stat().st_size for p in (A/'images').glob('*.webp')),
        addedAnswerImages=26,replacedAnswerImages=1,assets=records)
    report_path.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='assets'}),flush=True)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('archive',type=Path);p.add_argument('corrected',type=Path)
    args=p.parse_args();write(args.archive,args.corrected)
