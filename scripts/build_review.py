from pathlib import Path
import base64, csv, html, json, shutil, zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'dist'
OUT.mkdir(exist_ok=True)
FOLDER=ROOT/'review'
PROJECT=ROOT
qs=[]
for ch in (1,2):
    qs.extend(json.loads((PROJECT/f'app/src/main/assets/content/chapter{ch}.json').read_text()))
images=json.loads((FOLDER/'image_manifest.json').read_text())
fa=lambda x:str(x).translate(str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹'))
esc=lambda x:html.escape(str(x))
def inline_image(name):
    # The folder version shares actual files; the standalone export embeds each
    # unique file once, even when it appears on several review tabs.
    return 'question_images/'+name

suggestions=[
    [1,'اصلی',5,2,'مفید','g7_ch01_q05_answer.png','نمودار ترتیب روش علمی','پنج مرحله مشاهده، طرح پرسش، فرضیه‌سازی، آزمایش و نتیجه‌گیری، با جهت خواندن روشن','کمک به یادگیری ترتیب مراحل؛ پاسخ متنی فعلی کامل است.'],
    [2,'اصلی',2,3,'مفید','g7_ch02_q02_answer.png','جدول کامل‌شده چهار کمیت','همان چهار ستون و چهار ردیف کتاب، با پاسخ‌های جرم، وزن، حجم و چگالی','مقایسه همزمان جرم و وزن و مرور همه ردیف‌ها ساده‌تر می‌شود.'],
    [2,'اصلی',7,4,'اولویت بالا','g7_ch02_q07_answer.png','قبل و بعد از قرار دادن سنگ در آب','استوانه قبل و بعد، برچسب V1 و V2، سنگ کاملاً زیر آب و رابطه حجم سنگ = V2 − V1','این سؤال طراحی آزمایش است؛ تصویر مراحل به فهم پاسخ کمک زیادی می‌کند.'],
    [2,'اصلی',9,4,'اولویت بالا','g7_ch02_q09_answer.png','محاسبه حجم و چگالی سنگ','ترازو ۳۶ گرم، استوانه قبل ۶۰ و بعد ۷۲ سانتی‌متر مکعب؛ نتیجه حجم ۱۲ و چگالی ۳','تصویر سؤال و تصویر پاسخ هر دو دریافت شده و عددهای آن‌ها با متن هماهنگ‌اند.'],
    [2,'اصلی',10,5,'مفید','g7_ch02_q10_answer.png','جسم شناور و مقایسه چگالی با آب','جرم ۱۶۰ گرم، حجم ۴۰۰ سانتی‌متر مکعب؛ چگالی جسم ۰٫۴ و آب حدود ۱ گرم بر سانتی‌متر مکعب','نشان می‌دهد چرا جسم با چگالی کمتر روی آب می‌ماند.'],
    [2,'اصلی',11,5,'مفید','g7_ch02_q11_answer.png','شکل پاسخ‌دار سه گلوله','حرف‌های A، B، C حفظ شوند؛ کنار آن‌ها کمتر از آب، برابر آب و بیشتر از آب درج شود','پیوند وضعیت گلوله و چگالی را واضح می‌کند؛ برچسب‌ها در تصویر سؤال اضافه نشوند تا جواب لو نرود.'],
    [2,'اصلی',14,5,'اولویت بالا','g7_ch02_q14_answer.png','خط‌کش خوانا با پاسخ ۱۶ و ۱۲','صفر خط‌کش و نوک مدادها حفظ؛ شکل ۱ با نزدیک‌ترین عدد ۱۶ و شکل ۲ با ۱۲ مشخص شود','خواندن و گردکردن طول با دقت ۱ سانتی‌متر را نشان می‌دهد.'],
]
columns=['فصل','بخش','شماره سؤال','صفحه کتاب','اولویت','نام فایل','عنوان تصویر پاسخ','جزئیات علمی','علت استفاده','وضعیت']
csv_path=OUT/'Grade7_Answer_Images_Needed.csv'
with csv_path.open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f);w.writerow(columns);w.writerows(s+['دریافت و به سؤال متصل شد'] for s in suggestions)
shutil.copy2(csv_path,FOLDER/csv_path.name)
needed_md='''# وضعیت تصاویر پاسخ‌ها

هر هفت تصویر پیشنهادی دریافت و بررسی شد و اکنون به سؤال مربوط متصل است. همه فایل‌ها در پوشه `answers` قرار دارند. تصویر کامل پاسخ فقط پس از تکمیل همه بخش‌های سؤال با پاسخ درست یا استفاده مجاز از «دیدن پاسخ» نمایش داده می‌شود. نتیجه آزمون پاسخ‌ها را مستقیم نشان نمی‌دهد و امکان ورود به تمرین با راهنما را دارد.

'''
needed_md+='| فصل | سؤال | صفحه | اولویت | تصویر پیشنهادی | جزئیات |\n|---|---|---|---|---|---|\n'
for s in suggestions:
    needed_md+='| '+' | '.join(str(s[i]) for i in (0,2,3,4,6,7))+' |\n'
needed_md+='\nتصویر اصلاح‌شده سؤال ۹ فصل ۲ دریافت شد: جرم ۳۶ گرم و حجم‌های ۶۰ و ۷۲ سانتی‌متر مکعب. در سؤال ۷، عددهای تصویر سؤال و تصویر پاسخ دو مثال مختلف از یک روش آزمایش‌اند؛ خود سؤال طراحی آزمایش می‌خواهد. در سؤال ۱۴، عددهای اعشاری تصویر پاسخ فقط مثال روش گردکردن‌اند و گزارش نهایی سؤال در حد سانتی‌متر کامل است.\n'
(FOLDER/'Answer_Images_Needed_FA.md').write_text(needed_md)

readme='''# بازبینی علوم‌یار هفتم، فصل‌های ۱ و ۲، نسخه ۰٫۲٫۱

۱. ZIP را از حالت فشرده خارج کنید و `index.html` را باز کنید. متن اصلی ۲۸ سؤال، ۴۱ مرحله، ۸۲ راهنما و همه تصاویر سؤال و پاسخ قابل بررسی‌اند. این فایل مخصوص بازبینی معلم است و پاسخ‌ها را قابل مشاهده می‌کند؛ مسیر دانش‌آموز در اپ قفل مرحله‌ای دارد.
۲. `question_images` شامل ۱۱ تصویر سؤال و `answers` شامل هفت تصویر پاسخ دریافت‌شده است. فایل‌های ارسالی بدون کاهش کیفیت وارد اپ شده‌اند. وضعیت هفت تصویر پاسخ در CSV و فایل توضیح فارسی ثبت شده است.
۳. `original_embedded` و `source_pages` تصاویر و صفحه‌های PDF اولیه را برای مقایسه نگه می‌دارند؛ `image_manifest.json` ابعاد و هش فایل‌های فعلی و مشخصات استخراج اولیه را جدا ثبت می‌کند.
۴. در سؤال ۱۲ فصل ۲، شماره ۱ ساعت و شماره ۲ زمان‌سنج است. در سؤال ۱۵، شماره ۱ ترازو، شماره ۲ استوانه مدرج و شماره ۳ گروه نیروسنج‌هاست. در سؤال ۱۱، A و B و C و در سؤال ۱۴، شماره‌های ۱ و ۲ حفظ شده‌اند.
۵. از نسخه ۰٫۲، امضای نسخه‌های آزمایشی ثابت است تا ساخت‌های بعدی روی هم نصب شوند. APK قبلی ۰٫۱ امضای متفاوت دارد و با این APK به‌روزرسانی نمی‌شود؛ حذف آن پیشرفت ذخیره‌شده را پاک می‌کند.
۶. تصویر سؤال ۹ اصلاح شده و با داده‌های متن سازگار است: ۳۶ گرم، ۶۰ و ۷۲ سانتی‌متر مکعب. در سؤال ۷ تصویرها مثال روش آزمایش‌اند؛ در سؤال ۱۴ عددهای اعشاری تصویر پاسخ مثال گردکردن‌اند.
۷. دکمه بررسی و ادامه ثابت است؛ «انتخاب‌ها»، «راهنما» و «دیدن پاسخ» زیر متن و تصویر پنهان نمی‌شوند. جای‌خالی قبلی و بعدی دکمه مستقل دارند. گزارش تغییر در `GRADE7_NAVIGATION_FIX_FA.md` است. نسخه ۰٫۲٫۱ روی نسخه ۰٫۲ با حفظ پیشرفت نصب می‌شود.

در اپ، اولین پاسخ نادرست کامل راهنمایی ۱ و دومین پاسخ نادرست کاملِ متفاوت راهنمایی ۲ را نشان می‌دهد. سپس «دیدن پاسخ» فعال می‌شود. ارسال دوباره همان انتخاب یا پاسخ ناقص تلاش تازه حساب نمی‌شود. پاسخ درست اجازه ادامه دارد. تصویر کامل پاسخ تا پایان همه قسمت‌های سؤال پنهان می‌ماند. برگشت به سؤال و مرور آزمون این قاعده را دور نمی‌زنند.
'''
(FOLDER/'README_FA.md').write_text(readme)
if (FOLDER/'app_screenshots').exists():
    (FOLDER/'README_FA.md').write_text(readme+'\nپوشه `app_screenshots` تصویرهای واقعی برنامه را دارد. شناسه کد آزموده‌شده، هش APK و نتیجه آزمون‌ها در `android_verification.json` ثبت شده است.\n')

kinds={'single':'انتخاب یک پاسخ','multi':'انتخاب چند پاسخ','match':'جای خالی / تطبیق به ترتیب','order':'مرتب‌کردن مراحل','classify':'دسته‌بندی'}
cards=[]
for q in qs:
    ch=q['relatedChapter'];n=q['sourceNumber'];section=q['section']
    body=f'<article class="question" data-ch="{ch}" data-section="{esc(section)}"><div class="tags"><span>فصل {fa(ch)}</span><span>{esc(section)}</span><span>صفحه {fa(q["sourcePage"])}</span></div><h2>سؤال {fa(n)} · {esc(q["title"])}</h2><h3>صورت اصلی سؤال کتاب</h3><div class="source">{esc(q["bookPrompt"])}</div>'
    if q.get('visual'):
        body+=f'<figure><img src="{inline_image(q["visual"])}" alt="{esc(q.get("visualCaption",q["title"]))}"><figcaption>{esc(q.get("visualCaption", ""))}<br><code dir="ltr">{q["visual"]}</code></figcaption></figure>'
    for note in q.get('editorialNotes',[]): body+=f'<p class="notice">{esc(note)}</p>'
    body+=f'<details><summary>مراحل پاسخ تعاملی · {fa(len(q["steps"]))} مرحله</summary>'
    for i,s in enumerate(q['steps'],1):
        body+=f'<section class="step"><span class="kind">{kinds[s["kind"]]}</span><h4>مرحله {fa(i)}: {esc(s["prompt"])}</h4>'
        if s['kind'] in ('single','multi'):
            if s['kind']=='multi':body+=f'<p>تعداد انتخاب لازم: {fa(s["pick"])}</p>'
            body+='<ul>'+''.join(f'<li>{esc(o["text"])}</li>' for o in s['options'])+'</ul>'
        elif s['kind']=='match':
            body+='<ol>'+''.join(f'<li>{esc(p["left"])}<p class="options">گزینه‌ها: {esc(" · ".join(p.get("choices") or s.get("choices") or [x["right"] for x in s["pairs"]]))}</p></li>' for p in s['pairs'])+'</ol>'
        elif s['kind']=='order':body+='<p>در اپ، ترتیب اولیه جابه‌جا است و با دکمه‌های بالا و پایین مرتب می‌شود.</p><ul>'+''.join(f'<li>{esc(t)}</li>' for t in s['items'])+'</ul>'
        body+=f'<details class="answer"><summary>پاسخ، دو راهنما و توضیح</summary><p class="pre"><b>پاسخ:</b><br>{esc(s["answer"])}</p><p><b>راهنمای اول:</b> {esc(s["hint1"])}</p><p><b>راهنمای دوم:</b> {esc(s["hint2"])}</p><p><b>توضیح:</b> {esc(s["explanation"])}</p></details></section>'
    if q.get('answerVisual'):
        body+=f'<h3>تصویر کامل پاسخ</h3><p>در اپ پس از تکمیل همه قسمت‌های سؤال نمایش داده می‌شود.</p><figure><img src="answers/{q["answerVisual"]}" alt="تصویر پاسخ سؤال {fa(n)}"><figcaption>{esc(q.get("answerVisualCaption", ""))}<br><code dir="ltr">{q["answerVisual"]}</code></figcaption></figure>'
    body+='</details></article>'
    cards.append(body)
image_cards=[]
for m in images:
    label=f'فصل {fa(m["chapter"])} · '+('چهارگزینه‌ای ' if m['section']=='mcq' else 'سؤال ')+fa(m['sourceNumber'])
    sizes='؛ '.join(f'{o["width"]}×{o["height"]}' for o in m['originalImages']) or 'شکل برداری / متن PDF'
    image_cards.append(f'<article><div class="tags"><span>{label}</span><span>صفحه {fa(m["sourcePage"])}</span></div><h2>{esc(m["caption"])}</h2><figure><img src="{inline_image(m["file"])}" alt="{esc(m["caption"])}"><figcaption><code dir="ltr">{m["file"]}</code></figcaption></figure><p class="notice">{esc(m["qualityNote"])}</p><p>ابعاد فایل اپ: <b dir="ltr">{m["width"]}×{m["height"]}</b><br>ابعاد واقعی تصاویر اصلی: <b dir="ltr">{sizes}</b></p></article>')
recommendations='<div class="table-wrap"><table><thead><tr>'+''.join(f'<th>{esc(t)}</th>' for t in ['فصل / سؤال','وضعیت','تصویر پاسخ','جزئیات و دلیل'])+'</tr></thead><tbody>'
for s in suggestions:
    recommendations+=f'<tr><td>فصل {fa(s[0])}<br>سؤال {fa(s[2])}<br>صفحه {fa(s[3])}</td><td>دریافت و متصل شد</td><td>{s[6]}<br><code dir="ltr">{s[5]}</code></td><td>{s[7]}<p>{s[8]}</p></td></tr>'
recommendations+='</tbody></table></div>'
preview_cards=[]
preview_names={
    'grade7-01-home.png':'صفحه اصلی واقعی علوم‌یار هفتم',
    'grade7-02-source-question.png':'صورت اصلی سؤال قبل از تعامل',
    'grade7-03-ordered-blanks.png':'انتخاب مرحله‌ای و مرتب جای خالی‌ها',
    'grade7-04-chapter2-sections.png':'صفحه فصل ۲ و بخش‌های مستقل',
    'grade7-05-source-figure-zoom.png':'بزرگ‌نمایی شکل سؤال',
    'grade7-06-first-attempt-hint1.png':'تلاش اول: راهنمایی ۱ و پاسخ قفل‌شده',
    'grade7-07-second-attempt-hint2.png':'تلاش دوم: راهنمایی ۲ و امکان دیدن پاسخ',
    'grade7-08-answer-image-after-parts.png':'تصویر پاسخ پس از تکمیل همه قسمت‌ها',
    'grade7-09-last-question-quantities-answer.png':'پاسخ بخش دوم آخرین سؤال فصل ۲',
    'grade7-10-fixed-next-part-large-text.png':'دکمه ادامه ثابت در آخرین سؤال با متن بزرگ',
}
for name,title in preview_names.items():
    p=FOLDER/'app_screenshots'/name
    if p.exists():
        data='app_screenshots/'+name
        preview_cards.append(f'<article class="screen-card"><h2>{title}</h2><figure><img src="{data}" alt="{title}"></figure></article>')
css='''*{box-sizing:border-box}body{margin:0;background:#f3f6fc;color:#17233e;font:16px/1.9 Tahoma,Arial,sans-serif}header{background:linear-gradient(125deg,#0d1e42,#246be9);color:white;padding:36px 22px}header>div,main{max-width:1100px;margin:auto}h1{font-size:29px;margin:6px 0}header p{color:#d7e5ff;max-width:760px}.metrics{display:flex;gap:12px;flex-wrap:wrap}.metrics span{background:#ffffff20;border-radius:14px;padding:10px 20px}nav{position:sticky;top:0;background:#ffffffee;border-bottom:1px solid #dce5f4;z-index:2;padding:12px;display:flex;gap:9px;flex-wrap:wrap;justify-content:center}button,select{border:1px solid #d1daed;background:white;color:#245cb9;border-radius:12px;padding:10px 16px;cursor:pointer;font:inherit}button.active{background:#285ddc;color:white}main{padding:22px 18px 70px}article,.intro{background:white;border:1px solid #e1e8f3;border-radius:24px;padding:24px;margin:18px 0;box-shadow:0 6px 25px #11214405}h2{font-size:21px;line-height:1.8;margin:12px 0}h3{font-size:16px;color:#7150c0;margin-top:25px}.source,.pre{white-space:pre-line}.source{border:1px solid #e5ddfa;background:#faf8ff;padding:18px;border-radius:16px}.tags{display:flex;gap:8px;flex-wrap:wrap}.tags span,.kind{font-size:13px;padding:4px 10px;background:#eef4ff;color:#2558b2;border-radius:12px}figure{text-align:center;margin:22px 0}img{max-width:100%;height:auto;max-height:760px;border:1px solid #e0e5ed;border-radius:12px;object-fit:contain;background:white}figcaption{font-size:13px;color:#63708a;line-height:1.8;margin:10px 0}code{font:13px/1.6 monospace;direction:ltr;unicode-bidi:isolate;overflow-wrap:anywhere}.notice{background:#fff6df;border:1px solid #f0dda8;border-radius:13px;padding:14px;color:#765111}.step{border:1px solid #dbe7fa;border-radius:16px;padding:18px;margin:16px 0}h4{margin:12px 0;line-height:1.9}.options{color:#64708a;font-size:14px}summary{cursor:pointer;color:#2858ac;font-weight:bold;padding:10px 0}.answer{background:#f1f9f4;border:1px solid #d2e9d8;border-radius:14px;padding:12px 16px;margin-top:18px}.answer summary{color:#227144}table{width:100%;border-collapse:collapse;min-width:720px}td,th{padding:16px;text-align:right;vertical-align:top;border-bottom:1px solid #e0e7f1}th{background:#edf3ff;color:#2553a1}.table-wrap{overflow:auto;background:white;border-radius:18px}.filters{display:flex;gap:12px;align-items:center;flex-wrap:wrap}a{color:#295cc7}.panel{display:none}.panel.active{display:block}@media(max-width:600px){article,.intro{padding:18px}header{padding:26px 18px}h1{font-size:25px}nav{position:static}h2{font-size:19px}}'''
page='<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>بازبینی علوم‌یار هفتم · فصل ۱ و ۲</title><style>'+css+'</style></head><body>'
page+='''<header><div><small>کتاب کار علوم هفتم ۱۴۰۵ · تألیف علی رنجبر</small><h1>بازبینی فصل‌های ۱ و ۲ علوم‌یار هفتم</h1><p>این فایل برای بررسی متن، مراحل تعاملی و کیفیت تصاویر است. رابط اندروید همان طراحی قبلی را دارد؛ راهنماها و قفل پاسخ برای هر بخش سؤال بازبینی شده‌اند.</p><div class="metrics"><span>۲۸ سؤال کتاب</span><span>۴۱ مرحله تعاملی</span><span>۱۱ تصویر سؤال</span><span>۷ تصویر پاسخ دریافت‌شده</span><span>۸۲ راهنمای اختصاصی</span></div></div></header><nav><button data-panel="questions" class="active">سؤال‌ها و پاسخ‌ها</button><button data-panel="images">تصاویر سؤال‌ها</button><button data-panel="needed">تصاویر پاسخ دریافت‌شده</button><button data-panel="notes">نکات بازبینی</button></nav><main>'''
if preview_cards:
    page=page.replace('<button data-panel="notes">','<button data-panel="appearance">ظاهر واقعی اپ</button><button data-panel="notes">')
page+='''<div id="questions" class="panel active"><div class="intro"><p>ابتدا صورت اصلی کتاب نمایش داده شده؛ مراحل تعاملی و پاسخ‌ها با باز کردن کادرهای پایین قابل بررسی‌اند. فصل ۱ چهارگزینه‌ای ندارد؛ فصل ۲ چهارگزینه‌ای‌های کتاب را جدا دارد.</p><div class="filters"><label>فصل <select id="chapter"><option value="all">هر دو فصل</option><option value="1">فصل ۱</option><option value="2">فصل ۲</option></select></label><label>بخش <select id="section"><option value="all">همه سؤال‌ها</option><option>تمرین‌های اصلی</option><option>چهارگزینه‌ای</option></select></label><span id="count">۲۸ سؤال</span></div></div>'''+''.join(cards)+'</div>'
page+='<div id="images" class="panel"><div class="intro"><h2>تصاویر سؤال‌ها</h2><p>فایل‌های PNG همان فایل‌های استفاده‌شده در برنامه‌اند. اصل تصاویر کوچک PDF نیز داخل پوشه original_embedded قرار دارد. اندازه بزرگ‌تر رندر به معنی افزایش جزئیات تصویر اصلی نیست.</p><p class="notice">تصویر اصلاح‌شده سؤال ۹ دریافت شده و با داده‌های متن هماهنگ است. تصاویر ارسالی بدون کاهش کیفیت وارد اپ شده‌اند. برای هر فایل، شماره سؤال، اندازه و سابقه منبع ثبت شده است.</p></div>'+''.join(image_cards)+'</div>'
page+='<div id="needed" class="panel"><div class="intro"><h2>هفت تصویر پاسخ دریافت‌شده</h2><p>هر هفت پیشنهاد قبلی اکنون دریافت و به سؤال مربوط متصل شده است. تصویر کامل پاسخ در اپ پس از تکمیل همه قسمت‌ها نمایش داده می‌شود. این صفحه مخصوص بازبینی معلم است؛ پاسخ‌ها در مسیر دانش‌آموز قفل مرحله‌ای دارند.</p></div>'+recommendations+'</div>'
if preview_cards:
    page+='<div id="appearance" class="panel"><div class="intro"><h2>ظاهر واقعی نسخه اندروید ۰٫۲٫۱</h2><p>این تصویرها از خود برنامه روی شبیه‌ساز اندروید گرفته شده‌اند. دکمه بررسی و ادامه همیشه در دسترس است. حرکت بین بخش‌های سؤال، نمایش دو راهنما و متن بزرگ آزموده شده‌اند. نتیجه ساخت و آزمون‌ها، همراه با شناسه کد و هش APK، در گزارش android_verification.json ثبت شده است.</p></div><div class="screen-grid">'+''.join(preview_cards)+'</div></div>'
page+='''<div id="notes" class="panel"><article><h2>روش بررسی و جایگزینی</h2><ol><li>تصاویر موجود را با سؤال و صفحه اصلی مقایسه کنید.</li><li>در صورت نیاز، تصویر بهتر را با همان نام در پوشه question_images جایگزین کنید.</li><li>حرف‌ها، عددها و جای اجزای مورد اشاره در سؤال را حفظ کنید؛ مخصوصاً A، B، C و شماره شکل‌های ۱ و ۲.</li><li>پوشه اصلاح‌شده را ZIP کنید و ارسال کنید. برای تصاویر جدید پاسخ، نام پیشنهادی جدول را به کار ببرید.</li></ol><h2>ابهام‌های خود منبع</h2><p class="notice">فصل ۲ سؤال ۱ بخش b: طول کمیت است؛ متر یکای طول است. عبارت اصلی کتاب حفظ شده و توضیح علمی در بخش تعاملی آمده است.</p><p class="notice">فصل ۲ سؤال ۹: تصویر اصلاح‌شده اکنون با متن هماهنگ است: ۳۶ گرم، ۶۰ و ۷۲ سانتی‌متر مکعب؛ حجم ۱۲ و چگالی ۳.</p><p>چهارگزینه‌ای ۵ فصل ۲: گزینه ب در کتاب ۲٫۳۶ گرم است؛ علامت / به ممیز تبدیل شده تا عدد با تقسیم اشتباه نشود. پاسخ ب، مطابق پاسخنامه، حفظ شده است.</p><p>فصل ۱ سؤال ۷: تعبیر «ضعیف شدن چشم» به «خستگی چشم و کم‌تحرکی در استفاده طولانی و نامناسب» دقیق‌سازی شده است.</p><h2>روند دو تلاش و راهنما</h2><p>پاسخ نادرست کامل اول، راهنمایی ۱ و پاسخ نادرست کاملِ متفاوت دوم، راهنمایی ۲ را نشان می‌دهد؛ سپس دیدن پاسخ فعال می‌شود. پاسخ ناقص یا ارسال دوباره همان انتخاب شمارش نمی‌شود. پاسخ درست اجازه ادامه دارد. وضعیت تلاش‌ها هنگام برگشت حفظ می‌شود و پیش‌نویس قدیمی نمی‌تواند قفل پاسخ را دور بزند.</p><p>در سؤال ۱۲ فصل ۲، شماره ۱ ساعت و شماره ۲ زمان‌سنج است؛ در سؤال ۱۵، شماره‌های ۱، ۲ و ۳ به ترتیب ترازو، استوانه و گروه نیروسنج‌ها هستند.</p><h2>دامنه نسخه بازبینی</h2><p>فقط فصل‌های ۱ و ۲ پیاده‌سازی شده‌اند. سؤال‌های اصلی به ترتیب شماره کتاب، جای خالی‌ها به ترتیب متن، جدول به تفکیک ردیف و سؤال‌های چندبخشی مرحله‌به‌مرحله هستند. گزینه‌های تعاملی سؤال‌های باز برای آموزش ساخته شده‌اند و متن اصلی کتاب بالای آن‌ها حفظ شده است.</p><p>کد: <a href="https://github.com/nranjbar/oloomyar7">مخزن مستقل علوم‌یار هفتم</a>؛ ساخت پیش‌فرض پروژه، اپ هفتم با شناسه نصب جدا است.</p></article></div>'''
page+='''</main><style>.screen-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:18px}.screen-card{margin:0;padding:18px}figure img{cursor:zoom-in}dialog{border:1px solid #ccd6e8;border-radius:16px;width:96vw;height:94vh;padding:16px;background:white}dialog::backdrop{background:#0c153ccc}dialog img{max-width:100%;height:calc(100% - 70px);width:100%;max-height:none;object-fit:contain;border:none}dialog button{display:block;margin-bottom:10px}</style><dialog id="zoom"><button id="closeZoom">بستن تصویر</button><img id="zoomImage" alt=""></dialog><script>const nav=[...document.querySelectorAll('nav button')];const chapter=document.getElementById('chapter'),section=document.getElementById('section');nav.forEach(b=>b.addEventListener('click',()=>{nav.forEach(x=>x.classList.toggle('active',x===b));document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active',p.id===b.dataset.panel));window.scrollTo({top:0,behavior:'smooth'})}));function filter(){let n=0;document.querySelectorAll('.question').forEach(q=>{const yes=(chapter.value==='all'||chapter.value===q.dataset.ch)&&(section.value==='all'||section.value===q.dataset.section);q.hidden=!yes;if(yes)n++});document.getElementById('count').textContent=n.toLocaleString('fa-IR')+' سؤال'}chapter.addEventListener('change',filter);section.addEventListener('change',filter);const zoom=document.getElementById('zoom'),zoomImage=document.getElementById('zoomImage');document.querySelectorAll('figure img').forEach(img=>img.addEventListener('click',e=>{e.preventDefault();zoomImage.src=img.src;zoomImage.alt=img.alt;zoom.showModal()}));document.getElementById('closeZoom').addEventListener('click',()=>zoom.close());</script></body></html>'''
(FOLDER/'index.html').write_text(page)
# Embed each unique asset once rather than duplicating megabytes on every tab.
import re
assets=sorted(set(re.findall(r'<img src="([^"]+)"',page)))
asset_data={name:'data:image/png;base64,'+base64.b64encode((FOLDER/name).read_bytes()).decode() for name in assets}
standalone=re.sub(r'<img src="([^"]+)"',lambda m:'<img data-asset="'+m[1]+'"',page)
loader='<script>const reviewAssets='+json.dumps(asset_data,ensure_ascii=False)+';document.querySelectorAll("img[data-asset]").forEach(img=>{img.src=reviewAssets[img.dataset.asset]});</script>'
standalone=standalone.replace('</body>',loader+'</body>')
(OUT/'OloomYar7_Review.html').write_text(standalone)

shutil.copy2(PROJECT/'qa/grade7_content_validation.json',FOLDER/'content_validation.json')
if (PROJECT/'qa/grade7_android_verification.json').exists():
    shutil.copy2(PROJECT/'qa/grade7_android_verification.json',FOLDER/'android_verification.json')
if (PROJECT/'docs/GRADE7_NAVIGATION_FIX_FA.md').exists():
    shutil.copy2(PROJECT/'docs/GRADE7_NAVIGATION_FIX_FA.md',FOLDER/'GRADE7_NAVIGATION_FIX_FA.md')
with zipfile.ZipFile(OUT/'Grade7_Images_Review.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(FOLDER.rglob('*')):
        if p.is_file():z.write(p,Path('Grade7_Images_Review')/p.relative_to(FOLDER))
print(json.dumps({p.name:p.stat().st_size for p in [OUT/'Grade7_Images_Review.zip',OUT/'OloomYar7_Review.html',csv_path]}))
