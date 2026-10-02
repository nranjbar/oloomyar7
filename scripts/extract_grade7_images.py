from pathlib import Path
import fitz, json, hashlib, shutil, argparse

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description='Extract exact Grade 7 / 1405 question figures from the supplied workbook PDF.')
parser.add_argument('pdf', type=Path, help='Path to کتاب کار علوم هفتم 1405.pdf from haftom.zip')
SOURCE = parser.parse_args().pdf
OUT = ROOT / 'review'
ASSETS = ROOT / 'app/src/main/assets/images'
for folder in ('question_images', 'original_embedded', 'source_pages'):
    (OUT / folder).mkdir(parents=True, exist_ok=True)
ASSETS.mkdir(parents=True, exist_ok=True)
doc = fitz.open(SOURCE)
# Cropping the rendered source also preserves vector drawings and source labels.
# Coordinates are PDF points; the original embedded rasters are provided separately.
specs = [
 ('g7_ch01_q07.png',1,7,'main',2,(37,688,146,761),'رایانه','کم؛ تصویر کوچک کتاب، برای تشخیص رایانه کافی است.'),
 ('g7_ch02_q02.png',2,2,'main',3,(32,341,548,639),'جدول کمیت، تعریف، یکا و وسیله اندازه‌گیری','خوب؛ متن و جدول از خود PDF با حفظ بردارها رندر شده‌اند.'),
 ('g7_ch02_q07.png',2,7,'main',4,(32,351,94,449),'استوانه مدرج','کم؛ نمونه اصلی ۵۷×۱۸۹ پیکسل است.'),
 ('g7_ch02_q09.png',2,9,'main',4,(37,597,205,759),'ترازو و جابه‌جایی آب','نیازمند جایگزینی؛ عددهای تصویر با ۳۶ گرم و ۶۰ و ۷۲ متن هماهنگ نیستند.'),
 ('g7_ch02_q11.png',2,11,'main',5,(83,219,244,316),'گلوله‌های A و B و C در آب','خوب؛ شکل برداری، هر سه حرف و خط آب حفظ شده‌اند.'),
 ('g7_ch02_q12.png',2,12,'main',5,(34,388,164,467),'ساعت و زمان‌سنج؛ ساعت چپ، زمان‌سنج راست','متوسط؛ اصل تصویر ۱۵۴×۸۶ پیکسل است.'),
 ('g7_ch02_q14.png',2,14,'main',5,(27,683,419,789),'دو مداد و خط‌کش، شکل‌های ۱ و ۲','متوسط؛ دو تصویر اصلی حدود ۴۸۵×۷۷ و ۴۸۹×۶۹ پیکسل‌اند.'),
 ('g7_ch02_q15.png',2,15,'main',6,(38,46,542,215),'از راست: انواع نیروسنج، استوانه مدرج، ترازو','متوسط؛ ابزارها و پرانتز گروه نیروسنج حفظ شده‌اند.'),
 ('g7_ch02_mcq03.png',2,3,'mcq',6,(55,446,125,549),'جسم ۱۰۰ گرمی آویزان به نیروسنج','متوسط؛ شکل و نوشته ۱۰۰ گرم حفظ شده‌اند.'),
 ('g7_ch02_mcq04.png',2,4,'mcq',6,(110,541,222,632),'ظرف مکعبی با طول ضلع ۱۰ سانتی‌متر','متوسط؛ هر سه برچسب ۱۰ سانتی‌متر حفظ شده‌اند.'),
 ('g7_ch02_mcq05.png',2,5,'mcq',6,(40,671,147,787),'ترازوی دیجیتالی','متوسط؛ برای تشخیص ابزار کافی است.'),
]
manifest=[]
for name,ch,q,section,page,bbox,caption,quality in specs:
    pg=doc[page-1]; rect=fitz.Rect(bbox)
    dest=OUT/'question_images'/name
    # Remove only irrelevant neighbouring text/checkboxes from a temporary PDF
    # view. Source figures, scale marks, labels and supplied source stay intact.
    render_doc=fitz.open(SOURCE)
    render_page=render_doc[page-1]
    if name=='g7_ch02_q15.png':
        render_page.draw_rect(fitz.Rect(260,38,575,55.5),color=None,fill=(1,1,1),overlay=True)
    if name=='g7_ch02_mcq03.png':
        render_page.draw_rect(fitz.Rect(111,458,126,476),color=None,fill=(1,1,1),overlay=True)
    render_page.get_pixmap(matrix=fitz.Matrix(3,3),clip=rect,alpha=False).save(dest)
    render_doc.close()
    shutil.copy2(dest,ASSETS/name)
    originals=[]
    for img in pg.get_images(full=True):
        xref=img[0]
        if not any((r & rect).get_area()>r.get_area()*.65 for r in pg.get_image_rects(xref)): continue
        data=doc.extract_image(xref)
        native=f'{Path(name).stem}_xref{xref}.{data["ext"]}'
        (OUT/'original_embedded'/native).write_bytes(data['image'])
        originals.append(dict(file=native,width=data['width'],height=data['height']))
    # These two single-image prompts need no surrounding page labels: extract
    # the actual image bytes and normalize to PNG without introducing page text.
    if name in ('g7_ch01_q07.png','g7_ch02_mcq05.png'):
        native=doc.extract_image(23 if name.startswith('g7_ch01') else 65)
        pix=fitz.Pixmap(native['image'])
        if pix.n>3: pix=fitz.Pixmap(fitz.csRGB,pix)
        pix.save(dest)
        shutil.copy2(dest,ASSETS/name)
    check=fitz.Pixmap(str(dest))
    manifest.append(dict(file=name,chapter=ch,sourceNumber=q,section=section,sourcePage=page,cropPdfPoints=bbox,renderScale=(None if name in ('g7_ch01_q07.png','g7_ch02_mcq05.png') else 3),extractionMethod=('embedded image decoded losslessly to PNG' if name in ('g7_ch01_q07.png','g7_ch02_mcq05.png') else 'source page render at 3x'),width=check.width,height=check.height,caption=caption,qualityNote=quality,originalImages=originals,sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
for i in range(1,6):
    doc[i].get_pixmap(matrix=fitz.Matrix(2,2),alpha=False).save(OUT/'source_pages'/f'page_{i+1:02}.png')
(OUT/'image_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
(ROOT/'docs/grade7_image_manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps([dict(file=x['file'],size=[x['width'],x['height']],native=x['originalImages']) for x in manifest],ensure_ascii=False,indent=2))
