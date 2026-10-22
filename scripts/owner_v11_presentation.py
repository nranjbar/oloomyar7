"""Apply the owner's 2026-10-22 presentation decisions after native generation.

The source PDF and all four immutable question/key fields remain untouched.
School-level simplifications are preserved; this is not a new science rewrite.
"""
from pathlib import Path
import hashlib, json, subprocess

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'app/src/main/assets'
BASELINE='c4c942100ce0365e9e865e9e254e4a0ef1ad27f3'
TABLES={
    'g7_ch02_q02':(3,29,343,555,645),
    'g7_ch04_q17':(15,53,64,566,172),
    'g7_ch05_q02':(16,28,366,568,470),
    'g7_ch05_q11':(17,392,563,535,690),
    'g7_ch08_q18':(33,46,449,220,581),
}
native_hash=lambda q:hashlib.sha256(json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()).hexdigest()

def write():
    manifest=json.loads((A/'content/owner-scientific-art.json').read_text())
    art={r['question']:r for r in manifest['scientific'] if r['role']=='answer'}
    revisions=[];tables=[];ordered=[]
    for ch in range(1,16):
        path=f'app/src/main/assets/content/chapter{ch}.json'
        qs=json.loads((ROOT/path).read_text())
        previous={q['canonicalId']:q for q in json.loads(subprocess.check_output(['git','show',BASELINE+':'+path],cwd=ROOT))}
        for q in qs:
            cid=q['canonicalId'];reasons=[]
            if q['section']=='چهارگزینه‌ای':
                for s in q['steps']:
                    if s.get('options'):
                        s['preserveOptionOrder']=True
                        ordered.append(dict(question=cid,step=s['id'],options=[o['text']for o in s['options']]))
            if cid in TABLES:
                bounds=TABLES[cid]
                asset='pdf:workbook.pdf#'+','.join(str(x) for x in bounds)
                blocks=[b for b in q['nativeQuestion']if b.get('text')!='جدول اصلی کتاب‌کار']
                i=next(i for i,b in enumerate(blocks)if b['type']=='table')
                blocks.insert(i,dict(type='figure',text='جدول اصلی کتاب‌کار',asset=asset))
                q['nativeQuestion']=blocks
                tables.append(dict(question=cid,questionId=q['id'],asset=asset,sourcePage=bounds[0],bounds=list(bounds[1:]),beforeNativeTable=True))
                reasons.append('Owner requests the complete original printed table before its native interactive adaptation; exact bounded source-PDF region, no additional raster asset')
            if cid=='g7_ch07_q14':
                yes='بله؛ در این شکل، منطقهٔ اشباع همان سفرهٔ آب زیرزمینی است.'
                q['steps'][0]['pairs'][3].update(right=yes,choices=[yes,'خیر؛ منطقهٔ اشباع بدون آب است.'])
                q['steps'][0]['hint2']='به فاصلهٔ سطح زمین تا مرز آب، خودِ مرز و ناحیهٔ زیر آن توجه کن. برای قسمت ب، پر یا خالی بودن فاصلهٔ میان ذرات از آب را در نظر بگیر.'
                q['practiceRevision']=2
                q.pop('sourceReviewNote',None);q.pop('answerReviewNote',None)
                q['nativeAnswer']=[dict(type='text',text='۱- عمق سطح ایستابی (منطقهٔ بالایی)\n۲- سطح ایستابی\n۳- منطقهٔ اشباع\nب- بله؛ در این شکل، منطقهٔ اشباع همان سفرهٔ آب زیرزمینی است و فضاهای خالی بین ذرات آن از آب پر شده است.')]
                reasons.append('Owner-approved corrected answer image supplies part ب; retain the Grade 7 explanation and matching full text without the obsolete correction warning; literal original key remains immutable')
            if cid in art:
                q['answerStudyVisual']=art[cid]['asset']
                if cid=='g7_ch07_q14' or cid.startswith(tuple(f'g7_ch{c:02}_'for c in range(11,16))):
                    q['answerStudyCaption']='تصویر آموزشیِ ارسالی، در کنار پاسخ کامل متنی؛ برای خواندن جزئیات روی تصویر بزنید.'
            if native_hash(q)!=native_hash(previous[cid]):
                assert reasons,cid
                revisions.append(dict(id=cid,beforeNativeSha256=native_hash(previous[cid]),afterNativeSha256=native_hash(q),reasons=reasons))
        (ROOT/path).write_text(json.dumps(qs,ensure_ascii=False,indent=2)+'\n')
    ledger=dict(baseline=BASELINE,authorization='2026-10-22 owner requests printed MCQ order, original tables first, automatic complete reference after a correct check, corrected 7/14 and all new chapter 11–15 answer art; preserve school-level simplifications',revisions=revisions)
    (ROOT/'qa/source/native-editorial-revisions-v11.json').write_text(json.dumps(ledger,ensure_ascii=False,indent=2)+'\n')
    table_text=json.dumps(dict(sourceWorkbookSha256=hashlib.sha256((A/'source/workbook.pdf').read_bytes()).hexdigest(),printedTables=tables,
        policy='Five actual printed tables found among the fourteen native question tables. Other native tables adapt prose, comparison lists or figures; no nonexistent printed table is invented.'),ensure_ascii=False,indent=2)+'\n'
    (ROOT/'qa/source/original-tables-v11.json').write_text(table_text)
    (A/'content/original-tables.json').write_text(table_text)
    (ROOT/'qa/source/printed-mcq-order-v11.json').write_text(json.dumps(dict(policy='Original الف، ب، ج، د order; logical answer indices and reusable non-MCQ banks retained',steps=ordered),ensure_ascii=False,indent=2)+'\n')
    refresh_manifests()
    print(json.dumps(dict(nativeRevisions=len(revisions),originalTables=len(tables),orderedMCQ=len(ordered))))

def refresh_manifests():
    for name,chapters in [('native-first-five-manifest.json',range(1,6)),('native-chapters-six-to-ten-manifest.json',range(6,11)),('native-chapters-eleven-to-fifteen-manifest.json',range(11,16))]:
        records=[]
        for ch in chapters:
            for q in json.loads((A/f'content/chapter{ch}.json').read_text()):
                records.append(dict(id=q['canonicalId'],questionId=q['id'],sourceQuestion=q['sourceQuestionVisual'],sourceAnswer=q['answerVisual'],nativeSha256=native_hash(q)))
        (ROOT/'qa/source'/name).write_text(json.dumps(dict(edition=1405,questions=records),ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
