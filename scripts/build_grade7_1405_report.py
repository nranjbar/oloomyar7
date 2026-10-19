"""Source-faithful Persian audit PDF; uses original PDF ink, not regenerated text.

The user-facing report is separate from the Android package. All question/key
regions come from the same pinned source index as the app.
"""
import argparse,html,json,re
from pathlib import Path
import fitz
from index_full_workbook import ROOT,ASSETS

W,H=595,842;LEFT,RIGHT=38,557;BOTTOM=786
FONT=Path('/usr/share/fonts/truetype/dejavu')
CSS='''@font-face{font-family:fa;src:url(DejaVuSans.ttf)}
@font-face{font-family:fa;src:url(DejaVuSans-Bold.ttf);font-weight:bold}
body,div,p,table{font-family:fa;direction:rtl;text-align:right;color:#163945;font-size:11pt;line-height:1.75}
p{margin:0 0 9pt}h1{font-family:fa;font-size:23pt;line-height:1.5;color:white;margin:0}h2{font-size:15pt;color:#087987;margin:0 0 10pt}
table{border-collapse:collapse;width:100%;font-size:10pt}td,th{border:1px solid #d8e5e8;padding:7pt;vertical-align:top}th{background:#e6f3f4;color:#075f6d}
.muted{color:#58727b}.note{background:#fff2df;padding:11pt}.green{color:#087650}.small{font-size:9pt}b,strong{font-weight:bold}'''

def fa(value):return str(value).translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹'))
def esc(t):return html.escape(str(t))

class Report:
    def __init__(self):
        self.doc=fitz.open();self.archive=fitz.Archive(str(FONT));self.books={}
        self.records={r['id']:r for r in json.loads((ROOT/'qa/source/all-chapters-index.json').read_text())}

    def box(self,page,rect,text,css=''):
        spare,scale=page.insert_htmlbox(rect,'<div dir="rtl">'+text+'</div>',css=CSS+css,archive=self.archive,scale_low=1)
        if spare<0:raise ValueError('Report text overflows: '+re.sub('<[^>]+>','',text)[:100])
        return rect.y1-spare

    def page(self,title,subtitle='کتاب کار علوم هفتم ۱۴۰۵ • صورت سؤال و پاسخ چاپی بدون بازنویسی'):
        p=self.doc.new_page(width=W,height=H)
        p.draw_rect(fitz.Rect(0,0,W,101),fill=(.035,.35,.42),color=None)
        self.box(p,fitz.Rect(LEFT,15,RIGHT,66),'<h1>'+esc(title)+'</h1>')
        self.box(p,fitz.Rect(LEFT,68,RIGHT,95),'<p>'+esc(subtitle)+'</p>','p{font-size:8pt;color:#d9f5f5}')
        p.draw_rect(fitz.Rect(RIGHT+13,121,RIGHT+17,778),fill=(.07,.65,.65),color=None)
        return p

    def paper(self,asset):
        name,spec=asset[4:].split('#',1);parts=spec.split('|')
        parse=lambda raw:[float(x) for x in raw.split(',')]
        main=[parse(x)for x in parts[0].split(';')]
        plus=[parse(x[1:])for x in parts[1:]if x.startswith('+')]
        minus=[parse(x[1:])for x in parts[1:]if x.startswith('-')]
        if name not in self.books:self.books[name]=fitz.open(ASSETS/'source'/name)
        original=self.books[name];bounds=[]
        for pn,*bb in main:
            bound=fitz.Rect(bb)
            for number,*extra in plus:
                if number==pn:bound|=fitz.Rect(extra)
            bounds.append(bound)
        width=max(b.width for b in bounds);height=sum(b.height for b in bounds)+5*(len(bounds)-1)
        sheet=fitz.open();target=sheet.new_page(width=width,height=height);y=0
        for (pn,*bb),bound in zip(main,bounds):
            x=(width-bound.width)/2
            def place(box):return fitz.Rect(box.x0-bound.x0+x,box.y0-bound.y0+y,box.x1-bound.x0+x,box.y1-bound.y0+y)
            body=fitz.Rect(bb);target.show_pdf_page(place(body),original,int(pn)-1,clip=body,keep_proportion=False)
            for number,*mask in minus:
                if number==pn:target.draw_rect(place(fitz.Rect(mask)),fill=(1,1,1),color=None,overlay=True)
            for number,*extra in plus:
                if number==pn:
                    source=fitz.Rect(extra);target.show_pdf_page(place(source),original,int(pn)-1,clip=source,keep_proportion=False,overlay=True)
            y+=bound.height+5
        return sheet

    def region(self,page,asset,y,label,max_width=None,max_height=None):
        y=self.box(page,fitz.Rect(LEFT,y,RIGHT,y+25),'<p><b>'+esc(label)+'</b></p>')+6
        sheet=self.paper(asset);source=sheet[0].rect
        width=min(RIGHT-LEFT,max_width or RIGHT-LEFT)
        height=width*source.height/source.width
        if max_height and height>max_height:height=max_height;width=height*source.width/source.height
        if y+height>BOTTOM:raise ValueError(f'Source crop too tall: {asset[:60]}')
        rect=fitz.Rect(RIGHT-width,y,RIGHT,y+height)
        page.show_pdf_page(rect,sheet,0,keep_proportion=True)
        page.draw_rect(rect,color=(.84,.89,.9),width=.5)
        return y+height+14

    def case(self,cid,title,reason,prompt=None):
        r=self.records[cid];p=self.page(title)
        y=self.box(p,fitz.Rect(LEFT,117,RIGHT,179),'<p>'+esc(reason)+'</p>')+8
        y=self.region(p,r['questionAsset'],y,'صورت سؤال کتاب ـ صفحهٔ '+fa(r['sourcePage']))
        y=self.region(p,r['answerAsset'],y,'پاسخ چاپی پاسخنامه',max_width=85 if r['kind']=='mcq'else None,max_height=45 if r['kind']=='mcq'else None)
        if prompt:
            self.box(p,fitz.Rect(LEFT,y,RIGHT,BOTTOM),'<p class="note"><b>دستور پیشنهادی</b><br>'+esc(prompt)+'</p>')
        return p

    def finish(self,path):
        for i,p in enumerate(self.doc):
            p.draw_line((LEFT,803),(RIGHT,803),color=(.8,.87,.89),width=.5)
            self.box(p,fitz.Rect(LEFT,808,RIGHT,836),'<p class="small muted">علوم‌یار هفتم • مرجع ۱۴۰۵ • صفحهٔ '+fa(i+1)+' از '+fa(len(self.doc))+'</p>')
        self.doc.set_metadata(dict(title='گزارش بررسی علوم‌یار هفتم ۱۴۰۵',author='OloomYar',subject='Source-faithful questions, reference answers, image policy and author review'))
        path.parent.mkdir(parents=True,exist_ok=True)
        self.doc.save(path,garbage=4,deflate=True)
        print(json.dumps(dict(output=str(path.resolve()),pages=len(self.doc),bytes=path.stat().st_size),ensure_ascii=False))

def build(output):
    r=Report();p=r.page('گزارش علوم‌یار هفتم','۱۵ فصل • مرجع ۱۴۰۵ • انتقال اصلاحات نهایی علوم نهم')
    r.box(p,fitz.Rect(LEFT,123,RIGHT,230),'''<h2>اصل کتاب تغییر نکرده است</h2><p>این گزارش، نیاز واقعی به تصویر و اصلاح روش نمایش و تمرین را از تغییر محتوای مؤلف جدا می‌کند. صورت سؤال و پاسخ‌های نمونهٔ این فایل از خود PDF کتاب و پاسخنامه بریده شده‌اند؛ واژه‌های مؤلف بازنویسی نشده‌اند.</p>''')
    rows=[('پوشش','۳۱۷ سؤال در ۱۵ فصل؛ ۳۳۴ بخش تمرینی و ۶۶۸ راهنما'),('جای خالی','متن کامل، انتخاب در همان خانه، زرد پیش از بررسی، سبز درست و قرمز قابل اصلاح'),('لمس دوباره','فقط همان انتخاب پاک می‌شود؛ تلاش تازه و جابه‌جایی خودکار ثبت نمی‌شود'),('پاسخ مرجع','همان پاسخ پس از حل درست یا دو تلاش کاملِ نادرستِ متفاوت و هر دو راهنما، با درخواست دانش‌آموز'),('قالب علمی','کسر، فرمول، جدول، نمودار و حروف ترکیبی از هندسهٔ اصلی PDF حفظ می‌شوند'),('پیشرفت','انتخاب، راهنما، نشان، محل مطالعه و حل مستقل هنگام مرور پاسخ حفظ می‌شوند'),('حجم','یک نسخه از منابع و WebP بدون تغییر پیکسل؛ PNG تکراری وارد بسته نمی‌شود'),('ظاهر','جلدهای ارسالی ۱۵ فصل، ربات ارسالی، کنترل‌های خوانا و صفحهٔ بیشتر'),('مرجع مقایسه','آخرین کد نهم با شناسهٔ 1117ed101؛ مخزن نهم تغییر نکرده است')]
    table='<table>'+''.join('<tr><th>'+a+'</th><td>'+b+'</td></tr>'for a,b in rows)+'</table>'
    r.box(p,fitz.Rect(LEFT,240,RIGHT,741),table)
    r.box(p,fitz.Rect(LEFT,747,RIGHT,786),'<p class="small muted">این شمارش برای فصل‌های ۱ تا ۱۵ است؛ مجموعهٔ سؤالات پایانیِ صفحات ۶۰ تا ۶۳، بخش جداگانه‌ای است.</p>')

    p=r.page('حداقل نیاز به تصویر تازه')
    r.box(p,fitz.Rect(LEFT,123,RIGHT,756),'''<h2>دو پاسخ تصویری موجود، یک مکمل اختیاری</h2>
<table><tr><th>سؤال</th><th>نیاز و اقدام</th></tr><tr><td>فصل ۵، سؤال ۲</td><td>جدول مقایسهٔ آهن و سنگ معدن؛ همان جدول پاسخنامه لازم است. فایل تازه لازم نیست.</td></tr><tr><td>فصل ۱۰، سؤال ۱۳</td><td>شکل گردش همرفتی؛ همان شکل پاسخنامه لازم است. فایل تازه لازم نیست.</td></tr><tr><td>فصل ۵، سؤال ۱۱</td><td>نمودار ستونی تکمیل‌شده فقط یک مکمل اختیاری است. به پاسخ مؤلف افزوده نشده است.</td></tr></table>
<p>داشتن عدد، یکا یا حرف انگلیسی به تنهایی دلیل درخواست تصویر تازه نیست. در سؤال ۵ فصل ۷ و سؤال‌های ۵، ۶، ۷ و ۱۸ فصل ۸، قالب محاسبه و کسر با همان PDF حفظ می‌شود. این کار نیاز به تولید نمودار یا عکس اضافه ندارد.</p>
<h2>شکل سؤال‌ها</h2><p>تصاویر تازهٔ سؤال ۱۲ و نسخهٔ بدون زیرنویس سؤال ۱۵ فصل ۳ موجودند. هیچ فایل نمایشی اجباری گم نشده است. چهار پیشنهاد کیفیتِ سؤال در صفحات آخر این گزارش اختیاری‌اند.</p>
<h2>فقط یک مورد برای تصمیم مؤلف</h2><p>کلید چهارگزینه‌ای ۳ فصل ۱۳ در پاسخنامه «ج» است، ولی ترتیب کار دندان‌ها در گزینه‌ها با آن سازگار نیست. اصل سؤال و کلید در این مرحله حفظ شده‌اند؛ یادداشت بررسی جدا از متن کتاب است.</p>''')
    r.case('g7_ch05_q02','جدول پاسخ ـ فصل ۵، سؤال ۲','ضرورت تصویر برای حفظ ارتباط هر ویژگی با ردیف فلز آهن یا سنگ معدن است. جدولِ خود پاسخنامه کافی است؛ بازتولید یا تایپ دوبارهٔ آن ضرورت ندارد.')
    r.case('g7_ch10_q13','شکل پاسخ ـ فصل ۱۰، سؤال ۱۳','درک مسیر گردش آب به جای شعله و پیکان‌های شکل وابسته است. تصویر موجود در پاسخنامه حفظ می‌شود؛ تولید شکل تازه لازم نیست.')
    r.case('g7_ch05_q11','مکمل اختیاری ـ فصل ۵، سؤال ۱۱','پاسخنامه شیوهٔ درجه‌بندی و جواب بخش ب را دارد، اما نمودار تکمیل‌شده رسم نشده است. یک نمودار سبک می‌تواند مکمل باشد؛ متن پاسخ چاپی همچنان مرجع است.',
        'برای ابزار رسم دقیق SVG: نمودار ستونی با سال‌های ۱۳۸۳، ۱۳۸۹، ۱۳۹۲، ۱۳۹۴، ۱۳۹۶ و تولید سیمان به‌ترتیب ۳۳۰۰۰، ۶۱۰۰۰، ۶۸۰۰۰، ۵۸۰۰۰، ۷۰۰۰۰ تن. محور عمودی صفر تا ۸۰۰۰۰ با فاصلهٔ درجهٔ ۲۰۰۰۰، محور افقی «سال»، محور عمودی «تولید سیمان (تن)». سال‌ها دسته‌های هم‌فاصله؛ پس‌زمینه سفید، ستون‌های یک‌رنگ، بدون سه‌بعدی، جملهٔ افزوده یا برجسته‌کردن جواب.')
    r.case('g7_ch13_mcq03','نیازمند تأیید ـ چهارگزینه‌ای ۳ فصل ۱۳','متن سؤال ترتیب «جویدن، بریدن، پاره کردن» را می‌خواهد. گزینهٔ «د» ترتیب «آسیا، پیش، نیش» دارد، ولی پاسخنامه «ج» نوشته است. هیچ‌کدام تغییر نکرده‌اند. ارزیابی فعلیِ این پرسش صریحاً مطابقت با کلید چاپی را می‌سنجد؛ اصلاح محتوایی باید به تصمیم مؤلف باشد.')
    r.case('g7_ch07_q05','حفظ کسرها ـ فصل ۷، سؤال ۵','این مورد درخواست عکس تازه نیست. نسبت رس و ماسه، ترتیب مقایسه و کسرها از قالب چاپی حفظ می‌شوند تا متن استخراجی آن‌ها را جابه‌جا نکند.')
    p=r.page('حفظ محاسبه ـ فصل ۸، سؤال‌های ۵ و ۶')
    y=r.box(p,fitz.Rect(LEFT,120,RIGHT,171),'<p>صورت سؤال و محاسبهٔ پاسخنامه بدون بازنویسی آمده است؛ برای این دو مورد تصویر تولیدشدهٔ تازه لازم نیست.</p>')+8
    for cid in ['g7_ch08_q05','g7_ch08_q06']:
        q=r.records[cid];y=r.region(p,q['questionAsset'],y,'صورت سؤال '+fa(q['number']))
        y=r.region(p,q['answerAsset'],y,'پاسخ چاپی همان سؤال')
    r.case('g7_ch08_q07','حفظ دو مسیر ـ فصل ۸، سؤال ۷','نیروی هر مسیر باید با مسافت همان مسیر مقایسه شود. شکل، واحدها و دو محاسبه از منبع‌اند؛ محاسبه به متن ترکیبیِ بازنویسی‌شده تبدیل نشده است.')
    r.case('g7_ch08_q18','حفظ جدول و جمع ـ فصل ۸، سؤال ۱۸','جرم خوراکی، انرژی هر گرم و مجموع نهایی باید جای درست خود را داشته باشند. قالب جدول سؤال و محاسبهٔ مؤلف مستقیماً نگه داشته می‌شود؛ عکس تازه لازم نیست.')
    optional=[
        ('g7_ch03_q16','مدل‌های اتمی ـ پیشنهاد کیفیت','این شکل با چهارگزینه‌ای ۵ فصل ۳ مشترک است. فایل موجود است؛ وضوح نوشته‌های کوچک می‌تواند بهتر شود.',
         'با تصویر اصلی به‌عنوان مرجع، همان سه مدل، ترتیب، نسبت ابعاد و پیکان‌ها را واضح بازسازی کن. برچسب‌ها و تعدادها عین مرجع بمانند؛ هیچ ذره، مدار، لایه، عدد، نام یا جواب تازه اضافه نشود. قبل از تحویل، همهٔ برچسب‌ها با مرجع مقابله شوند.'),
        ('g7_ch03_q17','هستهٔ اتم ـ پیشنهاد کیفیت','این پیشنهاد مربوط به شکل سؤال است؛ پاسخ اصلی آن متنی است و تصویر پاسخ تازه لازم ندارد.',
         'فقط همان بیضی نارنجی، دایرهٔ آبی کوچک، پیکان و عین عبارت «هسته اتم» مطابق تصویر مرجع واضح شوند. محل و نسبت اجزا ثابت، زمینه سفید؛ بدون ذره، مدار، عنوان، شماره یا توضیح اضافه.'),
        ('g7_ch03_q22','آزمایش بادکنک ـ پیشنهاد کیفیت','تصویر موجود است و نمایش کامل آن حفظ شده؛ نسخهٔ واضح‌تر می‌تواند تشخیص دو حالت را آسان کند.',
         'دو آزمایش را عین تصویر مرجع بازسازی کن: سمت چپ «آب داغ» با بطری و بادکنک بادشده؛ سمت راست «مخلوط آب و یخ» با بطری و بادکنک افتاده. ترتیب، ارتفاع آب و وضعیت وسایل مطابق مرجع؛ بدون دماسنج، عدد دما، پیکان توضیحی یا جواب تازه.'),
        ('g7_ch03_q24','سه حالت آب ـ پیشنهاد کیفیت','این پیشنهاد فقط کیفیت شکل سؤال را بهتر می‌کند و فایل گم‌شده محسوب نمی‌شود.',
         'همان سه دایره و مدل مولکول‌های آبِ مرجع با ترتیب، تعداد، آرایش، رنگ و اتصال‌های یکسان بازسازی شوند. فاصله‌ها مطابق مرجع بمانند؛ هیچ نام حالت، شماره، پیکان یا پاسخ روی تصویر افزوده نشود. تصویر اصلی همین صفحه همراه درخواست باشد.')]
    for args in optional:r.case(*args)
    p=r.page('کنترل نهایی و سابقهٔ قابل پیگیری')
    validation=json.loads((ROOT/'qa/grade7_content_validation.json').read_text())
    proof_file=ROOT/'qa/grade7_v05_verification.json';proof=json.loads(proof_file.read_text())if proof_file.exists()else {}
    status='آزمون نهایی اندروید در حال تکمیل است؛ این پیش‌نویس برای کنترل صفحه‌آرایی است.' if not proof else proof.get('summaryFa','نتیجهٔ آزمون در گزارش گیت ثبت شده است.')
    text=f'''<h2>کنترل منبع</h2><p>فایل کتاب ۶۳ صفحه‌ای و ۱۵ پاسخنامهٔ فصل با اثرانگشت دیجیتال ثابت کنترل می‌شوند. برای هر یک از ۳۱۷ سؤال، ناحیهٔ سؤال و ناحیهٔ پاسخ جدا مشخص است. کلید چهارگزینه‌ای از ستون همان سؤال می‌آید، نه نوار مشترک جواب‌ها.</p>
<p>۲۹ پرسش جای خالی با متن تعاملی و ۵۵ متن خوانای افزوده، از نظر حروف و عددها با منبع مقابله شده‌اند. {fa(validation.get('wholeFloatingFiguresRestored',0))} ناحیهٔ شناورِ شکل یا برچسب کامل شده و {fa(validation.get('neighbouringFigureFragmentsExcluded',0))} قطعهٔ شکل سؤال کناری از نمایش همان سؤال کنار گذاشته شده است. فایل اصلی PDF تغییر نکرده است.</p>
<h2>تصاویر و حجم</h2><p>۲۳ تصویر علمی به WebP بدون تغییر پیکسل تبدیل شده‌اند. نسخهٔ اصلی با نام ارسالی در پوشهٔ بررسی منبع حفظ شده و وارد APK نمی‌شود. جلدها و ربات‌های ارسالی در مجموع حدود نیم مگابایت‌اند. باندل انتشار با کوچک‌سازی ساخته می‌شود.</p>
<h2>آزمون اندروید</h2><p>{esc(status)}</p>
<h2>پیگیری</h2><p>مخزن: <span dir="ltr">github.com/nranjbar/oloomyar7</span><br>فهرست انتقال: <span dir="ltr">docs/GRADE9_TO_GRADE7_1405_CHECKLIST_FA.md</span><br>فهرست تصویر و دستورها: <span dir="ltr">docs/ANSWER_IMAGE_POLICY_1405_FA.md</span></p>
<p class="note">تنها تصمیم محتوایی باز در این گزارش، کلید چهارگزینه‌ای ۳ فصل ۱۳ است. تا دریافت تصمیم مؤلف، سؤال و پاسخ چاپی آن بدون تغییر نگه داشته می‌شوند.</p>'''
    r.box(p,fitz.Rect(LEFT,120,RIGHT,BOTTOM),text)
    r.finish(output)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=ROOT/'dist/OloomYar7_1405_Audit.pdf');args=p.parse_args();build(args.output)
