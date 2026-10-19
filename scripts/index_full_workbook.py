"""Index only printed 1405 regions. Extracted text is search/audit data, not typesetting.

Pinned source coordinates are PDF points, with 1-based page numbers. No OCR,
rewriting, formula reconstruction, or generated scientific image is involved.
"""
import hashlib, json, re, shutil
from pathlib import Path
import fitz

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
SOURCE = ASSETS / 'source'
RANGES = {1:(2,2,8,0),2:(3,6,15,5),3:(7,11,24,7),4:(12,15,19,4),
          5:(16,19,17,5),6:(20,23,21,0),7:(24,29,28,6),8:(30,34,19,7),
          9:(35,38,15,3),10:(39,44,25,4),11:(45,47,13,0),12:(48,50,20,0),
          13:(51,53,12,5),14:(54,56,10,5),15:(57,59,14,6)}
TITLES = ['تجربه و تفکر','اندازه‌گیری در علوم و ابزارهای آن','اتم‌ها، الفبای مواد',
          'مواد پیرامون ما','از معدن تا خانه','سفر آب روی زمین','سفر آب درون زمین',
          'انرژی و تبدیل‌های آن','منابع انرژی','گرما و بهینه‌سازی مصرف انرژی',
          'یاخته و سازمان‌بندی آن','سفره سلامت','سفر غذا','گردش مواد','تبادل با محیط']
OVERRIDES = {(4,14):(14,442.5),(4,16):(14,631.1),(6,15):(23,41.3),
             (10,17):(42,343.6),(11,7):(46,578.6)}

def dump(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def spans(page):
    return [s for b in page.get_text('dict')['blocks'] for l in b.get('lines',[])
            for s in l['spans'] if s['text'].strip()]

def anchors(doc,first,last,min_x):
    return sorted([(pn,s['bbox'][1],int(s['text'].strip().strip('-– ').strip()),s['bbox'])
        for pn in range(first,last+1) for s in spans(doc[pn-1])
        if re.fullmatch(r'\s*\d{1,2}\s*[-–]?\s*',s['text']) and
        s['bbox'][0] >= min_x and 35 < s['bbox'][1] < 790],key=lambda x:(x[0],x[1],-x[3][0]))

def select(candidates,count,chapter=0,after=(0,0)):
    found=[]
    for number in range(1,count+1):
        if (chapter,number) in OVERRIDES:
            pn,y=OVERRIDES[chapter,number];a=(pn,y,number,[0,y,0,y+15])
        else:
            a=next((a for a in candidates if a[2]==number and (a[0],a[1])>after),None)
            if a is None: raise ValueError(f'missing anchor ch{chapter} q{number}, after {after}')
        found.append(a);after=(a[0],a[1]+1)
    return found

def segments(start,end,last_page,doc,answer=False):
    p0,y0=start[:2];p1,y1=end[:2] if end else (last_page,800 if not answer else 806)
    out=[]
    for pn in range(p0,p1+1):
        lo=y0-2 if pn==p0 else (36 if not answer else 38)
        hi=y1-3 if pn==p1 else (800 if not answer else 806)
        if hi>lo+2:out.append(dict(page=pn,bbox=[20,round(lo,2),min(590,doc[pn-1].rect.width-12),round(hi,2)]))
    return out

def region(asset,segs):
    return 'pdf:'+asset+'#'+';'.join(','.join(str(v) for v in [s['page'],*s['bbox']]) for s in segs)

def text(doc,segs):
    return '\n'.join(doc[s['page']-1].get_text(clip=fitz.Rect(s['bbox'])).strip() for s in segs)

def key_columns(doc,start,count):
    """Locate individual glyphs, including fused spans such as «ب2» and «الف3»."""
    pn,y=start
    chars=[ch for b in doc[pn-1].get_text('rawdict')['blocks'] for l in b.get('lines',[])
        for s in l['spans'] for ch in s['chars'] if abs(ch['bbox'][1]-y)<5]
    nums=[];right=450
    for n in range(1,count+1):
        ch=max((x for x in chars if x['c']==str(n) and x['bbox'][0]<right),key=lambda x:x['bbox'][0])
        nums.append(ch);right=ch['bbox'][0]-1
    letters=[];regions=[]
    for i,ch in enumerate(nums):
        lo=nums[i+1]['bbox'][2]+1 if i+1<len(nums) else 3
        hi=ch['bbox'][0]-1
        ink=[x for x in chars if x['c'] in 'الفبجدهـ' and lo<=x['bbox'][0]<hi]
        if not ink:raise ValueError('Missing printed MCQ key letter')
        letter=''.join(x['c'] for x in sorted(ink,key=lambda x:-x['bbox'][0]))
        letter={'فلا':'الف'}.get(letter,letter)
        if letter not in ['الف','ب','ج','د']:raise ValueError(f'Invalid key {letter}')
        letters.append(letter)
        bb=[min(x['bbox'][0] for x in ink)-2,min(x['bbox'][1] for x in ink)-2,
            max(x['bbox'][2] for x in ink)+2,max(x['bbox'][3] for x in ink)+2]
        regions.append([dict(page=pn,bbox=[round(v,2) for v in bb])])
    return letters,regions

def main():
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--sources',type=Path,required=True);args=p.parse_args()
    SOURCE.mkdir(parents=True,exist_ok=True)
    files={'workbook.pdf':args.sources/'کتاب کار علوم هفتم 1405.pdf',**{
        f'answers{c:02}.pdf':args.sources/f'پاسخ فصل {c} هفتم.pdf' for c in range(1,16)}}
    for target,src in files.items():shutil.copyfile(src,SOURCE/target)
    book=fitz.open(SOURCE/'workbook.pdf');records=[]
    old=json.loads((ROOT/'qa/source/chapters1-3-index.json').read_text())
    old_content={q['canonicalId']:q for c in range(1,4) for q in json.loads((ASSETS/f'content/chapter{c}.json').read_text())}
    for c,(first,last,nmain,nmcq) in RANGES.items():
        answer=fitz.open(SOURCE/f'answers{c:02}.pdf')
        if c<=3:
            for r in [r for r in old if r['chapter']==c]:
                q=old_content[r['id']];segs=json.loads(json.dumps(r['answer_source']['segments']))
                if r['id']=='g7_ch02_q07':segs[0]['bbox'][3]=659
                if r['id']=='g7_ch02_q09':segs=[dict(page=1,bbox=[0,717,595,807])]
                if r['id']=='g7_ch02_q10':segs[0]['bbox'][3]=151
                if r['id']=='g7_ch03_q17':segs[0]['bbox'][3]=265
                if r['id']=='g7_ch03_q18':segs[0]['bbox'][1]=265
                if r['section']=='mcq':
                    xs={2:[(334,367),(279,315),(208,260),(157,195),(96,139)],3:[(386,424),(340,376),(289,330),(239,276),(182,219),(108,160),(44,86)]}[c][r['number']-1]
                    s=segs[0];s['bbox'][0],s['bbox'][2]=xs
                records.append(dict(chapter=c,kind=r['section'],number=r['number'],id=r['id'],
                    segments=r['segments'],answerSegments=segs,questionAsset=region('workbook.pdf',r['segments']),
                    answerAsset=region(f'answers{c:02}.pdf',segs),questionText=r['source_text_extracted'],
                    answerText=r['answer_source']['text'],sourcePage=r['page']))
            continue
        cand=anchors(book,first,last,537)
        qs=select(cand,nmain,c)
        book_headings=[(pn,l['bbox'][1]) for pn in range(first,last+1)
            for b in book[pn-1].get_text('dict')['blocks'] for l in b.get('lines',[])
            if 'گزینه' in ''.join(s['text'] for s in l['spans']) and (pn,l['bbox'][1])>(qs[-1][0],qs[-1][1]+1)]
        heading=min(book_headings) if nmcq else None
        mcqs=select(cand,nmcq,after=heading) if nmcq else []
        ans=select(anchors(answer,1,len(answer),530),nmain)
        # A key heading is not part of the final open answer.
        headings=[]
        for pn,pg in enumerate(answer,1):
            for b in pg.get_text('dict')['blocks']:
                for line in b.get('lines',[]):
                    if 'گزینه' in ''.join(s['text'] for s in line['spans']):headings.append((pn,line['bbox'][1]))
        key_start=headings[-1] if nmcq and headings else None
        key_letters,key_regions=key_columns(answer,key_start,nmcq) if nmcq else ([],[])
        for i,a in enumerate(qs+mcqs):
            kind='main' if i<nmain else 'mcq';n=i+1 if kind=='main' else i-nmain+1
            end=(qs+mcqs)[i+1] if i+1<len(qs+mcqs) else None
            if i==nmain-1 and heading:end=heading
            ss=segments(a,end,last,book)
            if kind=='main':
                aa=segments(ans[i],ans[i+1] if i+1<nmain else key_start,len(answer),answer,True)
                if i==nmain-1 and key_start is None and nmcq:raise ValueError(f'no MCQ heading {c}')
            else:
                aa=key_regions[n-1]
            records.append(dict(chapter=c,kind=kind,number=n,id=f'g7_ch{c:02}_{"q" if kind=="main" else "mcq"}{n:02}',
                segments=ss,answerSegments=aa,questionAsset=region('workbook.pdf',ss),
                answerAsset=region(f'answers{c:02}.pdf',aa) if aa else None,
                questionText=text(book,ss),answerText=text(answer,aa) if aa else '',sourcePage=a[0],
                **({'keyLetter':key_letters[n-1]} if kind=='mcq' else {})))
        print(f'Chapter {c}: {nmain} + {nmcq} questions, key heading {key_start}')
    dump(ROOT/'qa/source/all-chapters-index.json',records)
    dump(ROOT/'qa/source/1405-source-manifest.json',dict(edition=1405,grade=7,referenceGrade9Commit='1117ed101b66a4b70554e47b51887aaafe768203',
        files=[dict(asset='source/'+name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),bytes=path.stat().st_size,pages=len(fitz.open(path))) for name,path in files.items()]))
    dump(ROOT/'qa/source/chapter-map.json',[dict(id=c,title=TITLES[c-1],questions=m+q,main=m,mcq=q,firstPage=f,lastPage=l) for c,(f,l,m,q) in RANGES.items()])
    print('Total questions',len(records))

if __name__=='__main__':main()
