"""Content boundaries, source identity and interactive placement for all 317 Qs."""
import hashlib
import json
import re
import subprocess
from pathlib import Path
import fitz
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
manifest=json.loads((ROOT/'qa/source/native-first-five-manifest.json').read_text())
records={r['id']:r for r in manifest['questions']}
records.update({r['id']:r for r in json.loads((ROOT/'qa/source/native-chapters-six-to-ten-manifest.json').read_text())['questions']})
records.update({r['id']:r for r in json.loads((ROOT/'qa/source/native-chapters-eleven-to-fifteen-manifest.json').read_text())['questions']})
assert len(records)==317
source={r['id']:r for r in json.loads((ROOT/'qa/source/all-chapters-index.json').read_text())}
from native_first_five import MCQ as FIRST_MCQ
from native_chapters_six_to_ten import MCQ as NEXT_MCQ
from native_chapters_eleven_to_fifteen import MCQ as LAST_MCQ
printed_mcq={**FIRST_MCQ,**NEXT_MCQ,**LAST_MCQ}
counts=[];figures=[];tables=0;maths=0;slots=0
pdf=fitz.open(ASSETS/'source/workbook.pdf')
for ch in range(1,16):
    qs=json.loads((ASSETS/f'content/chapter{ch}.json').read_text());counts.append(len(qs))
    for q in qs:
        cid=q['canonicalId']; r=records[cid]
        assert r['questionId']==q['id'] and r['sourceQuestion']==q['sourceQuestionVisual'] and r['sourceAnswer']==q['answerVisual']
        payload=json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()
        assert hashlib.sha256(payload).hexdigest()==r['nativeSha256'],cid
        assert q['nativeQuestion'] and q['nativeAnswer']
        assert all(b['type']in ['text','math','table','figure']for b in q['nativeAnswer']),cid
        for b in q['nativeQuestion']+q['nativeAnswer']:
            assert b['type'] in ['text','cloze','figure','input','table','math','reaction','flow','barChart']
            if b['type'] in ['cloze','reaction','flow','input','barChart']:assert b.get('step',0)in range(len(q['steps']))
            if b['type']=='figure':
                a=b['asset'];assert a not in [q['sourceQuestionVisual'],q['answerVisual']],cid
                if a.startswith('pdf:'):
                    file=a[4:].split('#')[0]
                    assert file in ['workbook.pdf','native_diagrams_11_15.pdf']or (cid=='g7_ch10_q13' and file=='answers10.pdf')
                    regions=a.split('#')[1].split('|')
                    p,x0,y0,x1,y1=map(float,regions[0].split(',')); rect=fitz.Rect(x0,y0,x1,y1)
                    doc=pdf if file=='workbook.pdf' else fitz.open(ASSETS/'source'/file)
                    assert p==int(p) and rect.width>0 and rect.height>0 and doc[int(p)-1].rect.contains(rect)
                    for exclusion in regions[1:]:
                        assert exclusion.startswith('-')
                        ep,*bb=map(float,exclusion[1:].split(','))
                        assert ep==int(ep) and doc[int(ep)-1].rect.contains(fitz.Rect(bb))
                elif a.startswith('crop:'):
                    filename,coords=a[5:].split('#'); x0,y0,x1,y1=map(int,coords.split(','))
                    w,h=Image.open(ASSETS/'images'/filename).size
                    assert 0<=x0<x1<=w and 0<=y0<y1<=h
                else:assert (ASSETS/'images'/a).exists(),a
                figures.append(dict(question=cid,asset=a))
            if b['type']=='table':
                tables+=1;assert b['headers'] and all(len(row)==len(b['headers'])for row in b['rows'])
                for cell in [c for row in b['rows']for c in row]:
                    for token in re.finditer(r'\{\{(\d+)(?::(\d+))?}}',cell):
                        s=q['steps'][int(token[1])]
                        if token[2]:assert int(token[2])in range(len(s.get('pairs',s.get('items',[]))))
            if b['type']in ['cloze','reaction','flow']:
                s=q['steps'][b.get('step',0)];assert s['kind']=='match'
                for index in re.findall(r'\{\{(\d+)}}',b['text']):assert int(index)in range(len(s['pairs']));slots+=1
            if b['type']=='math':
                maths+=1
                for part in b['parts']:assert bool(part.get('text'))or(bool(part.get('numerator'))and bool(part.get('denominator')))
        if q['section']=='چهارگزینه‌ای':
            key=printed_mcq[ch][q['sourceNumber']-1][2]
            if 'keyLetter'in source[cid]:assert key==source[cid]['keyLetter']
            if cid=='g7_ch13_mcq03':
                assert q['steps'][0]['kind']=='match' and [p['right']for p in q['steps'][0]['pairs']]==['آسیا','پیش','نیش']
                assert q['printedOptions']==[l+'- '+v for l,v in zip(['الف','ب','ج','د'],printed_mcq[13][2][1])]
                assert not q['practiceUsesPrintedKey'] and 'تأیید مؤلف'in q['sourceReviewNote']
            else:
                assert all(re.match(r'^(الف|ب|ج|د)- .+',o['text'])for o in q['steps'][0]['options'])
                assert q['steps'][0].get('preserveOptionOrder'),cid
                assert [o['text'].split('-')[0]for o in q['steps'][0]['options']]==['الف','ب','ج','د'],cid
                practice_key=next(o['text'].split('-')[0]for o in q['steps'][0]['options']if o['correct'])
                assert practice_key==('ج' if cid=='g7_ch10_mcq04' else key)
            assert q['nativeAnswer']==[dict(type='text',text=key)]
assert counts==[8,20,31,23,22,21,34,26,18,29,13,20,17,15,20]
from native_scientific_figures import decode_original
extracts=json.loads((ROOT/'qa/source/native-scientific-extractions.json').read_text())
assert len(extracts)==19
for entry in extracts:
    path=ASSETS/'images'/entry['asset'];assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['webpSha256']
    original=decode_original(pdf,entry['xref'],entry['smask']);decoded=Image.open(path).convert('RGBA')
    assert original.size==decoded.size==(entry['width'],entry['height'])
    assert original.tobytes()==decoded.tobytes()
    assert hashlib.sha256(decoded.tobytes()).hexdigest()==entry['decodedRgbaSha256']
biology=json.loads((ROOT/'qa/source/native-biology-diagrams.json').read_text())
derived_path=ASSETS/'source/native_diagrams_11_15.pdf'
assert hashlib.sha256((ASSETS/'source/workbook.pdf').read_bytes()).hexdigest()==biology['sourceWorkbookSha256']
assert hashlib.sha256(derived_path.read_bytes()).hexdigest()==biology['derivedPdfSha256']
derived=fitz.open(derived_path);assert len(derived)==len(biology['figures'])==4
image_hashes=set()
for page in derived:
    for im in page.get_images():image_hashes.add(hashlib.sha256(decode_original(derived,im[0],im[1]).tobytes()).hexdigest())
assert image_hashes=={r['decodedRgbaSha256']for r in biology['images']}
source_images=set()
for pn in {r['sourcePage']for r in biology['figures']}:
    for im in pdf[pn-1].get_images():source_images.add(hashlib.sha256(decode_original(pdf,im[0],im[1]).tobytes()).hexdigest())
assert image_hashes<=source_images
for r in biology['figures']:
    assert r['removedTextSpans']
    q=next(q for q in json.loads((ASSETS/f'content/chapter{int(r["question"][5:7])}.json').read_text())if q['canonicalId']==r['question'])
    assert [b['asset']for b in q['nativeQuestion']if b['type']=='figure']==[r['asset']]
# New native/practice fields may change; the immutable source fields may not.
baseline='7fc2ab9b6fa9a5ecb61a63a6950cf270df45d982'
for ch in range(1,16):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    previous={q['id']:q for q in json.loads(subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT))}
    for q in json.loads((ROOT/path).read_text()):
        for field in ['bookPrompt','sourceQuestionVisual','answerVisual','sourceAnswerTextExtracted']:
            assert q.get(field)==previous[q['id']].get(field),(q['canonicalId'],field,'original source field changed')
        for step in q['steps']:
            if step['kind']in['match','classify']:assert step.get('allowTokenReuse',True)
            choices=[o['text']for o in step.get('options',[])]+step.get('choices',[])+[c for p in step.get('pairs',[])for c in p.get('choices',[])]
            assert not any('این اتفاق نمی افتد و برعکس است' in c for c in choices)
editorial=json.loads((ROOT/'qa/source/native-editorial-revisions-v10.json').read_text())
approved={r['id']:r for r in editorial['revisions']}
native_hash=lambda q:hashlib.sha256(json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()).hexdigest()
# Retain the old layout guard. Only individually listed, exact before/after
# revisions authorized by the owner's new review request may differ.
seen=set()
v11=json.loads((ROOT/'qa/source/native-editorial-revisions-v11.json').read_text())
for ch in range(1,16):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    reference='3b076647f1eb50ced51fc69f792943fe0f394fb0' if ch<=10 else editorial['baseline']
    previous=json.loads(subprocess.check_output(['git','show',reference+':'+path],cwd=ROOT))
    retained=json.loads(subprocess.check_output(['git','show',v11['baseline']+':'+path],cwd=ROOT))
    for q,old in zip(retained,previous):
        cid=q['canonicalId']
        if q['nativeQuestion']!=old['nativeQuestion'] or q['nativeAnswer']!=old['nativeAnswer']:
            assert cid in approved,(cid,'unlisted native revision')
            assert native_hash(old)==approved[cid]['beforeNativeSha256'],(cid,'unexpected original native content')
            assert native_hash(q)==approved[cid]['afterNativeSha256'],(cid,'unexpected revised native content')
            assert approved[cid]['reasons'],cid
            seen.add(cid)
assert seen==set(approved)
latest={r['id']:r for r in v11['revisions']};seen_latest=set()
for ch in range(1,16):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    old={q['canonicalId']:q for q in json.loads(subprocess.check_output(['git','show',v11['baseline']+':'+path],cwd=ROOT))}
    for q in json.loads((ROOT/path).read_text()):
        cid=q['canonicalId']
        if native_hash(q)!=native_hash(old[cid]):
            assert cid in latest,(cid,'unlisted v11 native revision')
            assert native_hash(old[cid])==latest[cid]['beforeNativeSha256']
            assert native_hash(q)==latest[cid]['afterNativeSha256'] and latest[cid]['reasons']
            seen_latest.add(cid)
assert seen_latest==set(latest)
all_questions={q['canonicalId']:q for ch in range(1,16)for q in json.loads((ASSETS/f'content/chapter{ch}.json').read_text())}
original_tables=json.loads((ROOT/'qa/source/original-tables-v11.json').read_text())
assert len(original_tables['printedTables'])==5
assert original_tables['sourceWorkbookSha256']==hashlib.sha256((ASSETS/'source/workbook.pdf').read_bytes()).hexdigest()
for r in original_tables['printedTables']:
    blocks=all_questions[r['question']]['nativeQuestion']
    i=next(i for i,b in enumerate(blocks)if b['type']=='figure'and b.get('text')=='جدول اصلی کتاب‌کار')
    assert blocks[i]['asset']==r['asset'] and blocks[i+1]['type']=='table'
report=dict(status='PASS',edition=1405,nativeQuestions=sum(counts),nativeAnswers=sum(counts),chapterCounts=counts,
    nativeTables=tables,nativeMathBlocks=maths,inlinePrintedBlanks=slots,standaloneScientificFigures=figures,
    fullQuestionOrKeyScreenshots=0,originalEmbeddedFiguresPixelIdentical=len(extracts),originalQuestionAndKeyFieldsUnchanged=True,reusableSelectionBanks=True,explicitlyReviewedNativeRevisions=len(approved),ownerPresentationRevisionsV11=len(latest),originalPrintedTablesBeforeNative=5,printedMCQOrder=True,unlistedNativeChanges=0,
    isolatedBiologyDiagramPages=4,derivedDiagramEmbeddedImagesPixelIdentical=len(image_hashes),
    sourceReview='All workbook pages 2–59 and chapter answer PDFs 01–15 visually reviewed; practice revisions and printed-key conflicts are separately recorded.')
(ROOT/'qa/grade7_native_content_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items()if k!='standaloneScientificFigures'},ensure_ascii=False))
