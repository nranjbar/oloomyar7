"""Apply owner-supplied figures after transcription; keep print keys immutable."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'app/src/main/assets'

def write():
    rows=json.loads((ROOT/'qa/source/owner-art-2026-10-21.json').read_text())['scientific']
    for ch in range(1,16):
        path=ASSETS/f'content/chapter{ch}.json';qs=json.loads(path.read_text())
        for q in qs:
            entries=[r for r in rows if r['question']==q['canonicalId']]
            for row in entries:
                if row['role']=='answer':
                    q['answerStudyVisual']=row['asset']
                    captions={'g7_ch02_q07':'این تصویر یک نمونه از روش اندازه‌گیری است؛ عددها، مثال آموزشی‌اند.',
                        'g7_ch02_q14':'عددهای اعشاری تصویر، روش گردکردن را نشان می‌دهند؛ پاسخ نهایی شکل‌های ۱ و ۲ به‌ترتیب ۱۶ و ۱۲ سانتی‌متر است.'}
                    if q['canonicalId']in captions:q['answerStudyCaption']=captions[q['canonicalId']]
                elif row.get('applyFullQuestionFigure'):
                    blocks=q['nativeQuestion'];indices=[i for i,b in enumerate(blocks)if b['type']=='figure']
                    assert indices,q['canonicalId']
                    first=indices[0]
                    q['nativeQuestion']=[b for b in blocks if b['type']!='figure']
                    q['nativeQuestion'].insert(first,dict(type='figure',asset=row['asset'],text=''))
        path.write_text(json.dumps(qs,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
