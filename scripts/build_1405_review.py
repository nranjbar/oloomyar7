"""Generate the current author review index without obsolete PNG asset links."""
import html,json,io
import re
import fitz
from PIL import Image
from pathlib import Path
from index_full_workbook import ROOT,ASSETS,dump

def native_blocks(blocks, cid):
    """Review-only figure derivatives; none of this folder is packaged in Android."""
    result=[]
    for index, block in enumerate(blocks):
        value=dict(block)
        if block['type']=='figure':
            asset=block['asset']
            if asset.startswith(('pdf:','crop:')):
                directory=ROOT/'review/native-figures';directory.mkdir(exist_ok=True)
                filename=f'{cid}-{index:02}.webp';path=directory/filename
                if asset.startswith('pdf:'):
                    file,coordinates=asset[4:].split('#'); page,*bbox=map(float,coordinates.split(','))
                    with fitz.open(ASSETS/'source'/file)as pdf:
                        pix=pdf[int(page)-1].get_pixmap(matrix=fitz.Matrix(2.2,2.2),clip=fitz.Rect(bbox),alpha=False)
                        image=Image.frombytes('RGB',(pix.width,pix.height),pix.samples)
                else:
                    file,coordinates=asset[5:].split('#')
                    image=Image.open(ASSETS/'images'/file).crop(tuple(map(int,coordinates.split(','))))
                buffer=io.BytesIO()
                image.save(buffer,'WEBP',lossless=True,method=6)
                payload=buffer.getvalue();assert payload,asset
                path.write_bytes(payload)
                with Image.open(path)as check:check.verify()
                value['reviewSrc']='native-figures/'+filename
            else:value['reviewSrc']='../app/src/main/assets/images/'+asset
        result.append(value)
    return result

def main():
    source={r['id']:r for r in json.loads((ROOT/'qa/source/all-chapters-index.json').read_text())}
    chapters=json.loads((ASSETS/'content/catalog.json').read_text());data=[]
    for c in range(1,16):
        for q in json.loads((ASSETS/f'content/chapter{c}.json').read_text()):
            r=source[q['canonicalId']]
            data.append(dict(id=q['canonicalId'],chapter=c,number=q['sourceNumber'],section=q['section'],title=q['title'],
                page=q['sourcePage'],answerPage=r['answerSegments'][0]['page'],steps=q['steps'],note=q.get('sourceReviewNote'),
                reading=q.get('readablePrompt')or next((s['clozeTemplate']for s in q['steps']if s.get('clozeTemplate')),None),
                nativeQuestion=native_blocks(q.get('nativeQuestion',[]),q['canonicalId']),
                nativeAnswer=native_blocks(q.get('nativeAnswer',[]),q['canonicalId']+'-answer'),
                figures=[x for x in [q.get('visual')]+[v['asset']for v in q.get('visuals',[])]if x and not x.startswith('pdf:')]))
    css='''body{margin:0;background:#f2f7fa;color:#153743;font:16px/1.9 Tahoma,Arial}main{max-width:1000px;margin:auto;padding:24px}header{background:#0b6573;color:white;border-radius:24px;padding:24px}section,article{background:white;border:1px solid #d9e6e8;border-radius:18px;padding:20px;margin:18px 0}h2,h3{color:#087987}a{color:#126778}header a{color:#fff}select{padding:10px;font:inherit;border-radius:10px}nav{position:sticky;top:0;background:#f2f7fa;padding:12px}pre{white-space:pre-wrap;font:inherit}img{max-width:100%;max-height:280px}details{padding:12px;background:#f8fafb;margin:8px 0}.note{background:#fff0db;padding:14px;border-radius:12px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #d9e6e8;padding:10px;text-align:right}.pill{display:inline-block;background:#ffffff25;border-radius:20px;padding:5px 12px;margin:4px}'''
    page='''<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>بازبینی ۱۵ فصل علوم‌یار هفتم ۱۴۰۵</title><style>'''+css+'''</style><main><header><h1>علوم‌یار هفتم؛ بازبینی ۱۴۰۵</h1><p>۳۱۷ سؤال، ۳۳۴ بخش تمرینی و ۶۶۸ راهنمای مرحله‌ای در هر ۱۵ فصل.</p><span class="pill">سؤال و پاسخ اصلی بی‌ویرایش</span><span class="pill">بدون تصویر گم‌شده</span><p>این صفحه برای بازبینی مؤلف است. پیوند پاسخنامه در این گزارش آزاد است؛ قفل آموزشی و بررسی دو تلاش در اپ دانش‌آموز اعمال می‌شود.</p><a href="../docs/GRADE9_TO_GRADE7_1405_CHECKLIST_FA.md">چک‌لیست انتقال نهم</a> · <a href="../docs/ANSWER_IMAGE_POLICY_1405_FA.md">فهرست تصویر و پرامپت‌ها</a></header>
<section><h2>کمترین نیاز به تصویر تازه</h2><table><tr><th>سؤال</th><th>نیاز</th><th>وضعیت</th></tr><tr><td>فصل ۵، سؤال ۲</td><td>جدول پاسخ</td><td>در پاسخنامه موجود؛ تولید تازه لازم نیست</td></tr><tr><td>فصل ۱۰، سؤال ۱۳</td><td>شکل جریان همرفتی</td><td>در پاسخنامه موجود؛ تولید تازه لازم نیست</td></tr><tr><td>فصل ۵، سؤال ۱۱</td><td>نمودار ستونی کامل</td><td>فقط مکمل اختیاری؛ وارد پاسخ مؤلف نشده است</td></tr></table><p>فرمول‌ها، کسرها و متن ترکیبی از قالب اصلی PDF نمایش داده می‌شوند؛ عدد یا حرف انگلیسی به تنهایی موجب تولید تصویر جدید نیست.</p></section>
<section class="note"><h2>نیازمند تأیید مؤلف</h2><p>چهارگزینه‌ای ۳ فصل ۱۳: کلید چاپی «ج» با نقش دندان‌ها در گزینه‌ها سازگار نیست. متن و کلید تغییر نکرده‌اند؛ یادداشت جداگانهٔ بررسی در برنامه وجود دارد.</p></section>
<nav><label>فصل: <select id="chapter"><option value="0">همهٔ فصل‌ها</option>'''+''.join(f'<option value="{c["id"]}">{c["id"]} ـ {html.escape(c["title"])}</option>'for c in chapters)+'''</select></label> <span id="count"></span></nav><div id="questions"></div>
<script>const data='''+json.dumps(data,ensure_ascii=False).replace('</',r'<\/')+r''';const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function render(){const c=Number(document.querySelector('#chapter').value);const rows=data.filter(q=>!c||q.chapter===c);document.querySelector('#count').textContent=rows.length+' سؤال';document.querySelector('#questions').innerHTML=rows.map(q=>`<article id="${q.id}"><h3>فصل ${q.chapter} | ${esc(q.section)} | سؤال ${q.number}</h3><p>${esc(q.title)}</p>${q.note?`<p class="note">${esc(q.note)}</p>`:''}${q.reading?`<pre>${esc(q.reading.replace(/\{\{\d+}}/g,'................'))}</pre>`:'<p>صورت اصلی شامل چیدمان چاپی است؛ PDF مرجع را باز کنید.</p>'}<p><a href="../app/src/main/assets/source/workbook.pdf#page=${q.page}">صورت کامل و شکل‌ها؛ صفحهٔ ${q.page} کتاب</a> · <a href="../app/src/main/assets/source/answers${String(q.chapter).padStart(2,'0')}.pdf#page=${q.answerPage}">پاسخ اصلی؛ صفحهٔ ${q.answerPage} پاسخنامهٔ فصل</a></p>${q.figures.map(f=>`<img src="../app/src/main/assets/images/${esc(f)}" alt="شکل مورد استفاده در برنامه">`).join('')}<details><summary>راهنماهای مرحله‌ای ${q.steps.length} بخش</summary>${q.steps.map((s,i)=>`<h4>بخش ${i+1}</h4><p>${esc(s.prompt)}</p><p>راهنمای ۱: ${esc(s.hint1)}</p><p>راهنمای ۲: ${esc(s.hint2)}</p>`).join('')}</details></article>`).join('')}
document.querySelector('#chapter').addEventListener('change',render);render();</script></main></html>'''
    native_js=r'''
function prose(t){return esc(t).replace(/\{\{\d+(?::\d+)?}}/g,'<span class="source-blank">................</span>')}
function native(blocks,q){return blocks.map(b=>{
 if(b.type==='text'||b.type==='cloze')return `<pre>${prose(b.text)}</pre>`;
 if(b.type==='figure')return `<figure><img src="${esc(b.reviewSrc)}" alt="شکل مستقل سؤال ${q.number}"><figcaption>${esc(b.text||'')}</figcaption></figure>`;
 if(b.type==='table')return `<div class="table-scroll"><table><thead><tr>${b.headers.map(t=>`<th>${esc(t)}</th>`).join('')}</tr></thead><tbody>${b.rows.map(r=>`<tr>${r.map(t=>`<td>${prose(t)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
 if(b.type==='math')return `<div class="equation" dir="ltr">${b.parts.map(p=>p.numerator?`<span class="fraction"><span>${esc(p.numerator)}</span><span>${esc(p.denominator)}</span></span>`:`<bdi dir="ltr">${esc(p.text).split(/([<>+→=])/).map(t=>/[آ-ی]/.test(t)?`<bdi dir="rtl">${t}</bdi>`:t).join('')}</bdi>`).join(' ')}</div>`;
 if(b.type==='reaction')return `<pre dir="ltr">${prose(b.text)}</pre>`;
 if(b.type==='barChart')return '<p class="note">در اپ، دانش‌آموز مقیاس و ارتفاع پنج ستون را انتخاب می‌کند؛ نمودار پاسخ از پیش نمایش داده نمی‌شود.</p>';
 if(b.type==='input')return `<details><summary>گزینه‌های تمرین این قسمت</summary>${(q.steps[b.step||0].options||[]).map(o=>`<p>${esc(o.text)}</p>`).join('')}</details>`;
 return '';
}).join('')}
'''
    page=page.replace('function render(){',native_js+'\nfunction render(){')
    page=page.replace('${q.reading?', '${q.nativeQuestion.length?native(q.nativeQuestion,q):q.reading?')
    page=page.replace('${q.figures.map(', '${(q.nativeQuestion.length?[]:q.figures).map(')
    page=page.replace('<details><summary>راهنماهای مرحله‌ای', '${q.nativeAnswer.length?`<details><summary>پاسخ چاپی، به صورت متن و جدول و فرمول</summary>${native(q.nativeAnswer,q)}</details>`:\'\'}<details><summary>راهنماهای مرحله‌ای')
    page=page.replace('</style>','''@font-face{font-family:Noto;src:url('../app/src/main/res/font/noto_arabic_regular.ttf')}body{font-family:Noto,Tahoma,Arial;color:#11215e;background:#f6fbfd}pre{unicode-bidi:plaintext}.table-scroll,.equation{overflow-x:auto}.equation{display:flex;gap:12px;align-items:center;padding:12px}.fraction{display:inline-flex;flex-direction:column;text-align:center;min-width:45px}.fraction>span:first-child{border-bottom:1px solid #11215e}.source-blank{display:inline-block;background:#ddf6f3;border:1px solid #087a76;border-radius:10px;padding:0 8px}td,th{min-width:110px}figure{margin:16px 0}figcaption{color:#52617a;font-size:14px}</style>''')
    page=page.replace('فرمول‌ها، کسرها و متن ترکیبی از قالب اصلی PDF نمایش داده می‌شوند؛','در پنج فصل اول، هر ۱۰۴ سؤال و پاسخ به صورت متن بومی، جدول و کسر واقعی نمایش داده می‌شوند؛ شکل‌ها مستقل‌اند. در فصل‌های بعد قالب اصلی PDF حفظ شده است؛')
    page=page.replace('در پاسخنامه موجود؛ تولید تازه لازم نیست</td></tr><tr><td>فصل ۱۰','در پاسخنامه موجود و در اپ به جدول بومی تبدیل شده؛ تولید تازه لازم نیست</td></tr><tr><td>فصل ۱۰')
    (ROOT/'review/index.html').write_text(page)
    manifest=[]
    for entry in json.loads((ROOT/'qa/source/lossless-scientific-assets.json').read_text()):
        manifest.append(dict(file=entry['asset'],asset='../app/src/main/assets/images/'+entry['asset'],
            original=entry.get('originalRepositoryPath'),status='pixel_identical_lossless_webp',sha256=entry['webpSha256'],originalSha256=entry['originalSha256']))
    dump(ROOT/'docs/grade7_image_manifest.json',manifest)
    (ROOT/'review/README_FA.md').write_text('''# بازبینی هر ۱۵ فصل هفتم ۱۴۰۵

`index.html` فهرست ۳۱۷ سؤال و راهنماهای ۳۳۴ بخش را بدون نیاز به دریافت JSON جداگانه نمایش می‌دهد. پنج فصل اول ۱۰۴ سؤال و پاسخ بومی با شکل‌های مستقل، جدول و فرمول دارند. پیوندهای PDF برای تطبیق با اصل منبع محفوظ‌اند. شکل‌های پوشهٔ native-figures فقط برای این گزارش‌اند و وارد APK نمی‌شوند. برای تعامل کامل و قفل پاسخ از اپ استفاده کنید؛ این صفحه screenshot اندروید نیست.

دو پاسخ دارای تصویر ضروریِ موجود و یک نمودار تکمیلی اختیاری در `docs/ANSWER_IMAGE_POLICY_1405_FA.md` فهرست شده‌اند. تصاویر ۱۲ و ۱۵ فصل ۳ موجودند؛ چهار پیشنهاد دیگرِ کیفیت سؤال اختیاری‌اند. هیچ تصویر تازهٔ اجباری گم نشده است.

این صفحه گزارش مؤلف است و پاسخنامه را با پیوند مستقیم در دسترس می‌گذارد. در اپ، همان پاسخ مرجع پس از حل درست یا دو تلاش کامل نادرست متفاوت و راهنماها، فقط با درخواست دانش‌آموز باز می‌شود. اسناد و عکس‌های نسخه‌های قدیمی شاهد اجرای نسخهٔ ۰٫۶ نیستند.

بازسازی فهرست: `python3 scripts/build_1405_review.py`. گزارش PDF با `python3 scripts/build_grade7_1405_report.py --output PATH.pdf` ساخته می‌شود. اصل تصاویر علمی با نام ارسالی در `qa/source/original-scientific-images` و نسخهٔ بدون تغییر پیکسل برای برنامه در assets موجود است.
''')
    print('Current author review generated:',len(data),'questions')

if __name__=='__main__':main()
