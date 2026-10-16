"""Rebuild the three-chapter source-faithful edition from pinned original PDFs.

First run: --prepared DIRECTORY --pdfs DIRECTORY --uploads DIRECTORY.
Later runs use qa/source and the supplied images already retained in the repository.
Requires PyMuPDF and Pillow. Never uses the editorial answer cards as student answers.
"""
import argparse, hashlib, io, json, os, shutil
from pathlib import Path
import fitz
from PIL import Image
from grade7_chapter3 import chapter3_steps, chapter3_mcq

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
PINNED=ROOT/'qa/source'

TEMPLATES={
 (1,1):'''۱- از بین کلمات داخل پرانتز ، کلمه مناسب برای هر جمله را انتخاب کنید . ( آزمایش – کنجکاوی – فناوری – دانش – فرضیه سازی )
a – شما می توانید با یادگیری انواع {{0}} و پرورش مهارت های خود ، به حل مسائلی بپردازید که در زندگی با آن ها روبرو می شوید .
b – با طراحی و انجام {{1}} و بررسی نتایج آن ، درستی یا نادرستی پیش بینی خود را می توان بررسی کرد .
c – تبدیل علم به عمل ، {{2}} نام دارد .''',
 (1,5):'''۵- با استفاده از کلمات داخل پرانتز ، مراحل حل یک مسئله به روش علمی را به ترتیب کامل کنید . ( فرضیه سازی – نتیجه گیری – آزمایش )
۱- مشاهده     ۲- طرح پرسش     ۳- {{0}}     ۴- {{1}}     ۵- {{2}}''',
 (2,1):'''۱- از بین کلمات داخل کادر ، کلمه مناسب برای هر جمله را انتخاب کنید .
(( طول – ظرف های مدرج – جرم – ثانیه – فرضیه سازی – زمان – لیتر – اندازه گیری – حجم – کیلو گرم ))
a – مقدار ماده تشکیل دهنده جسم ، {{0}} نام دارد .
b – فاصله بین دو نقطه و مسافتی را که یک جسم طی می کند ، با یکای {{1}} اندازه می گیریم .
c – یک {{2}} برابر حجم ظرف مکعبی شکل به طول ، عرض و ارتفاع ۱۰ سانتی متر است . یعنی معادل ۱۰۰۰ سانتی متر مکعب است .
d – از {{3}} برای اندازه گیری حجم مقدارهای کم مایع استفاده می کنیم .
e – یک مرحله مهم برای جمع آوری اطلاعات ، {{4}} است که دقت آن به دقت شخص و دقت وسیله بستگی دارد .
f – برای اینکه بتوانیم به سؤال « چه وقت » و « چه مدت » پاسخ دهیم ، {{5}} را اندازه می گیریم .
g – یکای اندازه گیری زمان ، {{6}} است .''',
 (3,1):'''۱- جملات زیر را با استفاده از کلمات داده شده کامل کنید ( فلز – ترکیب – مایع – اتم – گاز – مولکول – جامد – عنصر – نافلز )
a – به ذره های ریز سازنده مواد ، {{0}} می گویند .
b – شکل خالصی از ماده که از یک نوع اتم ساخته شده است ، {{1}} نام دارد .
c – مولکول ها از پیوند دو یا چند {{2}} به وجود می آیند .
d – ذره های سازنده عنصر های {{3}} فقط اتم است .
e – ماده ای که از دو یا چند نوع اتم متفاوت تشکیل شده است ، {{4}} نام دارد .
f – {{5}} شکل مشخص و حجم معینی دارد ولی {{6}} به شکل ظرف در می آید و حجم معینی دارد
در حالی که {{7}} به شکل ظرف در می آید و حجم معینی ندارد .''',
 (3,8):'''۸- کدام یک از ویژگی های زیر مربوط به فلز ها و کدام مربوط به نا فلز ها است ؟
a – رسانای الکتریکی هستند ( {{0}} )
b – سطح براق ندارند ( {{1}} )
c – اغلب آن ها شکننده اند ( {{2}} )
d – چکش خوارند یعنی با ضربه به شکل های گوناگون در می آیند . ( {{3}} )
e – اغلب در آب فرو می روند ( {{4}} )
f – نارسانا ( عایق ) هستند ( {{5}} )
g – سطح براق دارند ( {{6}} )
h – اغلب روی آب شناور می مانند. ( {{7}} )''',
 (3,10):'''۱۰- نام اتم های سازنده مولکول های زیر را بنویسید .
الف- متان ( {{0}} و {{1}} )
ب- کربن دی اکسید ( {{2}} و {{3}} )
ج- آب ( {{4}} و {{5}} )''',
 (3,17):'''۱۷- در مورد ساختار اتم به سؤالات زیر پاسخ دهید :
الف – سه ذره سازنده اتم را نام ببرید . ۱- {{0}} ۲- {{1}} ۳- {{2}}
ب – هسته اتم از کدام ذرات تشکیل شده است ؟ {{3}} و {{4}}
ج – در یک اتم در حالت عادی ( خنثی ) ، تعداد {{5}} با تعداد {{6}} با هم برابر است .'''
}

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def dump(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def png(path,im):
    buf=io.BytesIO();im.save(buf,format='PNG');raw=buf.getvalue();Image.open(io.BytesIO(raw)).verify()
    temp=path.with_suffix('.tmp');path.parent.mkdir(parents=True,exist_ok=True)
    with temp.open('wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
    os.replace(temp,path)

def crops(doc,segments,dest):
    images=[]
    for s in segments:
        pg=doc[s['page']-1];r=fitz.Rect(0,s['bbox'][1],pg.rect.width,s['bbox'][3])
        pix=pg.get_pixmap(matrix=fitz.Matrix(3,3),clip=r,alpha=False)
        images.append(Image.frombytes('RGB',[pix.width,pix.height],pix.samples))
    if len(images)==1:out=images[0]
    else:
        out=Image.new('RGB',(max(x.width for x in images),sum(x.height for x in images)+12*(len(images)-1)),'white');y=0
        for im in images:out.paste(im,(0,y));y+=im.height+12
    png(dest,out)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--prepared',type=Path);ap.add_argument('--pdfs',type=Path);ap.add_argument('--uploads',type=Path);args=ap.parse_args()
    PINNED.mkdir(parents=True,exist_ok=True)
    if args.prepared:
        records=[q for q in json.loads((args.prepared/'data/grade7_1405_questions.json').read_text()) if q['chapter'] in (1,2,3)]
        dump(PINNED/'chapters1-3-index.json',records)
    records=json.loads((PINNED/'chapters1-3-index.json').read_text())
    if args.pdfs:
        for n in ['کتاب کار علوم هفتم 1405.pdf']+[f'پاسخ فصل {c} هفتم.pdf' for c in (1,2,3)]:
            shutil.copyfile(next(args.pdfs.rglob(n)),PINNED/n)
    if args.uploads:
        for p in args.uploads.glob('*.png'):
            if any(f'ch0{c}_' in p.name for c in (1,2,3)):
                target=(PINNED/'pending-images' if p.name=='g7_ch03_q15_question.png' else ASSETS/'images')/p.name
                target.parent.mkdir(exist_ok=True,parents=True);shutil.copyfile(p,target)
    workbook=fitz.open(PINNED/'کتاب کار علوم هفتم 1405.pdf')
    answers={c:fitz.open(PINNED/f'پاسخ فصل {c} هفتم.pdf') for c in (1,2,3)}
    existing={c:json.loads((ASSETS/f'content/chapter{c}.json').read_text()) for c in (1,2)}
    ch3=chapter3_steps();mcq3=chapter3_mcq();collections={1:[],2:[],3:[]};manifest=[]
    key_ranges={2:[(334,367),(279,315),(208,260),(157,195),(96,139)],3:[(386,424),(340,376),(289,330),(239,276),(182,219),(108,160),(44,86)]}
    titles={1:'جای خالی',2:'کاربرد سنگ مرمر',3:'کاربرد نفت خام',4:'کاربرد نمک خوراکی',5:'بخار آب در هوا',6:'ویژگی فلزها',7:'ویژگی نافلزها',8:'ویژگی مواد',9:'طبقه‌بندی عنصرها',10:'اتم‌های سازندهٔ مولکول',11:'ذره‌های سازندهٔ مواد',12:'مدل ذره‌ای مواد',13:'عنصر و ترکیب',14:'تعریف عنصر و ترکیب',15:'ساختار کربن دی اکسید',16:'ویژگی اتم‌ها',17:'ساختار اتم',18:'مقایسهٔ حجم',19:'تراکم‌پذیری',20:'فشرده‌شدن هوا',21:'نمودار افزایش حجم',22:'بطری و بادکنک',23:'انرژی و جنبش مولکول',24:'حالت گازی آب'}
    for r in records:
        c=r['chapter'];n=r['number'];ismcq=r['section']=='mcq';canonical=r['id']
        if c<3:q=next(q for q in existing[c] if q['sourceNumber']==n and (q['section']=='چهارگزینه‌ای')==ismcq)
        else:
            q=dict(id=f'g7-ch03-{ "mcq" if ismcq else "main"}-{n:03}-1405',number=len(collections[c])+1,sourceNumber=n,
                section='چهارگزینه‌ای' if ismcq else 'تمرین‌های اصلی',title='چهارگزینه‌ای' if ismcq else titles[n],concept='اتم‌ها، الفبای مواد',difficulty=2,
                steps=mcq3[n] if ismcq else ch3[n],visual=None,visuals=[],legacyProgress=[])
        q.update(grade=7,relatedChapter=c,sourceEdition='1405',sourcePage=r['page'],source=f'کتاب کار علوم هفتم ۱۴۰۵ • صفحهٔ {r["page"]}',
            bookPrompt=r['search_text'],canonicalId=canonical,sourceQuestionVisual=f'{canonical}_source_question.png',
            answerVisual=f'{canonical}_source_answer.png',answerVisualCaption=None,sourcePolicy='verbatim_original_pdf',sourceAnswerTextExtracted=r['answer_source']['text'])
        crops(workbook,r['segments'],ASSETS/'images'/q['sourceQuestionVisual'])
        segs=json.loads(json.dumps(r['answer_source']['segments']))
        # A formula extends below the normal text baseline; the earlier crops included
        # the next answer's numerator and clipped q9's denominator at the page end.
        if canonical=='g7_ch02_q07':segs[0]['bbox'][3]=659
        if canonical=='g7_ch02_q09':segs=[dict(page=1,bbox=[0,717,595,807])]
        if canonical=='g7_ch02_q10':segs[0]['bbox'][3]=151
        if canonical=='g7_ch03_q17':segs[0]['bbox'][3]=265
        if canonical=='g7_ch03_q18':segs[0]['bbox'][1]=265
        if ismcq:
            x0,x1=key_ranges[c][n-1];pg=answers[c][segs[0]['page']-1];y0,y1=segs[0]['bbox'][1],segs[0]['bbox'][3]
            pix=pg.get_pixmap(matrix=fitz.Matrix(5,5),clip=fitz.Rect(x0,y0,x1,y1),alpha=False)
            png(ASSETS/'images'/q['answerVisual'],Image.frombytes('RGB',[pix.width,pix.height],pix.samples))
            segs=[dict(page=segs[0]['page'],bbox=[x0,y0,x1,y1])]
        else:crops(answers[c],segs,ASSETS/'images'/q['answerVisual'])
        if c==3 and n in (16,17) and not ismcq:q['visual']='g7_ch03_mcq05_question.png' if n==16 else 'g7_ch03_q17_question.png'
        if c==3 and ismcq and n==5:q['visual']='g7_ch03_mcq05_question.png'
        if c==3 and not ismcq and n==15:q['visual']=None
        for i,s in enumerate(q['steps'],1):
            s['id']=f'{q["id"]}-s{i:02}'
            s['answer']=r['answer_source']['text']
            s['explanation']=''
            if s['kind']=='match':s['allowTokenReuse']=False
            if not ismcq and (c,n) in TEMPLATES:s['clozeTemplate']=TEMPLATES[c,n]
        if not ismcq and (c,n) in [(1,1),(1,5),(2,1)]:
            s=q['steps'][0];s['allowTokenReuse']=False
            s['prompt']='واژهٔ مناسب را در هر جای خالی انتخاب کن.'
            for p in s['pairs']:
                p['right']=p['right'].replace('\u200c',' ')
                p['left']=p['left'].replace('نام کمیتی که فاصله و مسافت را بیان می کند','b')
            s['choices']=[v.replace('\u200c',' ') for v in s['choices']]
            if c==2:
                s['choices'][-1]='کیلو گرم'
                s['hint1']='هر جمله را کامل بخوان و به مفهوم واژه‌ها و کاربرد آن‌ها توجه کن.'
                s['hint2']='در a به مادهٔ تشکیل‌دهنده، در c به ظرف مکعبی، در d به وسیلهٔ مدرج و در f و g به پرسش‌های مربوط به زمان توجه کن؛ هر انتخاب را در جملهٔ خودش دوباره بخوان.'
        if c==1 and n==6:
            for o in q['steps'][1]['options']:
                if o['text']=='رایانه':o['text']='کامپیوتر'
        if c==1 and n==7:
            for step,correct in zip(q['steps'],['استفاده از اطلاعات موجود در اینترنت، ذخیره سازی اطلاعات و...','ضعیف شدن چشم، کم تحرکی و تنبلی و...']):
                next(o for o in step['options'] if o['correct'])['text']=correct
        if c==2 and not ismcq and n==12:
            s=q['steps'][2];s['options']=[o for o in s['options'] if o['text']!='ثانیه'];s['prompt']='چهار یکای نوشته‌شده در پاسخ‌نامه را انتخاب کن.'
        if ismcq and c==2:
            for i,o in enumerate(q['steps'][0]['options']):
                if not o['text'].startswith(('الف-','ب-','ج-','د-')):o['text']=['الف- ','ب- ','ج- ','د- '][i]+o['text']
        for s in q['steps']:
            if 'واژه «یکا»' in s['prompt']:raise AssertionError('editorial correction leaked')
        manifest.append(dict(id=canonical,questionAsset=q['sourceQuestionVisual'],questionSha256=sha(ASSETS/'images'/q['sourceQuestionVisual']),
            answerAsset=q['answerVisual'],answerSha256=sha(ASSETS/'images'/q['answerVisual']),questionSourceSegments=r['segments'],answerSourceSegments=segs,
            answerImageRecommended=r.get('answer_image_recommended',False),formattingRisks=r.get('formatting_risks',[]),sharedAnswerStrip=False))
        collections[c].append(q)
    for c,qs in collections.items():dump(ASSETS/f'content/chapter{c}.json',qs)
    dump(ROOT/'docs/source_fidelity_manifest.json',dict(policy='Author instruction: original questions and answers unchanged; original PDF crops only for reference answers.',
        sourcePdfs=[dict(file=p.name,sha256=sha(p)) for p in sorted(PINNED.glob('*.pdf'))],questions=manifest))
    print(json.dumps(dict(chapterCounts=[len(collections[c]) for c in (1,2,3)],steps=[sum(len(q['steps']) for q in collections[c]) for c in (1,2,3)])))

if __name__=='__main__':main()
