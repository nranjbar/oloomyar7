"""Latest owner corrections: source sections, work calculations and literal key.

Run after all older scientific/distractor passes. Original PDF fields stay
immutable. Only 8/6 and 10/21 require fresh authorization for changed controls.
"""
from pathlib import Path
import copy
import hashlib
import json
from owner_v11_presentation import refresh_manifests
from review_practice_distractors import protected_content

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'app/src/main/assets/content'
LEDGER = ROOT / 'qa/source/native-editorial-revisions-v14.json'
CROPS = ['g7_ch08_q05', 'g7_ch08_q06', 'g7_ch08_q07', 'g7_ch10_q09']

def native_hash(q):
    return hashlib.sha256(json.dumps(dict(question=q['nativeQuestion'], answer=q['nativeAnswer']),
                                    ensure_ascii=False, sort_keys=True).encode()).hexdigest()

def write():
    chapters = {c: json.loads((CONTENT / f'chapter{c}.json').read_text()) for c in range(1, 16)}
    by_id = {q['canonicalId']: q for qs in chapters.values() for q in qs}
    before = copy.deepcopy(by_id)
    previous = json.loads(LEDGER.read_text()) if LEDGER.exists() else None

    q = by_id['g7_ch08_q06']
    q['nativeQuestion'][0]['text'] = q['nativeQuestion'][0]['text'].replace('۱٫۳', '1.3')
    s = q['steps'][0]
    s['pairs'] = [p for p in s['pairs'] if p['left'] != 'ارتفاع به متر']
    assert len(s['pairs']) == 1 and s['pairs'][0]['right'] == '۶۵ ژول'
    s['hint1'] = 'ارتفاع کمد دادهٔ مسئله است؛ در این جابه‌جایی عمودی، مسافت حرکت جسم را از همان داده بخوان.'
    q['practiceRevision'] = 1

    # Labels are separate RTL tokens within an LTR equation, and the numerical
    # substitution remains one LTR run. A Persian word cannot reverse the sum.
    for cid in ['g7_ch08_q05', 'g7_ch08_q06', 'g7_ch08_q07']:
        for block in by_id[cid]['nativeAnswer']:
            if block['type'] == 'math' and len(block['parts']) == 1:
                numbers = block['parts'][0]['text'].split(' = ', 2)[2]
                block['parts'] = [dict(text=t) for t in ['کار', '=', 'نیرو', '×', 'جا به جایی', '=', numbers]]

    q = by_id['g7_ch10_q09']
    p = q['steps'][0]['pairs'][4]
    p['right'] = 'آهن < آلومینیم < مس'
    p['choices'] = [p['right'], 'آلومینیم < آهن < مس', 'مس < آلومینیم < آهن']
    q['steps'][0].setdefault('choiceAliases', {})['مس > آلومینیم > آهن'] = p['right']

    for cid in CROPS:
        q = by_id[cid]
        q['nativeAnswer'] = [b for b in q['nativeAnswer'] if b.get('asset') != q['answerVisual']]
        q['nativeAnswer'].append(dict(type='figure', asset=q['answerVisual'], text='پاسخ اصلی کتاب‌کار'))

    q = by_id['g7_ch10_q21']
    q.pop('sourceReviewNote', None)
    q.pop('answerReviewNote', None)
    q['practiceUsesPrintedKey'] = True
    q['practiceRevision'] = 1
    s = q['steps'][0]
    values = [
        ('هر چه جسم تیره‌تر باشد، میزان تابش گرمایی آن بیشتر است.',
         'هر چه جسم روشن‌تر باشد، میزان تابش گرمایی آن بیشتر است.',
         'تیرگی و روشنی جسم در میزان تابش گرمایی آن تفاوتی ایجاد نمی‌کند.'),
        ('بخاری تیره تابش گرمایی بیشتری دارد و محیط را بیشتر گرم می‌کند.',
         'بخاری تیره تابش گرمایی کمتری دارد و محیط را کمتر گرم می‌کند.',
         'رنگ تیره فقط ظاهر بخاری را عوض می‌کند و بر تابش آن اثری ندارد.'),
        ('رنگ سفید تابش گرمایی کمتری دارد و گرمای آب آبگرم‌کن کمتر هدر می‌رود.',
         'رنگ سفید تابش گرمایی بیشتری دارد و گرمای آب را زودتر به محیط می‌دهد.',
         'رنگ سفید تابش گرمایی بیشتری دارد و همین باعث کمتر شدن اتلاف گرما می‌شود.'),
        ('مهران؛ چای پررنگ تیره‌تر است، تابش گرمایی بیشتری دارد و زودتر سرد می‌شود.',
         'سینا؛ چای کم‌رنگ روشن‌تر است، تابش گرمایی بیشتری دارد و زودتر سرد می‌شود.',
         'مهران؛ چای پررنگ تابش گرمایی کمتری دارد و به همین دلیل زودتر سرد می‌شود.'),
        ('رنگ سفید تابش گرمایی کمی دارد؛ گرمای بدن خرس کمتر به محیط منتشر می‌شود و هدر نمی‌رود.',
         'رنگ سفید تابش گرمایی زیادی دارد و گرمای بدن خرس را بیشتر به محیط می‌دهد.',
         'رنگ سفید با بیشتر کردن تابش گرمایی، مانع هدر رفتن گرمای بدن می‌شود.'),
    ]
    for pair, choices in zip(s['pairs'], values):
        pair['right'], pair['choices'] = choices[0], list(choices)
    s['choiceAliases'] = {}  # Never reinterpret an old scientific answer as the newly authorized key.
    s['hint1'] = 'ارتفاع ستون‌های نمودار را با هم مقایسه کن؛ نمودار چه ارتباطی میان رنگ و مقدار تابش نشان می‌دهد؟'
    s['hint2'] = 'از رابطهٔ نشان‌داده‌شده در نمودار برای هر قسمت استفاده کن؛ بیرون رفتن بیشتر گرما چه اثری بر گرم کردن محیط یا سرد شدن جسم دارد؟'

    for c, qs in chapters.items():
        (CONTENT / f'chapter{c}.json').write_text(json.dumps(qs, ensure_ascii=False, indent=2) + '\n')
    revisions = []
    for cid in CROPS:
        old_hash = native_hash(before[cid])
        new_hash = native_hash(by_id[cid])
        if previous:
            recorded = next(r for r in previous['revisions'] if r['id'] == cid)
            assert old_hash in [recorded['beforeNativeSha256'], recorded['afterNativeSha256']], cid
            old_hash = recorded['beforeNativeSha256']
        revisions.append(dict(id=cid, beforeNativeSha256=old_hash, afterNativeSha256=new_hash,
            reasons=['Owner requests LTR work/comparison display with a secondary exact source-answer crop; 8/6 height is the given decimal 1.3 m, not an extra question.']))
    ledger = dict(baseline='7d2f60e9cc29682dc3ea78969f7bd26f5f67ff90',
        authorization='Owner correction 2026-10-23: expose both workbook sections, hide MCQ header commentary, LTR math and exact reference crops, remove redundant height control, follow literal 10/21 key without advanced corrections.',
        revisions=revisions, answerCrops=[dict(question=cid, asset=by_id[cid]['answerVisual']) for cid in CROPS],
        practiceReset=['g7_ch08_q06', 'g7_ch10_q21'])
    LEDGER.write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + '\n')
    refresh_manifests()

    for name in ['first_ten_practice_review.json', 'chapters_10_15_practice_review.json']:
        path = ROOT / 'qa' / name
        report = json.loads(path.read_text())
        for entry in report['reviewed']:
            q = by_id[entry['id']]
            entry['practiceUsesPrintedKey'] = q.get('practiceUsesPrintedKey', True)
            entry['sourceReviewNote'] = q.get('sourceReviewNote')
            for target, step in zip(entry['steps'], q['steps']):
                target['controls' if name.startswith('chapters_') else 'choices'] = step.get('options') or step.get('pairs') or step.get('items')
                if 'hint1' in target:
                    target.update(hint1=step['hint1'], hint2=step['hint2'])
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    path = ROOT / 'qa/practice-distractors-review.json'
    report = json.loads(path.read_text())
    for entry in report['authored']:
        if entry['question'] == 'g7_ch10_q21':
            p = by_id[entry['question']]['steps'][entry['step']]['pairs'][entry['row']]
            entry.update(right=p['right'], choices=p['choices'])
    report['ownerFollowupV14'] = dict(ledger='qa/source/native-editorial-revisions-v14.json', originalKeyRestored='g7_ch10_q21')
    report['protectedContentSha256'] = hashlib.sha256(json.dumps(protected_content(chapters), ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('Owner v14: four exact answer crops, 1.3 m given, no height control, literal 10/21 key.')

if __name__ == '__main__':
    write()
