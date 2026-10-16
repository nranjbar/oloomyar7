"""Build the source-faithful author review, current image lists, and a Persian PDF.

Requires Pillow (RAQM), ReportLab. PDF shows original question/key crops, never editorial cards.
"""
import base64,csv,html,io,json,os
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader

ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'app/src/main/assets/images';OUT=ROOT/'dist';OUT.mkdir(exist_ok=True)
FA=str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹')
fa=lambda s:str(s).translate(FA)
qs=[q for c in (1,2,3) for q in json.loads((ROOT/f'app/src/main/assets/content/chapter{c}.json').read_text())]
byid={q['canonicalId']:q for q in qs}
manifest=json.loads((ROOT/'docs/source_fidelity_manifest.json').read_text())['questions']
answer_rows=[r for r in manifest if r['answerImageRecommended']]
requests=[
 dict(ids=['g7_ch03_q12'],status='لازم؛ فایل تازه نرسیده',reason='مدل ذره‌ای سه ماده در تصویر کتاب کم‌وضوح است. فایل متناظر در بستهٔ ارسالی نیست.',prompt='با استفاده از تصویر سؤال اصلی به عنوان مرجع، فقط سه مدل ذره‌ای را با همان ترتیب و ساختار بازطراحی کن: سمت راست هفت مولکول دواتمی هم‌نوع، وسط آرایش فشرده و منظم اتم‌های هم‌نوع فلز، سمت چپ چهار مولکول آب مطابق مرجع. تعداد، رنگ و اتصال ذره‌ها را حفظ کن. پس‌زمینه سفید، خطوط واضح و خروجی حداقل ۱۶۰۰ پیکسل عرض. هیچ نام ماده، شماره، پاسخ یا نوشتهٔ جدیدی اضافه نکن. زیر هر شکل فقط خط جای خالی مطابق مرجع بماند.'),
 dict(ids=['g7_ch03_q15'],status='لازم؛ نسخهٔ بدون زیرنویس اضافه',reason='فایل ارسالی موجود است اما عبارت «مدل ایزوتوپی: اکسیژن ۱۶ و کربن ۱۲» در کتاب نیست. نسخهٔ فعلی برای حفظ عین سؤال وارد نمایش دانش‌آموز نشده است.',prompt='همان نمودار کربن دی اکسیدِ تصویر اصلی کتاب را تمیز و خوانا بازطراحی کن. ترتیب از راست به چپ: ماده و کپسول، مولکول کربن دی اکسید، تعداد دو نوع اتم با دو خانهٔ خالی، شاخهٔ هر اتم اکسیژن و هر اتم کربن، و سه خانهٔ خالی الکترون و پروتون و نوترون برای هر شاخه. تمام نوشته‌های مرجع عیناً بمانند و هر هشت خانه خالی بماند. هیچ عددِ پاسخ، شمارهٔ تازه، زیرنویس ایزوتوپی، توضیح یا جملهٔ افزوده ننویس. خروجی PNG با پس‌زمینهٔ سفید و عرض حداقل ۲۰۰۰ پیکسل.'),
 dict(ids=['g7_ch03_q16','g7_ch03_mcq05'],status='اختیاری؛ ارتقای کیفیت فایل موجود',reason='فایل مشترک با نام چهارگزینه‌ای ۵ موجود و برای هر دو سؤال استفاده شده است. نوشته‌های کوچک مدل‌ها هنوز کیفیت چاپی ندارند؛ نسخهٔ واضح‌تر مفید است.',prompt='سه مدل اتمیِ مرجع کتاب را با همان اندازه‌های نسبی و همان ترتیب بازطراحی کن. از راست: هسته با (7p, 8n) و برچسب 7e؛ وسط (14p, 14n) و 14e؛ چپ (22p, 26n) و 22e. شکل‌ها، محل پیکان‌ها و همهٔ برچسب‌ها دقیقاً مطابق مرجع باشند. اعداد و حروف را به صورت تایپ واضح وارد کن، هیچ لایهٔ الکترونی، ذرهٔ اضافی، جواب، عنوان یا توضیح جدید اضافه نکن. خروجی PNG با عرض حداقل ۱۸۰۰ پیکسل. پیش از تحویل همهٔ اعداد با مرجع تطبیق داده شوند.'),
 dict(ids=['g7_ch03_q17'],status='اختیاری؛ ارتقای کیفیت فایل موجود',reason='فایل ارسالی موجود است و استفاده شده، اما لبهٔ شکل و نوشتهٔ «هسته اتم» همچنان کم‌وضوح‌اند.',prompt='فقط شکل ساختار اتمِ مرجع را واضح بازطراحی کن: همان بیضی نارنجی، دایرهٔ آبی کوچک در همان محل، پیکان سیاه به سمت راست و عین عبارت «هسته اتم». نسبت ابعاد و محل برچسب حفظ شود. هیچ ذره، مدار، نام، شماره یا نوشتهٔ دیگری اضافه نشود. پس‌زمینه سفید، خطوط واضح و خروجی PNG حداقل ۱۰۰۰ در ۷۰۰ پیکسل.'),
 dict(ids=['g7_ch03_q22'],status='پیشنهادی؛ تصویر تازه نرسیده',reason='تصویر کتاب برای نمایش اولیه موجود است؛ تصویر واضح‌ترِ دو آزمایش، تشخیص بادکنک بادشده و افتاده را آسان‌تر می‌کند.',prompt='دو آزمایش شکل کتاب را دقیقاً با همان ترتیب بازطراحی کن: در سمت چپ ظرف «آب داغ» با بطری و بادکنک بادشده، در سمت راست ظرف «مخلوط آب و یخ» با بطری و بادکنک افتاده. عین دو برچسب کتاب حفظ شود. ارتفاع آب، وضعیت بطری‌ها و حالت بادکنک‌ها مطابق مرجع بماند. هیچ دماسنج، عدد دما، پیکان توضیحی یا پاسخ جدیدی اضافه نکن. زمینه سفید و خروجی حداقل ۱۶۰۰ پیکسل عرض.'),
 dict(ids=['g7_ch03_q24'],status='پیشنهادی؛ تصویر تازه نرسیده',reason='تصویر اصلی سه حالت آب موجود است؛ نسخهٔ خواناتر برای دیدن فاصله و آرایش مولکول‌ها پیشنهاد می‌شود.',prompt='سه دایرهٔ مدل مولکول‌های آب را دقیقاً مطابق تصویر کتاب بازطراحی کن: چپ حالت جامد با آرایش فشرده و منظم، وسط مایع با مولکول‌های نزدیک و نامنظم، راست گاز با فاصله‌های زیاد. هر واحد، همان مدل مولکول آبِ مرجع با اتم‌ها و اتصال‌های یکسان باشد. ترتیب، تعداد ذره‌ها، حدود ظرف‌ها و رنگ‌ها را از مرجع حفظ کن. هیچ نام حالت، شماره، پیکان، توضیح یا پاسخ روی تصویر ننویس. خروجی PNG خوانا و حداقل ۱۸۰۰ پیکسل عرض.')]

def title(q):return f'فصل {fa(q["relatedChapter"])} | '+('چهارگزینه‌ای ' if q['section']=='چهارگزینه‌ای' else 'سؤال ')+fa(q['sourceNumber'])
def atomic(path,raw):
    tmp=path.with_suffix(path.suffix+'.tmp')
    with tmp.open('wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
    os.replace(tmp,path)

def web_review():
    esc=html.escape
    css='''body{margin:0;background:#f4f8f6;color:#15323c;font:17px/1.9 Tahoma,Arial}main{max-width:1050px;margin:auto;padding:25px}header{background:#113c4a;color:white;padding:25px;border-radius:24px}article,section{background:white;border:1px solid #d9e6e1;border-radius:20px;padding:22px;margin:20px 0}img{max-width:100%;height:auto;background:white;border:1px solid #d9e6e1}h2{color:#176b78}button,select{padding:12px;border-radius:12px;border:1px solid #176b78;background:#e6f5f5;color:#15323c;font:inherit}pre{white-space:pre-wrap;font:inherit;background:#fff2be;padding:18px;border-radius:12px}table{width:100%;border-collapse:collapse}td,th{border:1px solid #d9e6e1;padding:10px;text-align:right}details{padding:10px;background:#f8faf8;margin:10px 0}summary{cursor:pointer;color:#6250a8}.muted{color:#526773}.badge{display:inline-block;background:#fff2be;padding:4px 12px;border-radius:20px}nav{position:sticky;top:0;background:#f4f8f6;padding:12px;z-index:1}@media print{nav,button{display:none}article{break-inside:avoid}details{display:block}}'''
    parts=['<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>بازبینی سه فصل اول علوم‌یار هفتم</title><style>'+css+'</style><main>',
        '<header><h1>بازبینی سه فصل اول علوم‌یار هفتم</h1><p>۵۹ سؤال از کتاب کار علوم هفتم ۱۴۰۵؛ صورت سؤال و پاسخ عین PDF مؤلف.</p><p>این گزارش برای بازبینی مؤلف است. در اپ دانش‌آموز، نمایش پاسخ به دو تلاش کامل نادرستِ متفاوت و درخواست صریح وابسته است.</p></header>',
        '<nav><select id="chapter"><option value="all">همهٔ فصل‌ها</option>'+''.join(f'<option value="{c}">فصل {fa(c)}</option>' for c in (1,2,3))+'</select> <button onclick="document.getElementById(\'assets\').scrollIntoView()">تصاویر باقی‌مانده</button> <button onclick="document.getElementById(\'answers\').scrollIntoView()">۳۲ پاسخ تصویری</button></nav>',
        '<section id="assets"><h2>تصاویر قابل پیگیری</h2><p>۱۳ تصویر ارسالی در اپ استفاده شده‌اند. تصویر سؤال ۱۵ فصل ۳ به دلیل نوشتهٔ افزوده کنار گذاشته شده و تصویر کتاب موقتاً نمایش داده می‌شود.</p>']
    for req in requests:
        q=byid[req['ids'][0]]
        parts.append('<article><h3>'+esc(' / '.join(title(byid[i]) for i in req['ids']))+'</h3><span class="badge">'+esc(req['status'])+'</span><p>'+esc(req['reason'])+'</p><h4>صورت سؤال کتاب</h4><img src="../app/src/main/assets/images/'+q['sourceQuestionVisual']+'" alt="صورت اصلی"><h4>پاسخ کتاب‌کار</h4><img src="../app/src/main/assets/images/'+q['answerVisual']+'" alt="پاسخ اصلی"><h4>پرامپت پیشنهادی؛ تصویر اصلی کتاب را مرجع قرار بده</h4><pre>'+esc(req['prompt'])+'</pre></article>')
    parts.append('</section><section id="answers"><h2>۳۲ پاسخ با نمایش تصویری</h2><p>برای تمام این موارد، برش پاسخ‌نامه آماده و متصل است؛ تولید تصویر تازه برای پاسخ‌ها لازم نیست. اعداد، فرمول، نماد یا ساختار چندبخشی در تصویر اصلی حفظ می‌شوند.</p><table><tr><th>سؤال</th><th>علت نمایش تصویری</th></tr>')
    for r in answer_rows:parts.append('<tr><td><a href="#'+r['id']+'">'+esc(title(byid[r['id']]))+'</a></td><td>'+esc('؛ '.join(r['formattingRisks']))+'</td></tr>')
    parts.append('</table></section>')
    for q in qs:
        parts.append(f'<article class="question" id="{q["canonicalId"]}" data-ch="{q["relatedChapter"]}"><h2>'+esc(title(q))+'</h2><p class="muted">صفحهٔ '+fa(q['sourcePage'])+' کتاب کار ۱۴۰۵</p><h3>صورت کامل سؤال</h3><img loading="lazy" src="../app/src/main/assets/images/'+q['sourceQuestionVisual']+'" alt="صورت کامل سؤال از کتاب">')
        if q.get('visual'):parts.append('<details><summary>تصویر ارسالی استفاده‌شده</summary><img loading="lazy" src="../app/src/main/assets/images/'+q['visual']+'" alt="تصویر ارسالی"></details>')
        parts.append('<details><summary>پاسخ اصلی پاسخ‌نامه</summary><img loading="lazy" src="../app/src/main/assets/images/'+q['answerVisual']+'" alt="پاسخ اصلی"></details><details><summary>راهنماهای تمرین</summary>')
        for i,s in enumerate(q['steps'],1):parts.append('<h4>قسمت '+fa(i)+'</h4><p>راهنمای ۱: '+esc(s['hint1'])+'</p><p>راهنمای ۲: '+esc(s['hint2'])+'</p>')
        parts.append('</details></article>')
    parts.append('''</main><script>document.getElementById('chapter').addEventListener('change',e=>document.querySelectorAll('.question').forEach(q=>q.hidden=e.target.value!=='all'&&q.dataset.ch!==e.target.value));</script></html>''')
    page='\n'.join(parts);atomic(ROOT/'review/index.html',page.encode())
    # An offline copy, with each image encoded once even if it appears on several cards.
    import re
    files=sorted(set(re.findall(r'src="(\.\./app/src/main/assets/images/[^"]+)"',page)))
    assets={f:'data:image/png;base64,'+base64.b64encode((ROOT/'review'/f).read_bytes()).decode() for f in files}
    standalone=re.sub(r'src="(\.\./app/src/main/assets/images/[^"]+)"',lambda m:'data-image="'+m[1]+'"',page)
    standalone=standalone.replace('</html>','<script>const images='+json.dumps(assets)+';document.querySelectorAll("img[data-image]").forEach(i=>i.src=images[i.dataset.image]);</script></html>')
    atomic(OUT/'Grade7_Chapters1-3_Review_FA.html',standalone.encode())
    dump=dict(scope='chapters1-3',sourcePolicy='verbatim',requests=requests,answerImageList=answer_rows,answerImagesPrepared=True)
    atomic(ROOT/'docs/image_audit_chapters1-3.json',(json.dumps(dump,ensure_ascii=False,indent=2)+'\n').encode())
    with (ROOT/'review/Grade7_Answer_Images_Needed.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f,lineterminator="\n");w.writerow(['شناسه','سؤال','علت تصویر','فایل پاسخ','وضعیت'])
        w.writerows([r['id'],title(byid[r['id']]),'؛ '.join(r['formattingRisks']),r['answerAsset'],'برش اصلی آماده؛ تصویر جدید لازم نیست'] for r in answer_rows)
    lines=['# تصاویر سه فصل اول','', 'متن سؤال و پاسخ عین منبع است؛ فهرست زیر درخواست تغییر علمی نیست.','']
    for r in requests:lines+=['## '+' / '.join(title(byid[i]) for i in r['ids']),'',r['status']+'؛ '+r['reason'],'','پرامپت پیشنهادی با مرجع تصویر کتاب:','',r['prompt'],'']
    lines+=['## پاسخ‌های تصویری','', 'همهٔ ۳۲ برش آماده‌اند؛ نیاز به تولید تصویر جدید ندارند.','']
    lines+=['- '+title(byid[r['id']])+'؛ '+'؛ '.join(r['formattingRisks']) for r in answer_rows]
    atomic(ROOT/'docs/IMAGE_AUDIT_CHAPTERS1_3_FA.md',('\n'.join(lines)+'\n').encode())

FONT='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf';BOLD='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
W,H=1800,2546;M=110;INK='#15323c';TEAL='#176b78';MUTED='#526773'
def paragraph(draw,text,y,size=31,color=INK,bold=False,width=W-2*M):
    font=ImageFont.truetype(BOLD if bold else FONT,size)
    for para in text.split('\n'):
        line=''
        for word in para.split():
            candidate=(line+' '+word).strip()
            if line and draw.textlength(candidate,font=font,direction='rtl')>width:
                draw.text((W-M,y),line,font=font,fill=color,anchor='ra',direction='rtl');y+=size*1.65;line=word
            else:line=candidate
        if line:draw.text((W-M,y),line,font=font,fill=color,anchor='ra',direction='rtl');y+=size*1.65
        y+=size*.3
    return int(y)

def draw_image(page,path,y,maxh):
    im=Image.open(path).convert('RGB');scale=min((W-2*M)/im.width,maxh/im.height)
    w,h=round(im.width*scale),round(im.height*scale);resized=im.resize((w,h),Image.Resampling.LANCZOS)
    page.paste(resized,(M+(W-2*M-w)//2,int(y)));return int(y+h+28)

def pdf_review():
    path=OUT/'Grade7_Chapters1-3_Image_Audit_FA.pdf';c=canvas.Canvas(str(path),pagesize=(595.276,841.89));c.setTitle('Grade 7 1405: Chapters 1-3 source image audit')
    count=1+len(requests)+len(answer_rows);number=0
    def page(title_text):
        nonlocal number
        number+=1;im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im)
        d.rectangle((0,0,W,28),fill=TEAL)
        y=paragraph(d,title_text,92,44,bold=True)
        y=paragraph(d,'کتاب کار علوم هفتم ۱۴۰۵ | سه فصل اول',y+8,27,color=MUTED)
        d.line((M,H-100,W-M,H-100),fill='#d9e6e1',width=2)
        paragraph(d,f'بازبینی مؤلف | {fa(number)} از {fa(count)}',H-78,23,color=MUTED)
        return im,d,y+25
    def finish(im,y):
        assert y<H-130,('page overflow',number,y)
        b=io.BytesIO();im.save(b,format='PNG');b.seek(0)
        c.drawImage(ImageReader(b),0,0,width=595.276,height=841.89);c.showPage()
    im,d,y=page('گزارش تصاویر و پاسخ‌های سه فصل اول')
    y=paragraph(d,'۵۹ سؤال | ۳۲ پاسخ با نمایش تصویری | ۶ تصویر قابل پیگیری',y,35,color=TEAL,bold=True)
    y=paragraph(d,'اصل مورد تأکید مؤلف: هیچ سؤال یا پاسخی بازنویسی، اصلاح علمی یا کوتاه نشده است. صورت سؤال و پاسخ در این گزارش، تصویر خود PDF اصلی است.',y+20)
    y=paragraph(d,'برای پاسخ‌ها تصویر تازه لازم نیست. برش‌های اصلی همهٔ ۳۲ مورد آماده و به اپ متصل شده‌اند. چند برش قدیمی که پاسخ بعدی یا کسر ناقص داشتند از PDF دوباره استخراج شدند.',y+18)
    y=paragraph(d,'تصاویر سؤال‌ها',y+32,37,color=TEAL,bold=True)
    for r in requests:y=paragraph(d,' / '.join(title(byid[i]) for i in r['ids'])+' - '+r['status'],y+10,30)
    y=paragraph(d,'فهرست ۳۲ پاسخ تصویری',y+30,37,color=TEAL,bold=True)
    for cnum in (1,2,3):
        group=[byid[r['id']] for r in answer_rows if byid[r['id']]['relatedChapter']==cnum]
        mains=[fa(q['sourceNumber']) for q in group if q['section']!='چهارگزینه‌ای'];mcqs=[fa(q['sourceNumber']) for q in group if q['section']=='چهارگزینه‌ای']
        y=paragraph(d,'فصل '+fa(cnum)+'، اصلی: '+ '، '.join(mains)+ ('؛ چهارگزینه‌ای: '+'، '.join(mcqs) if mcqs else ''),y+8,29)
    y=paragraph(d,'در صفحات بعد، صورت کامل سؤال، پاسخ اصلی و علت نیاز به تصویر آمده است. پرامپت‌ها فقط برای خواناترکردن شکل‌اند و اجازهٔ تغییر متن یا پاسخ را نمی‌دهند.',y+25,29,color=MUTED)
    finish(im,y)
    for r in requests:
        q=byid[r['ids'][0]];im,d,y=page(' / '.join(title(byid[i]) for i in r['ids']))
        y=paragraph(d,r['status'],y,32,color=TEAL,bold=True)
        y=paragraph(d,'صورت کامل سؤال کتاب',y+16,31,bold=True);y=draw_image(im,ASSETS/q['sourceQuestionVisual'],y,600)
        y=paragraph(d,'پاسخ اصلی پاسخ‌نامه',y+5,31,bold=True);y=draw_image(im,ASSETS/q['answerVisual'],y,260)
        y=paragraph(d,'علت نیاز: '+r['reason'],y+8,29)
        y=paragraph(d,'پرامپت پیشنهادی با مرجع تصویر اصلی',y+20,32,color=TEAL,bold=True)
        y=paragraph(d,r['prompt'],y+5,29)
        finish(im,y)
    for r in answer_rows:
        q=byid[r['id']];im,d,y=page(title(q)+' - پاسخ با نمایش تصویری')
        y=paragraph(d,'صورت کامل سؤال کتاب',y,31,bold=True);y=draw_image(im,ASSETS/q['sourceQuestionVisual'],y,1100)
        y=paragraph(d,'پاسخ اصلی پاسخ‌نامه',y+12,31,bold=True);y=draw_image(im,ASSETS/q['answerVisual'],y,110 if q['section']=='چهارگزینه‌ای' else 560)
        y=paragraph(d,'علت نمایش تصویری: '+'؛ '.join(r['formattingRisks']),y+16,30,color=TEAL)
        y=paragraph(d,'وضعیت: برش اصلی آماده و متصل است. تصویر جدید از شما لازم نیست.',y+10,29,color=MUTED)
        finish(im,y)
    c.save();print(path,'pages',number,'bytes',path.stat().st_size)

if __name__=='__main__':web_review();pdf_review()
