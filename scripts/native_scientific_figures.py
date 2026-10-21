"""Extract original embedded scientific pixels without overlaid PDF question prose.

No resampling, retouching or generated art. Lossless RGBA hashes prove identity.
Vector diagrams and diagrams with external source labels stay as PDF regions.
"""
import hashlib
import io
import json
from pathlib import Path
import fitz
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
SINGLE={
 'g7_ch06_q06':(21,42.6,57.3),
 'g7_ch07_q04':(24,38.4,624.21),
 'g7_ch07_q05':(25,31.5,51),
 'g7_ch08_q03':(30,34.95,617.92),
 'g7_ch08_q07':(31,35.65,416),
 'g7_ch08_q08':(31,28.5,716.53),
 'g7_ch08_q14':(32,34.95,678.64),
 'g7_ch08_mcq05':(34,31.45,350.5),
 'g7_ch09_q04':(35,39.1,597.64),
 'g7_ch09_q13':(37,37,557.11),
 'g7_ch10_q09':(40,33,546.63),
 'g7_ch10_mcq02':(44,47.5,414.39),
 'g7_ch10_mcq03':(44,47.5,611.54),
}

def decode_original(doc,xref,smask):
    pix=fitz.Pixmap(doc,xref)
    if pix.colorspace is not None and pix.colorspace.n!=3:pix=fitz.Pixmap(fitz.csRGB,pix)
    if smask:pix=fitz.Pixmap(pix,fitz.Pixmap(doc,smask))
    return Image.frombytes('RGBA'if pix.alpha else'RGB',[pix.width,pix.height],pix.samples).convert('RGBA')

class OriginalFigures:
    def __init__(self):
        self.doc=fitz.open(ASSETS/'source/workbook.pdf');self.records={}
        self.dimensions=json.loads((ASSETS/'content/image-dimensions.json').read_text())
    def extract(self,cid,spec,suffix=''):
        page,x0,y0=spec
        images=[(im,rect)for im in self.doc[page-1].get_images()for rect in self.doc[page-1].get_image_rects(im[0])]
        im,rect=min(images,key=lambda v:abs(v[1].x0-x0)+abs(v[1].y0-y0))
        assert abs(rect.x0-x0)+abs(rect.y0-y0)<0.1,(cid,rect,spec)
        rgba=decode_original(self.doc,im[0],im[1])
        name=f'native_1405_{cid}{suffix}.webp';path=ASSETS/'images'/name
        encoded=io.BytesIO();rgba.save(encoded,'WEBP',lossless=True,method=6)
        temporary=path.with_suffix('.webp.tmp');temporary.write_bytes(encoded.getvalue());temporary.replace(path)
        decoded=Image.open(path).convert('RGBA');assert decoded.size==rgba.size and decoded.tobytes()==rgba.tobytes()
        self.dimensions[name]=list(rgba.size)
        self.records[name]=dict(question=cid,asset=name,pdf='workbook.pdf',page=page,xref=im[0],smask=im[1],
            sourceBox=list(rect),width=rgba.width,height=rgba.height,
            decodedRgbaSha256=hashlib.sha256(rgba.tobytes()).hexdigest(),webpSha256=hashlib.sha256(path.read_bytes()).hexdigest())
        return name
    def apply(self,cid,blocks):
        if cid in SINGLE:
            block=next(b for b in blocks if b['type']=='figure')
            block['asset']=self.extract(cid,SINGLE[cid])
            if cid=='g7_ch08_mcq05':block['text']='۱: خودروی سمت چپ — ۲: خودروی سمت راست'
            if cid=='g7_ch08_q08':
                # The supplied raster has a fragment of a neighbouring object in
                # its bottom white margin; the complete torch ends well above it.
                w,h=self.dimensions[block['asset']]
                self.records[block['asset']]['displayCrop']=[0,0,w,h-12]
                block['asset']=f'crop:{block["asset"]}#0,0,{w},{h-12}'
        if cid=='g7_ch08_q04':
            index=next(i for i,b in enumerate(blocks)if b['type']=='figure')
            blocks[index:index+1]=[dict(type='figure',asset=self.extract(cid,(31,22.4,48.9),'_12'),text='۱: شکل سمت چپ — ۲: شکل سمت راست'),
                                  dict(type='figure',asset=self.extract(cid,(31,382.35,56.6),'_3'),text='۳')]
        if cid=='g7_ch10_q21':
            index=next(i for i,b in enumerate(blocks)if b.get('asset','').startswith('pdf:workbook.pdf#43,49.6,'))
            blocks[index:index+1]=[dict(type='figure',asset=self.extract(cid,(43,49.6,407.53),'_dark'),text='چای پررنگ'),
                                  dict(type='figure',asset=self.extract(cid,(43,145.35,397.02),'_light'),text='چای کم‌رنگ')]
    def finish(self):
        for old in (ASSETS/'images').glob('native_1405_*.webp'):
            if old.name not in self.records:old.unlink();self.dimensions.pop(old.name,None)
        payload=json.dumps(list(self.records.values()),ensure_ascii=False,indent=2)+'\n'
        (ROOT/'qa/source/native-scientific-extractions.json').write_text(payload)
        (ASSETS/'content/native-scientific-extractions.json').write_text(payload)
        (ASSETS/'content/image-dimensions.json').write_text(json.dumps(self.dimensions,ensure_ascii=False,indent=2)+'\n')
