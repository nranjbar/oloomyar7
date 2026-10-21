"""Retain source diagram pixels/callouts while removing neighbouring prose ink.

The workbook is never edited. Redactions apply only to identified text spans in
an in-memory copy: images=0 and graphics=0 preserve all image/vector objects.
Four isolated PDF pages retain their original scientific labels and numbers.
"""
import hashlib
import json
import re
from pathlib import Path
import fitz
from native_scientific_figures import decode_original

ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'app/src/main/assets'
SPECS=[
    ('g7_ch11_q07',46,(28.65,605,295.53,768),'adjacent question and dotted answer lines'),
    ('g7_ch12_q18',50,(29,390,222,518),'adjacent multiple-choice checkbox'),
    ('g7_ch14_q02',54,(41.4,346.88,239.1,539.06),'adjacent answer parentheses and dots'),
    ('g7_ch15_q02',57,(50,384,260,632),'adjacent answer lines and prose below image'),
]
def write():
    original=ASSETS/'source/workbook.pdf';doc=fitz.open(original);out=fitz.open();records=[]
    for cid,pn,bbox,reason in SPECS:
        page=doc[pn-1];clip=fitz.Rect(bbox);removed=[]
        for block in page.get_text('dict')['blocks']:
            for line in block.get('lines',[]):
                for span in line['spans']:
                    rect=fitz.Rect(span['bbox']);text=span['text']
                    if not rect.intersects(clip)or not text.strip():continue
                    discard=(pn==46 and ('دو شباهت و دو تفاوت'in text or re.fullmatch(r'[\s.]+',text)))or\
                        (pn==50 and '\uf063'in text)or\
                        (pn==54 and re.fullmatch(r'[\s.()]+',text))or\
                        (pn==57 and ('.'in text or rect.y0>=628))
                    if discard:
                        removed.append(dict(text=text,bbox=list(rect)))
                        page.add_redact_annot(rect,fill=False,cross_out=False)
        assert removed,cid
        page.apply_redactions(images=0,graphics=0,text=0)
        target=out.new_page(width=clip.width,height=clip.height)
        target.show_pdf_page(target.rect,doc,pn-1,clip=clip)
        records.append(dict(question=cid,sourcePage=pn,sourceBox=list(bbox),page=len(out),
            reason=reason,removedTextSpans=removed,asset=f'pdf:native_diagrams_11_15.pdf#{len(out)},0,0,{target.rect.width},{target.rect.height}'))
    target=ASSETS/'source/native_diagrams_11_15.pdf';out.save(target,garbage=4,deflate=True,no_new_id=True)
    # Proof that every embedded image in the derived PDF is an unaltered original.
    source=fitz.open(original);derived=fitz.open(target)
    def image_hashes(pdf,pages=None):
        records={}
        for page in ([pdf[i]for i in pages]if pages is not None else pdf):
            for info in page.get_images():
                rgba=decode_original(pdf,info[0],info[1]);digest=hashlib.sha256(rgba.tobytes()).hexdigest()
                records[digest]=dict(width=rgba.width,height=rgba.height,decodedRgbaSha256=digest)
        return records
    before=image_hashes(source,sorted({s[1]-1 for s in SPECS}));after=image_hashes(derived)
    assert after and set(after)<=set(before)
    payload=dict(sourceWorkbookSha256=hashlib.sha256(original.read_bytes()).hexdigest(),
        derivedPdfSha256=hashlib.sha256(target.read_bytes()).hexdigest(),
        policy='Only neighbouring question/answer text spans removed; all source images and vector objects retained',
        images=list(after.values()),figures=records)
    (ROOT/'qa/source/native-biology-diagrams.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n')
    mapping={r['question']:r['asset']for r in records}
    for ch in range(11,16):
        path=ASSETS/f'content/chapter{ch}.json';qs=json.loads(path.read_text())
        for q in qs:
            if q['canonicalId']in mapping:
                figures=[b for b in q['nativeQuestion']if b['type']=='figure'];assert len(figures)==1
                figures[0]['asset']=mapping[q['canonicalId']]
        path.write_text(json.dumps(qs,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
