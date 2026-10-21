"""Independent content/source/package audit for all fifteen chapters (317 Qs).

Does not claim educational answers are scientifically approved solely because
the JSON agrees with itself. Printed-key differences are reported separately.
"""
import collections,hashlib,io,json,re,subprocess
from pathlib import Path
import fitz
from PIL import Image

ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'app/src/main/assets'
EXPECTED=[8,20,31,23,22,21,34,26,18,29,13,20,17,15,20]
MAIN=[8,15,24,19,17,21,28,19,15,25,13,20,12,10,14]
PRINTED_KEYS={4:['ب','ج','ب','الف'],5:['ب','ج','د','الف','ج'],7:['الف','ب','د','ب','ج','ب'],
 8:['د','الف','ج','الف','ج','ب','الف'],9:['ب','د','ج'],10:['ب','الف','ج','الف'],
 13:['ب','الف','ج','الف','د'],14:['ب','د','ب','الف','ج'],15:['ج','ج','الف','د','د','ب']}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_text())

source_manifest=load(ROOT/'qa/source/1405-source-manifest.json')
assert source_manifest['edition']==1405 and source_manifest['grade']==7
docs={}
for entry in source_manifest['files']:
    p=ASSETS/entry['asset'];assert sha(p)==entry['sha256'],str(p)
    docs[p.name]=fitz.open(p);assert len(docs[p.name])==entry['pages']
assert sha(ASSETS/'source/workbook.pdf')=='c67f3709bc3134ae50566722257b38f38726d64f4620d7800da71ec8c8800321'

records=load(ROOT/'qa/source/all-chapters-index.json');source={r['id']:r for r in records}
assert len(source)==317
def check_pdf(asset,segments,file):
    assert asset.startswith('pdf:'+file+'#'),asset
    raw=asset.split('#')[1].split('|')[0].split(';');assert len(raw)==len(segments)
    for encoded,seg in zip(raw,segments):
        values=[float(v) for v in encoded.split(',')]
        assert values==[seg['page'],*seg['bbox']]
        pn,*bb=values;assert int(pn)==pn and 1<=pn<=len(docs[file])
        rect=fitz.Rect(bb);page=docs[file][int(pn)-1]
        assert rect.width>0 and rect.height>0 and rect.x0>=0 and rect.y0>=0
        assert rect.x1<=page.rect.width+1 and rect.y1<=page.rect.height+1,(asset,page.rect)

native=load(ROOT/'qa/source/readable-prompts.json')['questions']
old_records={r['id']:r for r in load(ROOT/'qa/source/chapters1-3-index.json')}
digits=str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩يك','01234567890123456789یک')
def inventory(t):return collections.Counter(re.sub('[^آ-یA-Za-z0-9]','',t.translate(digits)))
qs_all=[];ids=set();step_ids=set();hint_pairs=set();images=set();inline=[];native_checks=[];kinds=collections.Counter()
for c,expected in enumerate(EXPECTED,1):
    qs=load(ASSETS/f'content/chapter{c}.json');assert len(qs)==expected
    assert [q['number'] for q in qs]==list(range(1,expected+1))
    assert [q['sourceNumber']for q in qs]==list(range(1,MAIN[c-1]+1))+list(range(1,expected-MAIN[c-1]+1))
    for q in qs:
        cid=q['canonicalId'];r=source[cid];assert q['id']not in ids;ids.add(q['id'])
        assert q['relatedChapter']==c and q['grade']==7 and q['sourceEdition']=='1405'
        assert q['sourcePolicy']=='verbatim_original_pdf'
        check_pdf(q['sourceQuestionVisual'],r['segments'],'workbook.pdf')
        assert q['sourceQuestionVisual']==r['questionAsset']
        check_pdf(q['answerVisual'],r['answerSegments'],f'answers{c:02}.pdf')
        assert (q['section']=='چهارگزینه‌ای')==(r['kind']=='mcq')
        if c>=4 and r['kind']=='mcq':
            assert r['keyLetter']==PRINTED_KEYS[c][r['number']-1]
            if cid=='g7_ch13_mcq03':
                assert q['nativeAnswer']==[dict(type='text',text='ج')]
                assert q['steps'][0]['kind']=='match' and [p['right']for p in q['steps'][0]['pairs']]==['آسیا','پیش','نیش']
                assert len(q['printedOptions'])==4 and not q['practiceUsesPrintedKey'] and q['sourceReviewNote']
            else:
                correct=[o['text'] for o in q['steps'][0]['options'] if o['correct']]
                expected_key='ج' if cid=='g7_ch10_mcq04' else r['keyLetter']
                assert len(correct)==1 and correct[0].startswith(expected_key+'- ')
                if cid=='g7_ch10_mcq04':assert not q['practiceUsesPrintedKey'] and q['sourceReviewNote']
            assert len(r['answerSegments'])==1 and r['answerSegments'][0]['bbox'][2]-r['answerSegments'][0]['bbox'][0]<60
        if c<=3:
            assert inventory(native[cid]['text']+native[cid].get('sourceFigureText',''))==inventory(old_records[cid]['source_text_extracted'])
            if q.get('readablePrompt') is not None:assert q['readablePrompt']==native[cid]['text']
        elif q.get('readablePrompt'):
            assert inventory(q['readablePrompt'])==inventory(r['questionText']),(cid,'reading words changed')
        for asset in [q.get('visual'),q.get('originalFigureVisual'),q.get('promptVisual')]+[v['asset']for v in q.get('visuals',[])]:
            if asset:
                if asset.startswith('pdf:'):
                    entries=asset.split('#')[1].split('|')[0].split(';')
                    segs=[dict(page=int(v[0]),bbox=[float(n)for n in v[1:]])for v in(entry.split(',')for entry in entries)]
                    check_pdf(asset,segs,asset[4:].split('#')[0])
                else:images.add(asset)
        for s in q['steps']:
            assert s['id']not in step_ids;step_ids.add(s['id'])
            assert all(s.get(k,'').strip()for k in ['prompt','answer','hint1','hint2']),cid
            assert s['hint1']!=s['hint2'] and s['answer']==q['sourceAnswerTextExtracted']
            assert not s['explanation'];h=(s['hint1'],s['hint2']);assert h not in hint_pairs,(cid,'duplicated hints');hint_pairs.add(h)
            k=s['kind'];kinds[k]+=1
            if k in ['single','multi']:
                opts=s['options'];assert len(opts)==len({o['text']for o in opts})
                n=sum(o['correct']for o in opts)
                assert (n==1 and len(opts)>1)if k=='single'else 0<s['pick']<=n<len(opts)
            elif k=='match':
                if s.get('choices'):assert all(p['right']in s['choices']for p in s['pairs']),(cid,'stale shared word bank')
                for pair in s['pairs']:
                    choices=pair.get('choices')or s.get('choices')or[p['right']for p in s['pairs']]
                    assert pair['right']in choices and len(choices)==len(set(choices)) and len(choices)>=2,(cid,pair)
                for alt in s.get('acceptedPairOrders',[]):
                    assert len(alt)==len(s['pairs'])
                    assert all(v in(p.get('choices')or s.get('choices')or[x['right']for x in s['pairs']])for p,v in zip(s['pairs'],alt))
            elif k=='classify':assert all(x['category']in s['categories']for x in s['items'])
            elif k=='order':assert sorted(s['correctOrder'])==list(range(len(s['items'])))
            else:raise AssertionError((cid,k))
            if s.get('clozeTemplate'):
                slots=[int(x)for x in re.findall(r'\{\{(\d+)}}',s['clozeTemplate'])]
                assert sorted(slots)==list(range(len(s['pairs']))),cid
                inline.append(cid)
                if c>=4:
                    a=inventory(r['questionText']);b=inventory(re.sub(r'\{\{\d+}}','',s['clozeTemplate']))
                    # Isolated text-layer alef from the adjacent crop boundary has no
                    # corresponding printed word. See the source crop visual audit.
                    artifact=(cid in ['g7_ch13_q01','g7_ch14_q01'] and a-b=={'ا':1} and not b-a)
                    assert a==b or artifact,(cid,'native transcript changed source letters',dict(b-a),dict(a-b))
                    native_checks.append(dict(id=cid,letterInventoryPreserved=a==b,textLayerBoundaryArtifact=artifact))
        qs_all.append(q)
assert len(ids)==317 and len(step_ids)==334 and len(inline)==29
assert all(any(cid.startswith(f'g7_ch{c:02}_')for cid in inline)for c in range(1,16))
for asset in images:
    assert asset.endswith('.webp'),asset
    im=Image.open(ASSETS/'images'/asset);im.verify()
owner={r['asset']:r for r in load(ROOT/'qa/source/owner-art-2026-10-21.json')['scientific']}
for entry in load(ROOT/'qa/source/lossless-scientific-assets.json'):
    out=ASSETS/'images'/entry['asset'];reviewed=owner.get(entry['asset'])
    assert sha(out)==(reviewed['webpSha256'] if reviewed else entry['webpSha256'])
    raw=Image.open(out).convert('RGBA').tobytes()
    assert hashlib.sha256(raw).hexdigest()==(reviewed['decodedRgbaSha256'] if reviewed else entry['decodedRgbaSha256'])
    if reviewed:assert reviewed['sourceRgbaSha256']==entry['decodedRgbaSha256']
    original=ROOT/entry['originalRepositoryPath'] if entry.get('originalRepositoryPath') else ASSETS/'images'/entry['source']
    if original.exists():assert sha(original)==entry['originalSha256']
    else:
        data=subprocess.check_output(['git','show','2787d494bce98497049e83431daa9b1f1b51a11b:app/src/main/assets/images/'+entry['source']],cwd=ROOT)
        assert hashlib.sha256(data).hexdigest()==entry['originalSha256']
    if original.exists():
        expected=Image.open(original).convert('RGBA').tobytes()
        assert hashlib.sha256(expected).hexdigest()==entry['decodedRgbaSha256']
        if not reviewed or reviewed['encoding']['mode']=='lossless':assert expected==raw
    # Original-pixel provenance stays strict. Optimized derivatives have separate
    # measured quality and decoded-pixel checks in validate_owner_art.py.
for entry in load(ROOT/'qa/source/design-assets-1405.json'):
    assert (ASSETS/'images'/entry['asset']).stat().st_size==entry['bytes']
catalog=load(ASSETS/'content/catalog.json');assert [x['questions']for x in catalog]==EXPECTED
assert [x['steps']for x in catalog]==[sum(len(q['steps'])for q in qs_all if q['relatedChapter']==c)for c in range(1,16)]
app_gradle=(ROOT/'app/build.gradle.kts').read_text();assert 'ignoreAssetsPattern = "*.png:'in app_gradle
assert 'com.oloomyar.app.grade7'in app_gradle and 'versionCode = 11'in app_gradle
assert 'android.permission.INTERNET'not in(ROOT/'app/src/main/AndroidManifest.xml').read_text()
packaged=[p for p in ASSETS.rglob('*')if p.is_file()and p.suffix!='.png'and not p.name.startswith('.')]
report=dict(status='PASS',edition=1405,chapters=15,questions=317,learningGroups=334,hintPairs=len(hint_pairs),
    chapterCounts=EXPECTED,inlineQuestions=inline,nativeExtraTranscriptChecks=native_checks,
    immutableSourcePdfs=16,sourceQuestionRegions=317,isolatedSourceAnswers=317,scientificWebpAssets=len(images),
    interactionKinds=dict(kinds),packagedAssetBytes=sum(p.stat().st_size for p in packaged),
    contentRequiringAuthorConfirmation=['g7_ch13_mcq03'],
    documentedPracticeCorrections=[q['canonicalId']for q in qs_all if not q.get('practiceUsesPrintedKey',True)],
    printedUnitReviewNotes=['g7_ch08_mcq03'],
    wholeFloatingFiguresRestored=sum(len(r.get('paperAdditions',[]))for r in records),
    neighbouringFigureFragmentsExcluded=sum(len(r.get('paperExclusions',[]))for r in records),
    notes=['Original question and answer PDFs are immutable and SHA-256 verified',
        'Native transcription preserves source letters; two PDF boundary extraction artifacts are visually documented',
        'Source MCQ key 13/3 is preserved, flagged, and not silently corrected',
        'Android build, native UI, bitmap rendering and install replacement are separate checks'])
dump_path=ROOT/'qa/grade7_content_validation.json';dump_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items()if k not in ['inlineQuestions','nativeExtraTranscriptChecks']},ensure_ascii=False))
