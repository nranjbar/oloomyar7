"""Current status of the owner's chapter 10–15 answer-art handoff."""
from pathlib import Path
from PIL import Image
import base64, html, io, json

ROOT=Path(__file__).resolve().parents[1];A=ROOT/'app/src/main/assets';OUT=ROOT/'review'

def write():
    from answer_illustrations_ten_to_fifteen import S, plain
    questions={q['canonicalId']:q for ch in range(10,16)for q in json.loads((A/f'content/chapter{ch}.json').read_text())}
    rows=[];cards=[]
    for cid,item in S.items():
        q=questions[cid];asset=q['answerStudyVisual'];assert (A/'images'/asset).exists()
        row=dict(id=cid,chapter=q['relatedChapter'],number=q['sourceNumber'],kind=q['section'],page=q['sourcePage'],
            priority='بالا',status='دریافت و اضافه شد',filename=cid+'_answer.png',asset=asset,title=item['title'],
            sourceQuestionVisual=q['sourceQuestionVisual'],sourceAnswerVisual=q['answerVisual'],
            questionText=plain(q['nativeQuestion']),answerText=plain(q['nativeAnswer']))
        rows.append(row)
        im=Image.open(A/'images'/asset).convert('RGB');im.thumbnail((440,330));buf=io.BytesIO();im.save(buf,'WEBP',quality=86,method=4)
        url='data:image/webp;base64,'+base64.b64encode(buf.getvalue()).decode()
        label=('تست 'if q['section']=='چهارگزینه‌ای'else'سؤال ')+str(q['sourceNumber'])
        cards.append(f'<article><p class="tag">فصل {q["relatedChapter"]} · {label} · دریافت و اضافه شد</p><h2>{html.escape(item["title"])}</h2><code>{row["filename"]}</code><img src="{url}" alt="تصویر پاسخ دریافت‌شده"><details><summary>پاسخ کامل متنی</summary><pre>{html.escape(row["answerText"])}</pre></details></article>')
    received10=[dict(id=q['canonicalId'],chapter=10,number=q['sourceNumber'],filename=q['canonicalId']+'_answer.png',status='دریافت و اضافه شد')for q in questions.values()if q['relatedChapter']==10 and q.get('answerStudyVisual')]
    assert len(rows)==26 and len(received10)==12
    data=dict(date='2026-10-22',edition=1405,chapters=list(range(10,16)),questionsReviewed=len(questions),
        newHighPriorityFiles=0,newlyReceivedChapter11To15=26,alreadySuppliedChapter10=12,
        policy='تمام تصاویر اولویت بالای این فهرست دریافت و اضافه شده‌اند. متن پاسخ‌ها باقی است؛ ساده‌سازی‌های مناسب پایهٔ هفتم حفظ می‌شوند. پاسخ درست و دیدن پاسخ یک متن و تصویر کامل دارند.',
        correctedExisting='g7_ch07_q14_answer.png جایگزین شد.',received=received10+rows,rows=rows)
    (OUT/'answer-illustrations-ch10-15.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    md=['# وضعیت تصاویر پاسخ — فصل‌های ۱۰ تا ۱۵','',data['policy'],'','۲۶ تصویر فصل‌های ۱۱ تا ۱۵ اضافه شد. ۱۲ تصویر فصل ۱۰ نیز از قبل اضافه شده است. هیچ فایل با اولویت بالای این فهرست در انتظار دریافت نیست. تصویر اصلاح‌شدهٔ فصل ۷ سؤال ۱۴ جایگزین شد.','','| فصل | سؤال | فایل | وضعیت |','|---|---|---|---|']
    for row in rows:
        label=('تست 'if row['kind']=='چهارگزینه‌ای'else'سؤال ')+str(row['number'])
        md.append(f'| {row["chapter"]} | {label} | `{row["filename"]}` | دریافت و اضافه شد |')
    (OUT/'Grade7_HighPriority_Images_Ch10-15.md').write_text('\n'.join(md)+'\n')
    doc='''<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>وضعیت تصاویر پاسخ علوم هفتم</title><style>body{margin:0;background:#f3f6fa;color:#142d42;font-family:Tahoma,Arial,sans-serif;line-height:1.95}main{max-width:920px;margin:auto;padding:24px}header,article{background:white;padding:24px;border:1px solid #dbe4ec;border-radius:18px;margin:18px 0}h1{font-size:28px}h2{font-size:21px}.tag{color:#146570}code{direction:ltr;unicode-bidi:isolate;display:inline-block;overflow-wrap:anywhere}img{display:block;max-width:100%;height:auto;max-height:330px;margin:16px auto}pre{white-space:pre-wrap;font-family:inherit}summary{cursor:pointer;color:#176874}article{break-inside:avoid}</style><main><header><p class="tag">علوم‌یار هفتم · کتاب کار ۱۴۰۵ · ۲۲ اکتبر ۲۰۲۶</p><h1>تصاویر پاسخ آماده و اضافه شدند</h1><p>هر ۲۶ تصویر درخواستی فصل‌های ۱۱ تا ۱۵، کنار متن کامل پاسخ اضافه شده است. ۱۲ تصویر اولویت بالای فصل ۱۰ از قبل موجود است. <strong>فایل با اولویت بالای این فهرست باقی نمانده است.</strong></p><p>تصویر اصلاح‌شدهٔ فصل ۷ سؤال ۱۴ جایگزین شد. بیان سادهٔ مناسب دانش‌آموز هفتم حفظ شده است.</p><p>تصاویر بدون کاهش ابعاد بهینه شده‌اند. پس از پاسخ درست، همان متن و تصویر کاملِ «دیدن پاسخ» به‌صورت خودکار نمایش داده می‌شود.</p></header>'''+''.join(cards)+'</main></html>'
    (OUT/'Grade7_HighPriority_Images_Ch10-15.html').write_text(doc)
    print(json.dumps(dict(newlyReceived=26,chapter10PreviouslyReceived=12,remainingHighPriority=0)))

if __name__=='__main__':write()
