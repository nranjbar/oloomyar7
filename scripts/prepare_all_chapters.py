"""Build all 317 source questions without bundling duplicate page rasters.

Run index_full_workbook.py first with the supplied 1405 PDFs. Scientific source
content is immutable. Practice, reading transcripts and hints are separate data.
"""
import hashlib,itertools,json,re
from pathlib import Path
from PIL import Image
from full_chapter_cloze import CLOZE
from full_chapter_practice import P,M
from full_chapter_mcq_hints import H
from full_extra_cloze import EXTRA,BANKS
from full_readable_prompts import READABLE
from index_full_workbook import ROOT,ASSETS,dump,region

def main():
    records=json.loads((ROOT/'qa/source/all-chapters-index.json').read_text())
    by_id={r['id']:r for r in records}
    catalog=json.loads((ROOT/'qa/source/chapter-map.json').read_text())
    fidelity=[]
    for c in range(1,16):
        path=ASSETS/f'content/chapter{c}.json'
        if c<=3:
            questions=json.loads(path.read_text())
            for q in questions:
                r=by_id[q['canonicalId']]
                old=q['sourceQuestionVisual']
                if q.get('originalFigureVisual')==old:
                    # These are classification/element tables: keep their printed geometry.
                    # Do not repeat the same question in both native text and the figure.
                    q['readablePrompt']=None;q['originalFigureVisual']=None
                q.update(sourceQuestionVisual=r['questionAsset'],answerVisual=r['answerAsset'],promptVisual=None)
        else:
            questions=[]
            cr=[r for r in records if r['chapter']==c]
            for r in cr:
                n=r['number'];ismcq=r['kind']=='mcq';source_id=f'g7-ch{c:02}-{"mcq" if ismcq else "main"}-{n:03}-1405'
                if ismcq:
                    h1,h2=H[c][n-1];letter=r['keyLetter']
                    step=dict(kind='single',prompt='گزینهٔ چاپی کتاب را انتخاب کن.',
                        options=[dict(text=f'گزینهٔ {v}',correct=v==letter) for v in ['الف','ب','ج','د']],hint1=h1,hint2=h2)
                    title='چهارگزینه‌ای'
                elif n==1:
                    template,answers,choices,h1,h2=CLOZE[c]
                    per_row=bool(choices and isinstance(choices[0],list))
                    step=dict(kind='match',prompt='واژهٔ مناسب را در همان جای خالی انتخاب کن.',
                        pairs=[dict(left=str(i+1),right=value,**({'choices':choices[i]} if per_row else {})) for i,value in enumerate(answers)],
                        choices=[] if per_row else choices,allowTokenReuse=c==8,clozeTemplate=template,hint1=h1,hint2=h2)
                    # Conjoined words in a sentence have scientifically interchangeable orders.
                    groups={5:[[1,2]],8:[[8,9],[11,12,13]],12:[[8,9],[10,11]]}.get(c,[])
                    alt=[answers]
                    for group in groups:
                        expanded=[]
                        for base in alt:
                            for values in itertools.permutations([base[i] for i in group]):
                                row=base.copy()
                                for i,value in zip(group,values):row[i]=value
                                expanded.append(row)
                        alt=expanded
                    step['acceptedPairOrders']=[v for v in alt if v!=answers]
                    title='جای خالی'
                else:
                    # Deep copy: generation never mutates authored dictionaries between runs.
                    step=json.loads(json.dumps(P[c][n]));title=step.pop('_title')
                    if (c,n)==(12,20):
                        step=M('مقدار آب به گرم|۳۰|۷۰|۲۰',step['hint1'],step['hint2'],title);step.pop('_title')
                    if (c,n)==(14,6):
                        step=M('۱|انقباض دهلیز ها|انقباض بطن ها|استراحت عمومی\n۲|انقباض بطن ها|انقباض دهلیز ها|استراحت عمومی\n۳|استراحت عمومی|انقباض بطن ها|انقباض دهلیز ها',step['hint1'],step['hint2'],title);step.pop('_title')
                    if (c,n)==(4,19):
                        for pair in step['pairs']:pair['choices']=[v for v in pair['choices'] if v!='نامشخص']
                    if (c,n)==(7,8):
                        for i,pair in enumerate(step['pairs']):pair['choices']=['بزرگ تر','کوچک تر'] if i==1 else ['بیشتر','کمتر']
                    if (c,n) in BANKS:
                        step['choices']=BANKS[c,n]
                        for pair in step['pairs']:pair.pop('choices',None)
                    if (c,n) in EXTRA:step['clozeTemplate']=EXTRA[c,n]
                answer=r['answerText'].strip() or r.get('keyLetter','')
                assert answer, r['id']
                step.update(id=source_id+'-s01',answer=answer,explanation='')
                q=dict(id=source_id,canonicalId=r['id'],number=len(questions)+1,sourceNumber=n,
                    section='چهارگزینه‌ای' if ismcq else 'تمرین‌های اصلی',title=title,
                    concept=catalog[c-1]['title'],difficulty=2,steps=[step],grade=7,relatedChapter=c,
                    sourceEdition='1405',sourcePage=r['sourcePage'],source=f'کتاب کار علوم هفتم ۱۴۰۵ • صفحهٔ {r["sourcePage"]}',
                    bookPrompt=r['questionText'],sourceQuestionVisual=r['questionAsset'],answerVisual=r['answerAsset'],
                    sourceAnswerTextExtracted=answer,sourcePolicy='verbatim_original_pdf',readablePrompt=READABLE.get(r['id']),
                    visual=None,visuals=[],originalFigureVisual=None,promptVisual=None,legacyProgress=[])
                if (c,n)==(10,16) and not ismcq:
                    q['visuals']=[dict(asset=region('workbook.pdf',[dict(page=42,bbox=bb)])) for bb in
                        [[235.5,112.48,399.75,203.98],[37.5,95.23,211.55,199.48]]]
                if c==13 and ismcq and n==3:
                    q['sourceReviewNote']='کلید چاپی این پرسش با گزینه‌های چاپی نیازمند تأیید مؤلف است. ارزیابی فعلی فقط مطابقت با کلید چاپی را می‌سنجد؛ متن سؤال و پاسخنامه تغییر نکرده است.'
                questions.append(q)
        dump(path,questions)
        catalog[c-1]['steps']=sum(len(q['steps']) for q in questions)
        for q in questions:
            r=by_id[q['canonicalId']]
            fidelity.append(dict(id=q['canonicalId'],chapter=c,sourceNumber=q['sourceNumber'],section=r['kind'],
                questionAsset=q['sourceQuestionVisual'],answerAsset=q['answerVisual'],questionSourceSegments=r['segments'],
                answerSourceSegments=r['answerSegments'],sharedAnswerStrip=False,sourcePolicy='immutable_pdf_regions',
                paperAdditions=r.get('paperAdditions',[]),paperExclusions=r.get('paperExclusions',[]),
                readingTranscript=q.get('readablePrompt'),inline=any(s.get('clozeTemplate') for s in q['steps'])))
    dump(ASSETS/'content/catalog.json',catalog)
    old_manifest=ROOT/'docs/source_fidelity_manifest.json'
    archive=ROOT/'qa/source/previous-v04-source-manifest.json'
    if not archive.exists():archive.write_bytes(old_manifest.read_bytes())
    dump(old_manifest,dict(schemaVersion=2,edition=1405,questions=fidelity,sourcePdfs=json.loads((ROOT/'qa/source/1405-source-manifest.json').read_text())['files']))

    # Lossless WebP preserves all decoded scientific pixels. Originals remain pinned
    # in source provenance / Git history, and are not duplicated in the APK.
    references=set()
    content=[q for c in range(1,16) for q in json.loads((ASSETS/f'content/chapter{c}.json').read_text())]
    for q in content:
        references.update(x for x in [q.get('visual'),q.get('sourceQuestionVisual'),q.get('answerVisual'),q.get('promptVisual'),q.get('originalFigureVisual')]+[x['asset'] for x in q.get('visuals',[])] if x and not x.startswith('pdf:'))
    replacements={};lossless=[]
    for name in sorted(references):
        p=ASSETS/'images'/name
        if p.suffix.lower()!='.png':continue
        target=p.with_suffix('.webp');im=Image.open(p).convert('RGBA')
        im.save(target,'WEBP',lossless=True,method=6)
        decoded=Image.open(target).convert('RGBA')
        assert im.tobytes()==decoded.tobytes(),name
        replacements[name]=target.name
        lossless.append(dict(source=name,asset=target.name,originalSha256=hashlib.sha256(p.read_bytes()).hexdigest(),
            decodedRgbaSha256=hashlib.sha256(im.tobytes()).hexdigest(),webpSha256=hashlib.sha256(target.read_bytes()).hexdigest(),
            originalBytes=p.stat().st_size,bytes=target.stat().st_size))
    for c in range(1,16):
        path=ASSETS/f'content/chapter{c}.json';qs=json.loads(path.read_text())
        for q in qs:
            for field in ['visual','originalFigureVisual','promptVisual']:q[field]=replacements.get(q.get(field),q.get(field))
            for visual in q.get('visuals',[]):visual['asset']=replacements.get(visual['asset'],visual['asset'])
        dump(path,qs)
    if lossless:dump(ROOT/'qa/source/lossless-scientific-assets.json',lossless)
    keep={replacements.get(name,name) for name in references}
    keep.update(f'chapter_{c:02}.webp' for c in range(1,16))
    keep.update(f'mascot_{pose}_480w.webp' for pose in ['master','welcome','thinking','encourage','success','guidance'])
    for p in (ASSETS/'images').iterdir():
        if p.is_file() and p.name not in keep:p.unlink()
    dump(ASSETS/'content/image-dimensions.json',{p.name:list(Image.open(p).size)for p in (ASSETS/'images').glob('*.webp')if p.name in keep})
    print('Prepared',len(content),'questions;',sum(x['steps'] for x in catalog),'learning groups;',sum(x.stat().st_size for x in ASSETS.rglob('*') if x.is_file()),'asset bytes')

if __name__=='__main__':main()
