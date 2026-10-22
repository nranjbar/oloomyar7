"""Inspect actual outputs, not an estimate, before shipping an Android build."""
import hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
outputs=[]
for kind,relative in [('debug','app/build/outputs/apk/debug/app-debug.apk'),('releaseBundle','app/build/outputs/bundle/release/app-release.aab')]:
    path=ROOT/relative;assert path.exists(),path
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist();prefix='assets/'if kind=='debug'else'base/assets/'
        assets=[n for n in names if n.startswith(prefix)]
        assert sum(n.endswith('/source/workbook.pdf')for n in assets)==1
        assert sum('/source/answers'in n and n.endswith('.pdf')for n in assets)==15
        assert sum('/content/chapter'in n and n.endswith('.json')for n in assets)==15
        assert not any(n.endswith('.png')for n in assets),'Duplicate raster originals were packaged'
        assert all(prefix+f'images/chapter_{c:02}.webp'in names for c in range(1,16))
        splash=prefix+'ui_art/launch_grade7.webp'
        assert splash in names and archive.getinfo(splash).file_size<180_000
        qs=[q for c in range(1,16)for q in json.loads(archive.read(prefix+f'content/chapter{c}.json'))]
        assert len(qs)==317
        assert all(q['answerVisual'].startswith('pdf:answers')for q in qs)
        native=qs
        assert len(native)==317 and all(q['nativeQuestion'] and q['nativeAnswer']for q in native)
        assert not any(b.get('asset')==q['sourceQuestionVisual']for q in native for b in q['nativeQuestion'])
        assert not any(b['type']in['input','cloze','barChart','reaction','flow']for q in native for b in q['nativeAnswer'])
        reference_figures=[(q['canonicalId'],b['asset'])for q in native for b in q['nativeAnswer']if b['type']=='figure']
        assert reference_figures==[('g7_ch10_q13','pdf:answers10.pdf#2,37.35,464.6,197.05,629.61')]
        assert sum(bool(q.get('answerStudyVisual')) for q in qs)==97
        for q in qs:
            if q.get('answerStudyVisual'):assert prefix+'images/'+q['answerStudyVisual'] in names
        # All 26 new answers retain their original resolution. Bound growth by
        # measured new image bytes and 1 MB for code/JSON metadata plus the Bazaar SDK.
        # v0.11 used 0.5 MB; the additional 0.5 MB budgets the billing dependency.
        optimization=json.loads((ROOT/'qa/source/image-optimization-v11.json').read_text())
        image_growth=optimization['afterImageBytes']-optimization['beforeImageBytes']
        assert path.stat().st_size<61_047_246+image_growth+1_000_000,'Unexplained package growth beyond the new measured artwork'
        outputs.append(dict(kind=kind,path=relative,bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            assetEntries=len(assets),nativeQuestions=len(native),nativeAnswers=len(native),compressedAssetBytes=sum(archive.getinfo(n).compress_size for n in assets),
            previousThreeChapterApkBytes=47_596_130))
out=ROOT/'qa/android_package_size.json';out.write_text(json.dumps(dict(status='PASS',outputs=outputs),indent=2)+'\n')
print(out.read_text())
