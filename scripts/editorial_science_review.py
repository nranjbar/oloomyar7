"""Owner-authorized 0.10 editorial/science review; immutable PDF fields stay intact.

Run after the source transcription and supplied-art adapters. All presentation
revisions are compared to the tested 0.9 source and listed with before/after
hashes. Scientific changes to checked controls invalidate only that question's
old authorization, never translate an old incorrect claim into a correct one.
"""
from pathlib import Path
import copy, hashlib, json, re, subprocess

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/content'
BASELINE='09441896d203babc5649e7275337f0a883ad5ee4'
sha=lambda v:hashlib.sha256(json.dumps(v,ensure_ascii=False,sort_keys=True).encode()).hexdigest()

def write():
    chapters={ch:json.loads((ASSETS/f'chapter{ch}.json').read_text()) for ch in range(1,16)}
    qs={q['canonicalId']:q for values in chapters.values() for q in values}
    reasons={}
    def q(ch,n):return qs[f'g7_ch{ch:02d}_q{n:02d}']
    def mq(ch,n):return qs[f'g7_ch{ch:02d}_mcq{n:02d}']
    def record(question,reason):reasons.setdefault(question['canonicalId'],[]).append(reason)
    def replace(question,field,old,new,reason):
        def walk(value):
            if isinstance(value,str):return value.replace(old,new)
            if isinstance(value,list):return [walk(v) for v in value]
            if isinstance(value,dict):return {k:walk(v) for k,v in value.items()}
            return value
        encoded=json.dumps(question[field],ensure_ascii=False)
        assert json.dumps(old,ensure_ascii=False)[1:-1] in encoded or json.dumps(new,ensure_ascii=False)[1:-1] in encoded,(question['canonicalId'],old)
        question[field]=walk(question[field]);record(question,reason)
    def note(question,text):question['answerReviewNote']=text;record(question,'Separate gated scientific clarification')
    def revised(question):
        question['practiceRevision']=1;question['practiceUsesPrintedKey']=False
        if not question.get('sourceReviewNote'):
            question['sourceReviewNote']='بعضی عبارت‌های این سؤال یا پاسخنامه نیاز به دقت علمی دارند. تمرین با بیان دقیق‌تر بررسی می‌شود و توضیح تکمیلی، جدا از متن پاسخنامه، پس از باز کردن پاسخ می‌آید.'
        for s in question['steps']:s.pop('choiceAliases',None)
        record(question,'Fresh check required; old scientific selections/hints/reveal are not migrated')
    def right(question,row,text,step=0):
        s=question['steps'][step];p=s['pairs'][row];old=p['right'];p['right']=text
        if p.get('choices'):p['choices']=[text if c==old else c for c in p['choices']]
        if s.get('choices'):s['choices']=[text if c==old else c for c in s['choices']]
    def hints(question,first=None,second=None,step=0):
        if first:question['steps'][step]['hint1']=first
        if second:question['steps'][step]['hint2']=second

    # Orthography fixes are explicit and limited to the native presentation.
    for question,field,old,new in [
        (q(2,3),'nativeAnswer','لب تاب','لپ‌تاپ'),
        (q(2,11),'nativeQuestion','کمنر','کمتر'),
        (q(3,18),'nativeAnswer','بیشر','بیشتر'),
        (mq(3,7),'nativeQuestion','مقدر','مقدار'),
        (q(4,1),'nativeQuestion','رها می کنیم','رها کنیم'),
        (q(4,7),'nativeAnswer','دویاره','دوباره'),
        (q(4,14),'nativeQuestion','ماننذ','مانند'),
        (q(5,2),'nativeQuestion','چدول','جدول'),
        (q(5,2),'nativeAnswer','واکنش یا اکسیژن','واکنش با اکسیژن'),
        (q(5,3),'nativeAnswer','قاشفک','قاشقک'),
        (q(5,8),'nativeAnswer','استفاده می گنند','استفاده می‌کنند'),
        (q(5,15),'nativeQuestion','با توحه','با توجه'),
        (q(15,1),'nativeQuestion','لوله ادرای','لولهٔ ادراری'),
        (q(15,8),'nativeQuestion','تکمیل کنید..','تکمیل کنید.'),
        (mq(15,3),'nativeQuestion','خارج می شود ؟','خارج شود؟'),
    ]:replace(question,field,old,new,'Native spelling/grammar correction; original source retained')
    # These slash numerals were individually checked against their calculations:
    # every occurrence in native blocks is a decimal, true fractions use MathPart.
    decimal=re.compile(r'([0-9۰-۹]+)/([0-9۰-۹]+)')
    def decimals(value):
        if isinstance(value,str):return decimal.sub(lambda m:m[1]+('٫' if any(c in '۰۱۲۳۴۵۶۷۸۹' for c in m[0]) else '.')+m[2],value)
        if isinstance(value,list):return [decimals(v) for v in value]
        if isinstance(value,dict):return {k:decimals(v) for k,v in value.items()}
        return value
    for question in qs.values():
        for field in ['nativeQuestion','nativeAnswer']:
            new=decimals(question[field])
            if new!=question[field]:question[field]=new;record(question,'Unambiguous decimal separator; fractions retained as numerator/denominator')
        for s in question['steps']:
            if s['kind']=='single' and question['canonicalId']!='g7_ch13_mcq03':
                s['prompt']='گزینهٔ درست را با توجه به سؤال انتخاب کن.' if question['section']=='چهارگزینه‌ای' else s['prompt']
    # Instrument numbers follow the delivered corrected question figures.
    replace(q(2,1),'nativeQuestion','با یکای {{1}} اندازه می گیریم','با کمیت {{1}} بیان می‌کنیم','Length is a quantity, not a unit')
    replace(q(2,12),'nativeAnswer','الف- زمان سنج ( کرنومتر ) – ساعت','الف- ۱: ساعت؛ ۲: زمان‌سنج (کرنومتر)','Explicit labels match the corrected figure')
    q(2,15)['nativeAnswer']=[dict(type='text',text='۱- ترازو: اندازه‌گیری جرم\n۲- استوانهٔ مدرج: اندازه‌گیری حجم مایع\n۳- نیروسنج‌ها: اندازه‌گیری نیرو، مانند وزن')]
    record(q(2,15),'Same complete source facts reordered by authoritative figure numbers')
    note(q(2,9),'حجم آب پیش از قرار دادن سنگ ۶۰ و حجم آب و سنگ پس از آن ۷۲ سانتی‌متر مکعب است؛ اختلاف آن‌ها، یعنی ۱۲ سانتی‌متر مکعب، حجم خودِ سنگ است.')
    # Eye strain is not synonymous with permanent loss of vision or laziness.
    q(1,7)['steps'][1]['options'][1]['text']='خستگی چشم و کاهش تحرک در استفادهٔ طولانی بدون استراحت'
    revised(q(1,7));note(q(1,7),'استفادهٔ طولانی و بدون وقفه می‌تواند به خستگی یا خشکی چشم و کاهش تحرک بینجامد. خستگی چشم را نباید با آسیب دائمی یا ضعیف‌شدن قطعی بینایی یکی دانست؛ «تنبلی» نیز پیامد حتمی استفاده از رایانه نیست.')
    # Density/floatation does not define the metal/nonmetal distinction.
    s=q(3,7)['steps'][0]
    for option,text in zip(s['options'][:4],[
        'بیشتر نافلزها رسانای ضعیف جریان الکتریکی‌اند.',
        'بیشتر نافلزهای جامد جلای فلزی ندارند.',
        'نافلزهای جامد معمولاً شکننده‌اند و چکش‌خوار نیستند.',
        'بیشتر نافلزها رسانای ضعیف گرما هستند.'
    ]):option['text']=text
    for option,text in zip(s['options'][4:],[
        'همهٔ نافلزها رسانای خوب جریان الکتریکی‌اند.',
        'همهٔ نافلزهای جامد جلای فلزی دارند.',
        'نافلزهای جامد همیشه چکش‌خوارند و نمی‌شکنند.',
        'نافلز بودن به‌تنهایی، شناور ماندن روی آب را تضمین می‌کند.'
    ]):option['text']=text
    s['prompt']='چهار ویژگی معمول نافلزها را با توجه به استثناها انتخاب کن.'
    hints(q(3,7),second='ویژگی‌های معمول رسانایی و رفتار جامد در برابر ضربه را بسنج. گرافیت برای رسانایی برق و الماس برای رسانایی گرما استثناهای مهم‌اند؛ شناوری به چگالی وابسته است.')
    revised(q(3,7));note(q(3,7),'روی آب ماندن ویژگی عمومی نافلزها نیست؛ شناوری به چگالی نمونه و مایع بستگی دارد. ویژگی‌های رسانایی و جلای نافلزها هم استثنا دارند؛ مثلاً گرافیت برق و الماس گرما را خوب منتقل می‌کنند.')
    replace(q(3,8),'nativeQuestion','h- اغلب روی آب شناور می مانند .','h- اغلب رسانای ضعیف گرما هستند .','Replace invalid nonmetal floatation generalization')
    replace(q(3,8),'steps','h – اغلب روی آب شناور می مانند.','h – اغلب رسانای ضعیف گرما هستند.','Keep cloze adaptation consistent')
    hints(q(3,8),second='رسانایی، جلای سطح و رفتار در برابر ضربه را برای نمونه‌های معمول این دو گروه مقایسه کن. برای فرورفتن در آب، چگالی نمونه را نیز باید در نظر گرفت.')
    revised(q(3,8));note(q(3,8),'این دسته‌بندی دربارهٔ ویژگی‌های معمول نمونه‌هاست و استثنا دارد. بند h در نمایش تمرینی به «رسانایی ضعیف گرما» اصلاح شده است؛ شناوری به چگالی وابسته است، نه صرفاً فلز یا نافلز بودن.')
    replace(q(3,1),'nativeQuestion','e- ماده ای که از دو یا چند نوع اتم متفاوت تشکیل شده است','e- مادهٔ خالصی که از پیوند شیمیایی اتم‌های دو یا چند عنصر متفاوت تشکیل شده است','Distinguish a compound from a mixture')
    right(q(3,14),1,'مادهٔ خالص حاصل از پیوند شیمیایی اتم‌های دو یا چند عنصر متفاوت')
    hints(q(3,14),second='نوع عنصرها و پیوند شیمیایی را با هم بررسی کن. کنار هم بودن دو ماده بدون پیوند شیمیایی، به‌تنهایی ترکیب شیمیایی نمی‌سازد.')
    revised(q(3,14));note(q(3,14),'ترکیب شیمیایی، ماده‌ای خالص از دو یا چند عنصر با نسبت معین و پیوند شیمیایی است. مخلوط چند ماده نیز چند نوع اتم دارد؛ پس وجود اتم‌های متفاوت به‌تنهایی تعریف کاملی برای ترکیب نیست.')
    s=q(3,16)['steps'][0];s['options'][1]['text']='هسته پروتون دارد و بیشتر هسته‌ها نوترون هم دارند.'
    s['options'][2]['text']='در اتم خنثی، تعداد الکترون‌ها و پروتون‌ها برابر است.'
    revised(q(3,16));note(q(3,16),'هیدروژنِ معمولی (هیدروژن-۱) در هستهٔ خود یک پروتون و هیچ نوترونی ندارد. برابری تعداد پروتون و الکترون مربوط به اتم خنثی است؛ مدل کروی این فصل نیز ساده‌سازی آموزشی است.')
    mq(3,5)['steps'][0]['prompt']='با توجه به مدل‌های اتمیِ نشان‌داده‌شده در همین شکل، گزینهٔ درست را انتخاب کن.'
    revised(mq(3,5));note(mq(3,5),'کلید «همهٔ موارد» را در چارچوب مدل‌های همین شکل بخوانید. هستهٔ هیدروژن-۱ نوترون ندارد؛ عبارتِ دارای پروتون و نوترون را نمی‌توان بدون استثنا به همهٔ اتم‌ها تعمیم داد.')
    note(q(3,15),'عددهای نوترون در این مدل مربوط به کربن-۱۲ و اکسیژن-۱۶ هستند. برای اتم‌های خنثیِ شکل، تعداد الکترون و پروتون برابر است؛ ایزوتوپ‌های دیگر می‌توانند تعداد نوترون متفاوتی داشته باشند.')
    note(q(3,19),'منظور، مقایسهٔ تراکم‌پذیری ماده‌هاست. هوای میان دانه‌های شن ممکن است با فشار خارج شود و دانه‌ها جابه‌جا شوند؛ این را با فشرده‌شدن خودِ مادهٔ جامد یکی ندانید.')
    note(q(3,21),'ترتیب نشان‌داده‌شده فقط نتیجهٔ مقایسهٔ همین چهار نمونه در شرایط آزمایش کتاب است؛ از این نمودار نمی‌توان دربارهٔ همهٔ فلزها و نافلزها حکم کلی داد.')
    note(q(3,24),'شکل‌ها مدلِ کیفیِ آرایش ذره‌ها هستند و مقیاس دقیق چگالی ندارند. دربارهٔ آب، یخ از آب مایع کم‌چگال‌تر است؛ پاسخ این سؤال با شناسایی حالت گاز از روی شکل تعیین می‌شود.')
    replace(mq(3,2),'nativeQuestion','همه مواد موجود در عالم هستی تقریباً از چند نوع اتم ( عنصر ) ساخته شده اند ؟','در تقریب این فصل، مواد طبیعی از حدود چند نوع اتم (عنصر طبیعی) ساخته شده‌اند؟','Scope the approximate natural-element count')
    revised(mq(3,2))
    note(mq(3,2),'عدد این تست، تقریب آموزشیِ شمار عنصرهای طبیعی در این فصل است؛ تعداد همهٔ عنصرهای شناخته‌شده ۱۱۸ است. اصل گزینه‌ها و حرف کلید منبع حفظ شده‌اند.')
    # Alloys may include a nonmetal. Impact tools are not a sound cast-iron example.
    right(q(4,16),0,'ماده‌ای با ویژگی فلزی، شامل یک فلز و یک یا چند عنصر دیگر')
    right(q(4,16),1,'ذوب و مخلوط کردن فلز با جزءهای دیگر، در روش مطرح‌شدهٔ کتاب')
    right(q(4,16),3,'یک نوع فولاد زنگ‌نزن: آهن و کروم، همراه نیکل؛ مقاوم‌تر در برابر زنگ‌زدگی')
    revised(q(4,16));note(q(4,16),'آلیاژ الزاماً فقط از فلزها ساخته نمی‌شود؛ کربنِ نافلز از اجزای فولاد و چدن است. همچنین همهٔ انواع فولاد زنگ‌نزن نیکل ندارند؛ مثال کتاب مربوط به یک نوع رایج آن است.')
    right(q(4,17),5,'ساخت ظرف چدنی و بدنهٔ برخی ماشین‌آلات')
    s=q(4,17)['steps'][0]
    s['acceptedPairOrders']=[[('ساخت ظرف چدنی و بدنهٔ برخی ماشین‌آلات' if x=='ساخت کلنگ و تیشه و تبر' else x) for x in row] for row in s.get('acceptedPairOrders',[])]
    hints(q(4,17),second='اجزای یک آلیاژ می‌توانند فلزی یا نافلزی باشند. کاربردهای چدنِ ریختگی را با ابزارهای ضربه‌ایِ فولادی و وسایل مقاوم در برابر زنگ‌زدگی مقایسه کن.')
    revised(q(4,17));note(q(4,17),'نمونهٔ کاربرد چدن در کلید نیاز به اصلاح دارد: تیغهٔ تبر و ابزار ضربه‌ای معمولاً از فولاد مناسب ساخته می‌شود. برای چدن، ظرف چدنی و بدنهٔ برخی ماشین‌آلات مثال مناسب‌تری است. ترتیب دو جزء هر آلیاژ در تمرین آزاد است.')
    # Independent solution preparation has two equally valid orders.
    q(5,3)['steps'][0]['acceptedOrders']=[[1,0,2,3]]
    note(q(5,3),'در این آزمایش، رسوب قهوه‌ای مایل به قرمزِ هیدروکسید آهن(III)، نشانهٔ یون آهن(III) است. آماده‌کردن دو محلول می‌تواند با هر ترتیبی انجام شود؛ تصویر، مدل آزمایش کلاسی با نظارت دبیر است.')
    note(q(5,6),'پس از حرارت‌دادن، نمونه باید سرد شود و سپس محصول بررسی شود؛ «۳۰ ثانیه» را زمان تضمینی سردشدن ندانید. جذب آهن‌ربا در چارچوب مواد و مراحل همین آزمایش تفسیر می‌شود.')
    q(5,6)['steps'][0]['items'][2]='پس از صبر و سردشدن نمونه، پودر کردن بخش سوخته و ریختن آن روی شیشهٔ ساعتی'
    revised(q(5,6))
    note(q(5,9),'بتن معمولی از سیمان، آب و سنگ‌دانه‌ها، از جمله ماسه و معمولاً شن، ساخته می‌شود. عبارت کوتاه کتاب نام شن را جداگانه نیاورده است.')
    right(q(5,9),0,'سیمان، آب و سنگ‌دانه‌ها، شامل ماسه و معمولاً شن')
    revised(q(5,9))
    right(q(5,10),1,'در معرف عمومی pH، رنگ متناظر با عدد بیشتر از ۷، نشانهٔ بازی بودن است')
    revised(q(5,10));note(q(5,10),'رنگ را باید با جدول همان کاغذ pH مقایسه کرد؛ برای محلول بازی، pH بیشتر از ۷ است. آبی‌شدنِ ذکرشده در پاسخنامه مربوط به رنگ معرف به‌کاررفته است و قاعدهٔ همهٔ شناساگرها نیست.')
    note(q(5,15),'این نمودار یک پیش‌بینی آموزشی بر پایهٔ فرض‌های کتاب است؛ تاریخ قطعی پایان منابع یا آمار به‌روز جهان نیست. زمان باقی‌مانده به ذخایر شناخته‌شده، فناوری، بازیافت و مقدار مصرف وابسته است.')
    hints(q(5,12),first='پیامد اقتصادی معدن را از پیامدهای محیطی و اجتماعی آن جدا بررسی کن.')
    record(q(5,12),'Use the mine in the question context, rather than an unrelated factory, in hint 1')
    # Water coverage and precipitation mechanisms.
    replace(q(6,1),'nativeQuestion','زیرا تقریبا','زیرا حدود ۷۱ درصد سطح زمین، نزدیک به','Correct surface-water coverage')
    replace(q(6,1),'nativeQuestion','سطح زمین ( بیش از ۷۵ درصد ) را','سطح زمین را','Remove incorrect greater-than-75-percent claim')
    q(6,1)['nativeQuestion']=q(6,1)['nativeQuestion'][:1]+[dict(type='cloze',step=0,text='f- سیارهٔ زمین از فضا آبی دیده می‌شود، زیرا حدود ۷۱ درصد (نزدیک به سه‌چهارم) سطح زمین را {{6}} می‌پوشانند.')]
    note(q(6,1),'آب حدود ۷۱ درصد سطح زمین را می‌پوشاند؛ عبارت «بیش از ۷۵ درصد» متن چاپی دقیق نیست. سه‌چهارم فقط یک تقریبِ ساده است.')
    right(q(6,7),1,'رسیدن قطره‌های مایع آب به زمین؛ این قطره‌ها می‌توانند از ذوب بلورهای یخ نیز پدید آیند')
    right(q(6,7),2,'رشد دانه‌های یخ در ابرِ توفانی، با جریان صعودی و قطره‌های آبِ فوق‌سرد، و سپس بارش آن‌ها')
    hints(q(6,7),first='حالت آب در ابر، مسیر سقوط و جابه‌جایی درون ابر را برای هر نوع بارش مقایسه کن.',second='تفاوت را میان بلور یخ، قطرهٔ مایع و دانهٔ یخی که در ابر رشد می‌کند پیدا کن. بالا رفتن هوا در ابر و امکان ذوب‌شدن در مسیر را نیز بررسی کن.')
    revised(q(6,7));note(q(6,7),'تگرگ در ابرهای توفانی، با کمک جریان‌های صعودی و یخ‌زدن قطره‌های فوق‌سرد روی دانهٔ یخ رشد می‌کند؛ صرف عبور باران از هوای سردِ زیر ابر، تعریف درستی برای تگرگ نیست. باران ممکن است از ذوب برف در لایهٔ گرم‌تر هم پدید آید، پس دمای تمام ابر لازم نیست بالاتر از صفر باشد. توضیح‌های کوتاه تصویر و پاسخنامه را با این دقت بخوانید.')
    right(q(6,13),0,'تودهٔ نسبتاً بزرگ آب در گودیِ سطح خشکی، محصور در خشکی؛ ممکن است ورودی یا خروجی رودخانه‌ای داشته باشد')
    right(q(6,13),1,'بسته به دریاچه: غذا، مواد معدنی، نفت و گاز، گردشگری، تعدیل آب‌وهوا و حمل‌ونقل')
    hints(q(6,13),first='گودیِ آب‌دارِ محصور در خشکی را از رودِ جاری و آبِ آزاد دریا جدا کن.',second='محصوربودن در خشکی الزاماً به معنی نداشتن رود خروجی نیست. برای اهمیت دریاچه، منابع و فعالیت اقتصادی و اثر اقلیمی را نیز بررسی کن.')
    revised(q(6,13));note(q(6,13),'نداشتن راه خروج طبیعی، شرط همهٔ دریاچه‌ها نیست. بعضی دریاچه‌ها به رود خروجی و در نهایت دریا راه دارند؛ دریاچهٔ بسته خروجی رودخانه‌ای ندارد.')
    # Aquifers need transmissive connected pores, not just saturation.
    note(q(7,4),'مقایسه مربوط به مدل شکل و برابر بودن شرایط دیگر است. تصمیم واقعی دربارهٔ ساخت سد به بررسی زمین‌شناسی، آب، محیط زیست و نیاز منطقه وابسته است و از نبود پوشش گیاهی به‌تنهایی نتیجه نمی‌شود.')
    note(q(7,5),'نسبتِ دو‌سوم رس و یک‌سوم ماسه، پاسخِ مقایسهٔ مدل همین سؤال است؛ نسخهٔ ثابت برای هر گیاه یا خاک واقعی نیست. بافت، گیاخاک، تهویه، زهکشی و نیاز گیاه نیز مهم‌اند.')
    q(7,11)['steps'][0]['items'][0]='پر کردن بشر از ماسهٔ خشک و هم‌سطح کردن آن'
    q(7,11)['steps'][0]['items'][2]='ادامه تا اشباع فضاهای بین دانه‌ها، بدون جمع‌شدن آب اضافه روی ماسه'
    hints(q(7,11),second='ماسه در آغاز خشک باشد؛ آبِ جمع‌شده بالای سطح ماسه نباید جزو فضای خالی میان دانه‌ها شمرده شود. حجم آب واقعاً واردشده را ثبت کن.')
    revised(q(7,11));note(q(7,11),'ماسه باید در آغاز خشک باشد. آب را آرام اضافه کنید تا فضای بین دانه‌ها پر شود، نه اینکه لایه‌ای آب بالای ماسه جمع شود. حجم آب مصرف‌شده، در این شرایط، تقریب حجم فضای خالی است.')
    right(q(7,12),0,'آبرفتِ درشت‌دانه و نفوذپذیر؛ آب از فضاهای به‌هم‌پیوستهٔ آن بهتر قابل برداشت است')
    hints(q(7,12),second='علاوه بر آب موجود در فضاها، باید امکان حرکت آن به سمت چاه را بررسی کرد؛ فضای خالیِ فراوان بدون اتصال و عبور مناسب، کافی نیست.')
    revised(q(7,12));note(q(7,12),'برای چاه، تنها مقدار فضای خالی کافی نیست؛ آب باید بتواند از فضاهای به‌هم‌پیوسته حرکت کند. رس نیز می‌تواند تخلخل زیاد اما نفوذپذیری و آب‌دهی کمی داشته باشد؛ جنس آبرفت‌ها هم یکسان نیست.')
    note(q(7,13),'نتیجه مربوط به آرایش دانه‌های همین دو شکل است: ذرات ریز، بخشی از فاصله‌های شکل دیگر را پر کرده‌اند. تخلخل فقط با درشتی دانه تعیین نمی‌شود؛ جورشدگی و چیدمان ذرات مهم‌اند و نفوذپذیری به اتصال فضاها نیز بستگی دارد.')
    right(q(7,14),3,'در این مدلِ نفوذپذیر بله؛ اما هر منطقهٔ اشباع، لزوماً آبخوانِ دارای آب‌دهی مناسب نیست')
    revised(q(7,14));note(q(7,14),'راهنمای درست شماره‌ها: ۱ فاصلهٔ سطح زمین تا سطح ایستابی (عمق سطح ایستابی)؛ ۲ مرز بالایی منطقهٔ اشباع (سطح ایستابی)؛ ۳ خودِ منطقهٔ اشباع. برچسبِ «منطقهٔ بالای اشباع» در تصویر آموزشی جایگزین نام کمیتِ شمارهٔ ۱ نیست. در این مدلِ نفوذپذیر، بخش اشباع آبخوان است؛ لایهٔ اشباعِ بسیار کم‌نفوذ الزاماً آبخوان با آب‌دهی مناسب نیست. پاسخ جداگانهٔ بخش ب در کلید چاپی نیامده است.')
    hints(q(7,14),second='فاصله، مرز و ناحیه را از هم جدا کن. برای آبخوان‌بودن، علاوه بر وجود آب، امکان حرکت و برداشتِ آب از لایه را هم در نظر بگیر.')
    note(q(7,20),'چاهِ خودجوش فقط وقتی آب را به بیرون می‌رساند که تراز فشار آب از سطح زمین بالاتر باشد. محصور و تحت‌فشار بودن آبخوان، به‌تنهایی خودجوش بودن هر چاه را تضمین نمی‌کند.')
    for option,text in zip(q(7,24)['steps'][0]['options'][:4],[
        'در شرایط طبیعی معمولاً بی‌رنگ، بی‌بو و کم‌کدورت‌اند.',
        'در یک ذخیرهٔ معین، دما و ترکیب معمولاً نوسان کمتری از آب سطحی دارند.',
        'بدون ورود آلاینده، معمولاً آلودگی میکروبی کمتری از آب سطحی دارند.',
        'بسته به جنس زمین، معمولاً املاح محلول بیشتری از آب سطحی دارند.'
    ]):option['text']=text
    revised(q(7,24));note(q(7,24),'دما و ترکیب آب زیرزمینی ثابتِ مطلق نیستند؛ اقلیم، عمق، جنس زمین، تغذیه و ورود آلاینده اثر دارند. بی‌رنگ و بی‌بو بودن نیز به‌تنهایی پاکی یا مناسب‌بودن آب برای آشامیدن را اثبات نمی‌کند.')
    # Fair experiments and model assumptions.
    right(q(8,2),0,'همان تخته را با نیروی هم‌اندازه و در امتداد حرکت، در دو فاصلهٔ متفاوت می‌کشیم')
    revised(q(8,2));note(q(8,2),'برای بررسی اثر جابه‌جایی، نیرو را ثابت نگه دارید؛ برای بررسی اثر نیرو، جابه‌جایی را ثابت بگیرید. رابطهٔ سادهٔ کار = نیرو × جابه‌جایی در این سؤال، برای نیروی ثابت و هم‌جهت با جابه‌جایی است.')
    right(q(8,13),0,'رها کردن گلوله‌های هم‌اندازه و هم‌شکل با وزن متفاوت، از ارتفاع یکسان')
    revised(q(8,13))
    note(q(8,13),'برای مقایسهٔ وزن، ارتفاع و شکل و اندازهٔ بیرونی گلوله‌ها و شرایط سطح برخورد را یکسان بگیرید؛ برای مقایسهٔ ارتفاع، همان گلوله را به کار ببرید. شکل گلوله‌های تصویر مقیاس دقیق ندارد. اثر فرورفتگی در ماسه، شاخص روشن‌تری از بلندی صدای برخورد است.')
    replace(q(8,1),'nativeQuestion','بخش زیادی از انرژی {{5}}','بخشی از انرژی {{5}}','Do not imply that most radio energy necessarily becomes sound')
    replace(mq(8,3),'nativeQuestion','چند کیلو ژول بر گرم انرژی کسب می کنیم','چند کیلوژول انرژی کسب می‌کنیم','Correct total-energy unit; retain original source and review note')
    # Renewable does not mean zero pollution, zero emissions or unlimited harvest.
    replace(q(9,1),'nativeQuestion','می توانند به طور مداوم جایگزین شوند و هیچ وقت تمام نمی شوند . این منابع انرژی ، زمین را آلوده نمی کنند و باعث گرمایش جهانی نمی شوند .','در فرایندهای طبیعی جایگزین می‌شوند. بسیاری از فناوری‌های این گروه، مانند باد و خورشید، در چرخهٔ عمر معمولاً آلودگی و انتشار گاز گلخانه‌ای کمتری از سوخت‌های فسیلی دارند؛ اثر محیطی آن‌ها صفر نیست .','Qualify renewable-energy claims')
    replace(q(9,1),'nativeQuestion','در کشور ایران،انتقال','در آمار مورد استفادهٔ کتاب برای ایران، انتقال','Scope historical quantity to source data')
    s=q(9,7)['steps'][0]
    for item,text in zip(s['options'][:3],[
        'کاهش آلودگی هوا با فناوری‌هایی مانند باد و خورشید، در مقایسه با سوزاندن سوخت فسیلی',
        'انتشار معمولاً کمترِ گاز گلخانه‌ای در چرخهٔ عمرِ برق بادی و خورشیدی',
        'جایگزینی طبیعی منبع، با بهره‌برداری متناسب با ظرفیت آن'
    ]):item['text']=text
    hints(q(9,7),second='کمتر بودن آلودگی و انتشار را با صفر بودن همهٔ اثرها یکی ندان. امکان جایگزینی منبع را هم با مقدار و شیوهٔ بهره‌برداری مقایسه کن.')
    revised(q(9,7));note(q(9,7),'تجدیدپذیر به معنی بی‌اثر بودن بر محیط نیست. ساخت تجهیزات، استفاده از زمین و بعضی شیوه‌های بهره‌برداری پیامد محیطی دارند؛ سوخت زیستی نیز هنگام سوختن آلاینده و کربن دی‌اکسید آزاد می‌کند. جایگزینی منابعی مانند زیست‌توده به مدیریت برداشت وابسته است.')
    note(q(9,1),'آمار حجم انتقال نفت، دادهٔ نقل‌شده در کتاب است. تجدیدپذیر بودن با صفر بودن آلودگی یا نامحدود بودن برداشت برابر نیست؛ فناوری و شیوهٔ بهره‌برداری اهمیت دارند.')
    revised(q(9,1))
    note(q(9,2),'تجدیدپذیر بودن یعنی منبع در فرایندهای طبیعی جایگزین می‌شود؛ برداشت بیش از ظرفیت، مثلاً از زیست‌توده یا ذخیرهٔ محلی زمین‌گرمایی، می‌تواند پایداری آن را مختل کند.')
    right(q(9,14),1,'فرآوری مواد گیاهی و قندی، مانند نیشکر و پسماند آن؛ مثلاً با تخمیر برای تهیهٔ اتانول')
    revised(q(9,14));note(q(9,14),'سوخت مایع زیستی مستقیماً همان تفاله نیست؛ مادهٔ گیاهی باید فرآوری شود، مثلاً قند برای تولید اتانول تخمیر می‌شود. زغالِ این بخش، زغال چوب است و با زغال‌سنگ فسیلی تفاوت دارد.')
    note(q(9,9),'این دستگاه آبگرم‌کن خورشیدی است. در مدل گردش طبیعی (ترموسیفون)، آب گرم‌تر به مخزن بالاتر می‌رود و آب سردتر به جمع‌کننده برمی‌گردد. دمای ۶۰ تا ۷۰ درجه، مثال کتاب است و به طراحی دستگاه و تابش و مصرف وابسته است.')
    replace(mq(9,2),'nativeQuestion','در حال حاضر بیش از ۸۵ درصد','در آمار نقل‌شده در کتاب، بیش از ۸۵ درصد','Avoid presenting historical book statistics as current data')
    hints(mq(9,2),first='این تست دربارهٔ سهم گزارش‌شده در کتاب است؛ آن را آمار لحظه‌ای جهان ندان.',second='سه سوخت حاصل از ذخایر دیرینهٔ جانداران را در یک گروه قرار بده و منبع غالبِ آمار کتاب را از منابع دیگر جدا کن.')
    note(mq(9,2),'عدد ۸۵ درصد، آمار منبع آموزشی است و به عنوان آمار به‌روز سال جاری ارائه نمی‌شود؛ سهم هر منبع به سال، روش محاسبه و تعریف انرژی بستگی دارد.')
    # Heat, visible colour and emissivity must not be conflated.
    note(q(10,2),'دو نقطهٔ مرجع صفر و صد درجهٔ سلسیوس برای آب خالص و فشار معمولِ سطح دریا بیان شده‌اند؛ نقطهٔ جوش با فشار تغییر می‌کند. این یک مدل آموزشیِ درجه‌بندی دماسنجِ مناسب این بازه است.')
    note(q(10,5),'در مدل سادهٔ این فصل، دمای بیشتر با میانگین انرژی جنبشی بیشترِ ذرات مرتبط است. در تعادل، دماها برابرند؛ این به معنای یکسان بودن سرعت تک‌تک ذره‌ها، به‌ویژه برای ذرات با جرم متفاوت، نیست.')
    note(q(10,19),'قوطی‌ها باید هم‌اندازه، دارای مقدار آب و دمای آغازین یکسان و در محیط یکسان باشند. نتیجهٔ تصویر، فرضِ پوشش سیاه با گسیلندگی فروسرخ بیشتر از پوشش سفیدِ مقایسه‌شده را دارد؛ رنگِ مرئی به‌تنهایی گسیلندگی همهٔ سطح‌ها را تعیین نمی‌کند. هر دو در پایان به دمای محیط نزدیک می‌شوند.')
    note(q(10,20),'علاوه بر دما، مساحت و ویژگی گسیلندگیِ سطح بر توان تابش اثر دارند. در مقایسهٔ کتاب، سطح‌ها و شرایط دیگر برابر فرض شده‌اند؛ «تیره‌تر» را به همهٔ مواد و همهٔ طول موج‌ها تعمیم ندهید.')
    right(q(10,20),1,'با سطح و گسیلندگی یکسان، دمای بالاتر تابش بیشتری دارد؛ در مدل کتاب، پوشش تیره گسیلنده‌تر فرض شده است')
    hints(q(10,20),first='«گسیلندگی» یعنی توانایی نسبی سطح در منتشرکردن تابش گرمایی. ویژگی سطح را از دما و از یکای اندازه‌گیری دما جدا کن.')
    hints(q(10,20),second='هنگام مقایسه، دما را از ویژگی گسیلندگیِ سطح جدا کن. بررسی کن کدام عامل در مدل ثابت است و کدام تغییر می‌کند؛ رنگ مرئی به‌تنهایی ویژگی همهٔ سطح‌ها را تعیین نمی‌کند.')
    revised(q(10,20))
    mq(10,4)['steps'][0]['prompt']='با فرض مقایسهٔ سطح‌های نمودار همین فصل، گزینهٔ درست را انتخاب کن.'
    revised(mq(10,4));note(mq(10,4),'در چارچوب نمودار کتاب، سطح تیره گسیلنده‌تر فرض شده است. بیان دقیق‌تر، تابش بیشتر در دمای بالاتر و گسیلندگی بیشترِ سطح است؛ رنگ مرئی به‌تنهایی معیار همهٔ سطح‌ها نیست. حرف کلید چاپی منبع، جدا از معیار تمرین حفظ شده است.')
    note(q(10,24),'خلأ رسانش و همرفت بین دو جداره را بسیار کم می‌کند، ولی مانع تابش نیست. سطح بازتابنده و درپوش عایق، مسیرهای دیگر اتلاف را کم می‌کنند؛ اتلاف گرما هرگز کاملاً صفر نمی‌شود.')
    # Later chapters: preserve existing reviewed medical/biology exceptions and
    # the dental key awaiting the author's explicit decision.
    right(q(11,7),6,'در نمونهٔ گیاهیِ شکل، دیواره و سبزدیسه دیده می‌شود؛ نمونهٔ جانوری این دو را ندارد')
    revised(q(11,7));note(q(11,7),'مقایسه دربارهٔ یاخته‌های نشان‌داده‌شده در شکل است. همهٔ یاخته‌های گیاه سبزدیسه ندارند؛ برای مثال، بسیاری از یاخته‌های ریشه فاقد سبزدیسه‌اند.')
    hints(q(12,4),second='هر سه نمونه را با یک روش و مقدار یکسان معرف بررسی کن. رنگ نشانه را با رنگ اولیهٔ همان نمونه مقایسه کن، نه با رنگ ظرف.')
    replace(q(13,1),'nativeQuestion','c- بزاق','b- بزاق','Correct duplicate subpart letter')
    note(q(13,9),'صفرا آنزیم گوارشی ندارد؛ چربی را به قطره‌های ریزتر پراکنده می‌کند تا آنزیم‌ها بهتر اثر کنند. ساخت صفرا در کبد و ذخیرهٔ آن در کیسهٔ صفرا انجام می‌شود.')
    note(q(14,6),'این توالی، مدل سادهٔ چرخهٔ قلب است. بخش عمدهٔ پرشدن بطن‌ها هنگام شل‌بودن آن‌ها رخ می‌دهد و انقباض دهلیزها پرشدن را کامل می‌کند؛ ورود خون به دهلیزها فقط به یک لحظهٔ استراحت محدود نیست.')
    right(q(14,8),1,'در شرایط معمول، شمارش نبض راه ساده‌ای برای برآورد تعداد ضربان قلب است')
    q(14,8)['steps'][0]['pairs'][1]['choices']=[
        'در شرایط معمول، شمارش نبض راه ساده‌ای برای برآورد تعداد ضربان قلب است',
        'نبض هیچ ارتباطی با ضربان قلب ندارد.',
        'با شمارش نبض می‌توان همهٔ صداهای قلب را شنید.'
    ]
    revised(q(14,8));note(q(14,8),'نبض، موج فشار در سرخرگ است و در شرایط معمول برای برآورد شمار ضربان به کار می‌رود. گوش‌دادن به قلب اطلاعات دیگری هم می‌دهد؛ این دو روش در همهٔ شرایط جایگزین کامل یکدیگر نیستند.')
    note(q(14,10),'گویچهٔ قرمز بالغ انسان هسته ندارد. پلاکت، قطعهٔ کوچکِ یاخته‌ای است و یاختهٔ کامل به حساب نمی‌آید. شکل گویچهٔ سفید نیز می‌تواند هنگام فعالیت تغییر کند؛ شکل‌های جدول، مدل سادهٔ آموزشی‌اند.')
    note(mq(14,1),'این اعداد تقریبِ آموزشی برای قلب بزرگسال‌اند؛ جرم قلب و حجم سالانهٔ خونِ پمپ‌شده با اندازهٔ بدن، فعالیت و شرایط فرد تغییر می‌کند و مقدار ثابتی برای همه نیست.')
    # Remove visual-only unbound answer lines while retaining the full prompt;
    # the native input below supplies the actual controls (no fake blank fields).
    for cid in ['g7_ch01_q07','g7_ch07_q18','g7_ch10_q17','g7_ch10_mcq01','g7_ch11_q13','g7_ch12_q05','g7_ch12_q17','g7_ch14_mcq01']:
        question=qs[cid]
        for block in question['nativeQuestion']:
            if 'text' in block:block['text']=re.sub(r'\.{3,}','…',block['text'])
        record(question,'Compact printed blank marks; native controls remain the only editable fields')

    # New figure covers a–f; part g remains complete in the native question.
    record(q(4,13),'Owner question figure replaces four isolated source figures; native part g retained')
    record(q(6,10),'Owner water-cycle question figure replaces source figure')
    for values in chapters.values():
        for question in values:
            if question.get('answerStudyVisual') and not question.get('answerStudyCaption'):
                question['answerStudyCaption']='تصویر آموزشیِ ارسالی، در کنار پاسخ کامل متنی؛ برای خواندن جزئیات روی تصویر بزنید.'
    for question in [q(6,7),q(7,14)]:
        question['answerStudyCaption']='این تصویر عین طرح ارسالی است؛ عبارت‌ها و برچسب‌های مورد اشاره را با توضیح علمیِ اصلاحیِ بالا بخوانید. برای خواندن جزئیات روی تصویر بزنید.'
    revisions=[];review=[]
    for ch,values in chapters.items():
        old_by={x['canonicalId']:x for x in json.loads(subprocess.check_output(['git','show',BASELINE+f':app/src/main/assets/content/chapter{ch}.json'],cwd=ROOT))}
        for question in values:
            old=old_by[question['canonicalId']]
            before=dict(question=old['nativeQuestion'],answer=old['nativeAnswer'])
            after=dict(question=question['nativeQuestion'],answer=question['nativeAnswer'])
            if before!=after:
                revisions.append(dict(id=question['canonicalId'],beforeNativeSha256=sha(before),afterNativeSha256=sha(after),reasons=reasons.get(question['canonicalId'],[])))
            review.append(dict(id=question['canonicalId'],sourcePage=question['sourcePage'],
                nativeQuestionAndFullKeyReviewed=True,hintsReviewed=2*len(question['steps']),
                interactionKinds=[s['kind'] for s in question['steps']],
                practiceRevision=question.get('practiceRevision',0),
                scientificNote=question.get('answerReviewNote'),changes=reasons.get(question['canonicalId'],[])))
        (ASSETS/f'chapter{ch}.json').write_text(json.dumps(values,ensure_ascii=False,indent=2)+'\n')
    (ROOT/'qa/source/native-editorial-revisions-v10.json').write_text(json.dumps(dict(baseline=BASELINE,authorization='2026-10-21 owner requests complete scientific and editorial review; original source fields/PDFs retained',revisions=revisions),ensure_ascii=False,indent=2)+'\n')
    (ROOT/'qa/all-chapters-editorial-review-v10.json').write_text(json.dumps(dict(
        questions=len(review),learningSteps=sum(len(x['steps']) for x in qs.values()),
        hints=sum(x['hintsReviewed'] for x in review),
        policy='Complete native key retained; reviewed science notes separately gated; pending dental source key unchanged',
        primarySources=[
            'https://www.aao.org/eye-health/tips-prevention/five-vision-myths',
            'https://openstax.org/books/chemistry-2e/pages/18-4-structure-and-general-properties-of-the-nonmetals',
            'https://openstax.org/books/chemistry-2e/pages/2-3-atomic-structure-and-symbolism',
            'https://hultafors.com/en-gb/products/hatchet',
            'https://archive.nptel.ac.in/content/storage2/courses/112108150/pdf/PPTs/MTS_09_m.pdf',
            'https://www.lodgecastiron.com/products/round-cast-iron-classic-skillet',
            'https://isotopes.gov/isotope-basics',
            'https://www.nssl.noaa.gov/education/svrwx101/hail/',
            'https://www.weather.gov/lmk/winter-precip',
            'https://www.usgs.gov/water-science-school/science/how-much-water-there-earth',
            'https://pubs.usgs.gov/gip/gw/glossary.html',
            'https://pubs.usgs.gov/gip/gw/how_b.html',
            'https://www.eia.gov/energyexplained/energy-and-the-environment/',
            'https://www.eia.gov/energyexplained/biomass/'
        ],reviewed=review),ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
