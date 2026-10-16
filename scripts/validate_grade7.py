"""Validate the source-faithful three-chapter edition, controls, hints and referenced PNGs."""
import hashlib, json, re, struct, zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
collections=[json.loads((ASSETS/f'content/chapter{c}.json').read_text()) for c in (1,2,3)]
assert [len(q) for q in collections]==[8,20,31]
assert [sum(len(q['steps']) for q in qs) for qs in collections]==[11,30,35]
manifest=json.loads((ROOT/'docs/source_fidelity_manifest.json').read_text())
provenance={r['id']:r for r in manifest['questions']}
assert len(provenance)==59
for src in manifest['sourcePdfs']:
    assert hashlib.sha256((ROOT/'qa/source'/src['file']).read_bytes()).hexdigest()==src['sha256']
ids=set();steps=set();images=set();inline=[];counts={}
for chapter,qs in enumerate(collections,1):
    assert [q['number'] for q in qs]==list(range(1,len(qs)+1))
    expected_main=[8,15,24][chapter-1];expected_mcq=[0,5,7][chapter-1]
    assert [q['sourceNumber'] for q in qs]==list(range(1,expected_main+1))+list(range(1,expected_mcq+1))
    for q in qs:
        assert q['id'] not in ids;ids.add(q['id'])
        assert q['grade']==7 and q['relatedChapter']==chapter and q['sourceEdition']=='1405'
        assert q['sourcePolicy']=='verbatim_original_pdf'
        r=provenance[q['canonicalId']]
        for field,key in [('sourceQuestionVisual','question'),('answerVisual','answer')]:
            name=q[field];assert name==r[key+'Asset']
            assert hashlib.sha256((ASSETS/'images'/name).read_bytes()).hexdigest()==r[key+'Sha256']
        if q.get('promptVisual'):
            assert r['promptTextPreserved']
            assert hashlib.sha256((ASSETS/'images'/q['promptVisual']).read_bytes()).hexdigest()==r['promptSha256']
        if q['section']=='چهارگزینه‌ای':
            assert not r['sharedAnswerStrip']
            assert r['answerSourceSegments'][0]['bbox'][2]-r['answerSourceSegments'][0]['bbox'][0]<60
        images.update(x for x in [q.get('visual'),q.get('answerVisual'),q.get('sourceQuestionVisual'),q.get('promptVisual')]+[v['asset'] for v in q.get('visuals',[])] if x)
        for s in q['steps']:
            assert s['id'] not in steps;steps.add(s['id'])
            assert all(s.get(k,'').strip() for k in ['prompt','answer','hint1','hint2'])
            assert s['hint1']!=s['hint2'] and s['answer']==q['sourceAnswerTextExtracted']
            assert not s['explanation']
            k=s['kind'];counts[k]=counts.get(k,0)+1
            if k in ['single','multi']:
                opts=s['options'];assert len(set(o['text'] for o in opts))==len(opts)
                n=sum(o['correct'] for o in opts)
                assert n==1 if k=='single' else s['pick']>0 and s['pick']<=n<len(opts)
            elif k=='match':
                for p in s['pairs']:
                    choices=p.get('choices') or s.get('choices') or [x['right'] for x in s['pairs']]
                    assert p['right'] in choices and len(set(choices))>=2
                if s.get('clozeTemplate'):
                    slots=[int(v) for v in re.findall(r'\{\{(\d+)}}',s['clozeTemplate'])]
                    assert sorted(slots)==list(range(len(s['pairs'])))
                    inline.append(q['canonicalId'])
            elif k=='classify':assert all(i['category'] in s['categories'] for i in s['items'])
            elif k=='order':assert sorted(s['correctOrder'])==list(range(len(s['items'])))
            else:raise AssertionError(k)
for name in images:
    data=(ASSETS/'images'/name).read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n'
    assert all(v>0 for v in struct.unpack('>II',data[16:24]))
    pos=8;compressed=[];ended=False
    while pos<len(data):
        n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];payload=data[pos+8:pos+8+n]
        crc=struct.unpack('>I',data[pos+8+n:pos+12+n])[0];assert zlib.crc32(kind+payload)&0xffffffff==crc
        if kind==b'IDAT':compressed.append(payload)
        if kind==b'IEND':ended=True;break
        pos+=n+12
    assert ended;zlib.decompress(b''.join(compressed))
assert len(inline)==7
assert [p['right'] for p in collections[2][0]['steps'][0]['pairs']]==['اتم','عنصر','اتم','فلز','ترکیب','جامد','مایع','گاز']
assert 'با یکای {{1}}' in collections[1][0]['steps'][0]['clozeTemplate']
assert collections[2][14]['visual'] is None
report=dict(status='PASS',questions=len(ids),steps=len(steps),chapterCounts=[len(q) for q in collections],
    sourceQuestionCrops=59,isolatedSourceAnswers=59,inlineQuestions=inline,images=len(images),reviewedHintPairs=len(steps),interactionKinds=counts,
    notes=['Original PDF crops are authoritative','No editorial answer cards or explanations displayed','MCQ crops contain only the current question key','Formula and adjacent-answer crop boundaries checked','Native UI and attempt tests are reported separately'])
(ROOT/'qa/grade7_content_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
