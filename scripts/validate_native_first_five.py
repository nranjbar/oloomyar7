"""Content boundaries, source identity and interactive placement for all 104 Qs."""
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
assert len(records)==104
counts=[];figures=[];tables=0;maths=0;slots=0
pdf=fitz.open(ASSETS/'source/workbook.pdf')
for ch in range(1,6):
    qs=json.loads((ASSETS/f'content/chapter{ch}.json').read_text());counts.append(len(qs))
    for q in qs:
        cid=q['canonicalId']; r=records[cid]
        assert r['questionId']==q['id'] and r['sourceQuestion']==q['sourceQuestionVisual'] and r['sourceAnswer']==q['answerVisual']
        payload=json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()
        assert hashlib.sha256(payload).hexdigest()==r['nativeSha256'],cid
        assert q['nativeQuestion'] and q['nativeAnswer']
        assert all(b['type']in ['text','math','table']for b in q['nativeAnswer']),cid
        for b in q['nativeQuestion']+q['nativeAnswer']:
            assert b['type'] in ['text','cloze','figure','input','table','math','reaction','barChart']
            if b['type'] in ['cloze','reaction','input','barChart']:assert b.get('step',0)in range(len(q['steps']))
            if b['type']=='figure':
                a=b['asset'];assert a not in [q['sourceQuestionVisual'],q['answerVisual']],cid
                if a.startswith('pdf:'):
                    assert a.startswith('pdf:workbook.pdf#')
                    p,x0,y0,x1,y1=map(float,a.split('#')[1].split(',')); rect=fitz.Rect(x0,y0,x1,y1)
                    assert p==int(p) and rect.width>0 and rect.height>0 and pdf[int(p)-1].rect.contains(rect)
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
                        if token[2]:assert int(token[2])in range(len(s['pairs']))
            if b['type']in ['cloze','reaction']:
                s=q['steps'][b.get('step',0)];assert s['kind']=='match'
                for index in re.findall(r'\{\{(\d+)}}',b['text']):assert int(index)in range(len(s['pairs']));slots+=1
            if b['type']=='math':
                maths+=1
                for part in b['parts']:assert bool(part.get('text'))or(bool(part.get('numerator'))and bool(part.get('denominator')))
        if q['section']=='چهارگزینه‌ای':
            assert all(re.match(r'^(الف|ب|ج|د)- .+',o['text'])for o in q['steps'][0]['options'])
            key=next(o['text'].split('-')[0]for o in q['steps'][0]['options']if o['correct'])
            assert q['nativeAnswer']==[dict(type='text',text=key)]
assert counts==[8,20,31,23,22]
# The remaining ten chapters must be byte-identical to the last accepted data.
baseline='7fc2ab9b6fa9a5ecb61a63a6950cf270df45d982'
for ch in range(6,16):
    path=f'app/src/main/assets/content/chapter{ch}.json'
    assert (ROOT/path).read_bytes()==subprocess.check_output(['git','show',baseline+':'+path],cwd=ROOT)
report=dict(status='PASS',edition=1405,nativeQuestions=sum(counts),nativeAnswers=sum(counts),chapterCounts=counts,
    nativeTables=tables,nativeMathBlocks=maths,inlinePrintedBlanks=slots,standaloneScientificFigures=figures,
    fullQuestionOrKeyScreenshots=0,chaptersSixToFifteenDataUnchanged=True,
    sourceReview='All workbook pages 2–19 and chapter answer PDFs 01–05 visually reviewed; PDF text-layer inventories are secondary checks, not OCR certification.')
(ROOT/'qa/grade7_native_content_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items()if k!='standaloneScientificFigures'},ensure_ascii=False))
