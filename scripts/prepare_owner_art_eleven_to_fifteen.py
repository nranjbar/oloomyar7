"""Import the twelve already-delivered chapter 11–15 figures without resampling."""
import argparse
import hashlib
import io
import json
import re
import zipfile
from pathlib import Path
from PIL import Image

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--archive',type=Path,required=True)
    args=parser.parse_args();root=Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets'
    path=root/'qa/source/owner-art-2026-10-21.json';manifest=json.loads(path.read_text())
    sha=lambda b:hashlib.sha256(b).hexdigest()
    assert sha(args.archive.read_bytes())==manifest['archiveSha256']
    dims=json.loads((assets/'content/image-dimensions.json').read_text())
    imported=[]
    with zipfile.ZipFile(args.archive)as archive:
        for name in sorted(archive.namelist()):
            if not re.fullmatch(r'question_images/g7_ch(11|12|13|14|15)_(q|mcq)\d\d_question\.png',name):continue
            raw=archive.read(name);original=Image.open(io.BytesIO(raw)).convert('RGBA')
            asset='owner_20261021_'+Path(name).stem+'.webp';target=assets/'images'/asset
            buffer=io.BytesIO();original.save(buffer,'WEBP',lossless=True,method=6);target.write_bytes(buffer.getvalue())
            decoded=Image.open(target).convert('RGBA')
            assert original.size==decoded.size and original.tobytes()==decoded.tobytes()
            cid=Path(name).stem.removesuffix('_question')
            entry=dict(question=cid,role='question',asset=asset,originalRepositoryPath=None,
                sourceFilename=name,sourceSha256=sha(raw),webpSha256=sha(target.read_bytes()),
                decodedRgbaSha256=sha(decoded.tobytes()),width=decoded.width,height=decoded.height,
                applyFullQuestionFigure=True,deliveredSource=name)
            old=next((e for e in manifest['scientific']if e['question']==cid and e['role']=='question'),None)
            if old:assert old==entry,cid
            else:manifest['scientific'].append(entry)
            dims[asset]=list(decoded.size);imported.append(name)
    assert len(imported)==12
    manifest['deferredOutsideCurrentChapterScope']=[n for n in manifest['deferredOutsideCurrentChapterScope']if n not in imported]
    manifest['scope']='chapters 1–15; the separate final examination remains outside this request'
    path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    (assets/'content/owner-scientific-art.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    (assets/'content/image-dimensions.json').write_text(json.dumps(dims,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(dict(imported=len(imported),scientificTotal=len(manifest['scientific']),
        newWebpBytes=sum((assets/'images'/('owner_20261021_'+Path(n).stem+'.webp')).stat().st_size for n in imported))))

if __name__=='__main__':main()
