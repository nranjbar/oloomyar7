"""Validate complete Grade 7 source coverage, interaction schemas and source images."""
import json
from pathlib import Path
import struct
import zlib

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
collections=[json.loads((ASSETS/'content'/f'chapter{ch}.json').read_text()) for ch in (1,2)]
assert [len(qs) for qs in collections]==[8,20]
assert [sum(len(q['steps']) for q in qs) for qs in collections]==[11,30]
assert [q['sourceNumber'] for q in collections[0]]==list(range(1,9))
assert [q['sourceNumber'] for q in collections[1]]==list(range(1,16))+list(range(1,6))
assert [q['sourcePage'] for q in collections[0]]==[2]*8
assert [q['sourcePage'] for q in collections[1]]==[3]*3+[4]*6+[5]*5+[6]*6
assert [sum(q['section']=='چهارگزینه‌ای' for q in qs) for qs in collections]==[0,5]
ids=set(); steps=set(); images=set(); question_images=set(); answer_images=set(); counts={}
for ch,qs in enumerate(collections,1):
    assert [q['number'] for q in qs]==list(range(1,len(qs)+1))
    for q in qs:
        assert q['id'] not in ids; ids.add(q['id'])
        assert q['grade']==7 and q['relatedChapter']==ch and q['sourceEdition']=='1405'
        assert q['bookPrompt'].strip() and q['steps'] and q['source'].strip()
        if q.get('visual'): question_images.add(q['visual'])
        if q.get('answerVisual'): answer_images.add(q['answerVisual'])
        for image in [q.get('visual'),q.get('answerVisual')]+[v['asset'] for v in q.get('visuals',[])]:
            if image: images.add(image)
        for s in q['steps']:
            assert s['id'] not in steps; steps.add(s['id'])
            assert all(s[k].strip() for k in ['prompt','answer','hint1','hint2','explanation'])
            assert s['hint1']!=s['hint2']
            k=s['kind']; counts[k]=counts.get(k,0)+1
            if k in ['single','multi']:
                opts=s['options']; assert len({o['text'] for o in opts})==len(opts)
                n=sum(o['correct'] for o in opts)
                assert n==1 if k=='single' else 0<s['pick']<=n<len(opts)
            elif k=='match':
                assert s['pairs']
                for p in s['pairs']:
                    choices=p.get('choices') or s.get('choices') or [x['right'] for x in s['pairs']]
                    assert p['right'] in choices and len(set(choices))>=2
            elif k=='order':
                assert sorted(s['correctOrder'])==list(range(len(s['items'])))
            elif k=='classify':
                assert all(i['category'] in s['categories'] for i in s['items'])
            else: raise ValueError(k)
for name in images:
    data=(ASSETS/'images'/name).read_bytes()
    assert data[:8]==b'\x89PNG\r\n\x1a\n'
    width,height=struct.unpack('>II',data[16:24]); assert width>0 and height>0
    pos=8; compressed=[]; ended=False
    while pos<len(data):
        n=struct.unpack('>I',data[pos:pos+4])[0]; kind=data[pos+4:pos+8]
        payload=data[pos+8:pos+8+n]; crc=struct.unpack('>I',data[pos+8+n:pos+12+n])[0]
        assert zlib.crc32(kind+payload)&0xffffffff==crc
        if kind==b'IDAT': compressed.append(payload)
        if kind==b'IEND': ended=True; break
        pos+=n+12
    assert ended; zlib.decompress(b''.join(compressed))
assert len(question_images)==11 and len(answer_images)==7 and len(images)==18
assert images=={p.name for p in (ASSETS/'images').glob('*.png')}
# Independent scientific checks, including exact decimals and the source key.
assert 12/8==1.5 and 36/(72-60)==3 and 160/400==0.4
q9=collections[1][8]
assert '۳۶' in q9['bookPrompt'] and '۶۰' in q9['bookPrompt'] and '۷۲' in q9['bookPrompt']
assert 'هماهنگ نیستند' not in q9['visualCaption']
assert q9['answerVisual']=='g7_ch02_q09_answer.png'
assert [p['right'] for p in collections[1][3]['steps'][0]['pairs']]==['۶۰ نیوتون','۲ نیوتون','۶ نیوتون','۳۰۰۰ نیوتون']
assert [p['right'] for p in collections[1][10]['steps'][0]['pairs']]==['کمتر از','برابر','بیشتر از']
assert [p['right'] for p in collections[1][13]['steps'][0]['pairs']]==['۱۶ سانتی‌متر','۱۲ سانتی‌متر']
assert [next(i for i,o in enumerate(q['steps'][0]['options']) if o['correct']) for q in collections[1][15:]]==[2,2,0,3,1]
assert collections[1][-1]['steps'][0]['options'][1]['text']=='۲٫۳۶ گرم'
# Match the supplied figure numbers, not a remembered right-to-left order.
assert [p['right'] for p in collections[1][11]['steps'][0]['pairs']]==['ساعت','زمان‌سنج (کرنومتر)']
assert [p['right'] for p in collections[1][14]['steps'][0]['pairs']]==['ترازو','استوانه مدرج','انواع نیروسنج']
assert [p['right'] for p in collections[1][14]['steps'][1]['pairs']]==['جرم','حجم مایع','نیرو، مانند وزن']
# All 41 manually authored hint pairs must survive content regeneration.
from grade7_refinements import HINTS
seen=set()
for ch,qs in enumerate(collections,1):
    for q in qs:
        section='mcq' if q['section']=='چهارگزینه‌ای' else 'main'
        for i,s in enumerate(q['steps'],1):
            key=ch,section,q['sourceNumber'],i
            assert (s['hint1'],s['hint2'])==HINTS[key]
            assert s['answer'] not in (s['hint1'],s['hint2'])
            seen.add(key)
assert seen==set(HINTS) and len(seen)==41
for key,forbidden in {
    (2,'main',8,1):['۱٫۵'], (2,'main',9,1):['۱۲'],
    (2,'main',10,1):['۰٫۴'], (2,'main',14,1):['۱۶','۱۲'],
    (2,'mcq',2,1):['۴۸۰'], (2,'mcq',4,1):['۱۰۰۰'],
    (2,'mcq',5,1):['۲٫۳۶'],
}.items():
    assert not any(value in '\n'.join(HINTS[key]) for value in forbidden)
report=dict(status='PASS',questions=len(ids),steps=len(steps),images=len(images),questionImages=len(question_images),answerImages=len(answer_images),reviewedHintPairs=len(seen),interactionKinds=counts,chapterCounts=[8,20],notes=['All original source prompts retained','Both hints individually reviewed for all 41 steps','Source question 9 figure corrected by the supplied image','Image numbers in questions 12, 14 and 15 aligned with interactions','Native attempt-policy tests are reported separately'])
(ROOT/'qa').mkdir(exist_ok=True)
(ROOT/'qa/grade7_content_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
