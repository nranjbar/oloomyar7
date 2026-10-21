"""Content boundaries, source identity and interactive placement for all 232 Qs."""
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
assert len(records)==232
source={r['id']:r for r in json.loads((ROOT/'qa/source/all-chapters-index.json').read_text())}
from native_first_five import MCQ as FIRST_MCQ
from native_chapters_six_to_ten import MCQ as NEXT_MCQ
printed_mcq={**FIRST_MCQ,**NEXT_MCQ}
counts=[];figures=[];tables=0;maths=0;slots=0
pdf=fitz.open(ASSETS/'source/workbook.pdf')
for ch in range(1,11):
    qs=json.loads((ASSETS/f'content/chapter{ch}.json').read_text());counts.append(len(qs))
    for q in qs:
        cid=q['canonicalId']; r=records[cid]
        assert r['questionId']==q['id'] and r['sourceQuestion']==q['sourceQuestionVisual'] and r['sourceAnswer']==q['answerVisual']
        payload=json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()
        assert hashlib.sha256(payload).hexdigest()==r['nativeSha256'],cid
        assert q['nativeQuestion'] and q['nativeAnswer']
        assert all(b['type']in ['text','math','table','figure']for b in q['nativeAnswer']),cid
        for b in q['nativeQuestion']+q['nativeAnswer']:
            assert b['type'] in ['text','cloze','figure','input','table','math','reaction','barChart']
            if b['type'] in ['cloze','reaction','input','barChart']:assert b.get('step',0)in range(len(q['steps']))
            if b['type']=='figure':
                a=b['asset'];assert a not in [q['sourceQuestionVisual'],q['answerVisual']],cid
                if a.startswith('pdf:'):
                    file=a[4:].split('#')[0]
                    assert file=='workbook.pdf' or (cid=='g7_ch10_q13' and file=='answers10.pdf')
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
            if b['type']in ['cloze','reaction']:
                s=q['steps'][b.get('step',0)];assert s['kind']=='match'
                for index in re.findall(r'\{\{(\d+)}}',b['text']):assert int(index)in range(len(s['pairs']));slots+=1
            if b['type']=='math':
                maths+=1
                for part in b['parts']:assert bool(part.get('text'))or(bool(part.get('numerator'))and bool(part.get('denominator')))
        if q['section']=='چهارگزینه‌ای':
            assert all(re.match(r'^(الف|ب|ج|د)- .+',o['text'])for o in q['steps'][0]['options'])
            key=printed_mcq[ch][q['sourceNumber']-1][2]
            if 'keyLetter'in source[cid]:assert key==source[cid]['keyLetter']
            practice_key=next(o['text'].split('-')[0]for o in q['steps'][0]['options']if o['correct'])
            assert practice_key==('ج' if cid=='g7_ch10_mcq04' else key)
            assert q['nativeAnswer']==[dict(type='text',text=key)]
assert counts==[8,20,31,23,22,21,34,26,18,29]
from native_scientific_figures import decode_original
extracts=json.loads((ROOT/'qa/source/native-scientific-extractions.json').read_text())
assert len(extracts)==17
for entry in extracts:
    path=ASSETS/'images'/entry['asset'];assert hashlib.sha256(path.read_bytes()).hexdigest()==entry['webpSha256']
    original=decode_original(pdf,entry['xref'],entry['smask']);decoded=Image.open(path).convert('RGBA')
    assert original.size==decoded.size==(entry['width'],entry['height'])
    assert original.tobytes()==decoded.tobytes()
    assert hashlib.sha256(decoded.tobytes()).hexdigest()==entry['decodedRgbaSha256']
# The remaining five chapters must be byte-identical to the last accepted data.
baseline='7fc2ab9b6fa9a5ecb61a63a6950cf270df45d982'
for ch in range(1,11):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    previous={q['id']:q for q in json.loads(subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT))}
    for q in json.loads((ROOT/path).read_text()):
        for field in ['bookPrompt','sourceQuestionVisual','answerVisual','sourceAnswerTextExtracted']:
            assert q.get(field)==previous[q['id']].get(field),(q['canonicalId'],field,'original source field changed')
        for step in q['steps']:
            if step['kind']in['match','classify']:assert step.get('allowTokenReuse',True)
            choices=[o['text']for o in step.get('options',[])]+step.get('choices',[])+[c for p in step.get('pairs',[])for c in p.get('choices',[])]
            assert not any('این اتفاق نمی افتد و برعکس است' in c for c in choices)
for ch in range(11,16):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    assert (ROOT/path).read_bytes()==subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT)
report=dict(status='PASS',edition=1405,nativeQuestions=sum(counts),nativeAnswers=sum(counts),chapterCounts=counts,
    nativeTables=tables,nativeMathBlocks=maths,inlinePrintedBlanks=slots,standaloneScientificFigures=figures,
    fullQuestionOrKeyScreenshots=0,originalEmbeddedFiguresPixelIdentical=len(extracts),originalQuestionAndKeyFieldsUnchanged=True,reusableSelectionBanks=True,chaptersElevenToFifteenDataUnchanged=True,
    sourceReview='All workbook pages 2–44 and chapter answer PDFs 01–10 visually reviewed; practice revisions and printed-key conflicts are separately recorded.')
(ROOT/'qa/grade7_native_content_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items()if k!='standaloneScientificFigures'},ensure_ascii=False))
