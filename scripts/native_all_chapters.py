"""Regenerate all fifteen native chapters with the reviewed practice and owner art."""
from native_first_five import write as first_five
from native_chapters_six_to_ten import write as next_five
from native_chapters_eleven_to_fifteen import write as last_five
from clarify_practice_choices import write as clarify
from apply_owner_art import write as owner_art
from native_biology_diagrams import write as biology_diagrams
from editorial_science_review import write as science_review
import hashlib
import json
from pathlib import Path

def write():
    first_five();next_five();clarify();last_five();biology_diagrams();review_ten();owner_art();science_review()
    root=Path(__file__).resolve().parents[1]
    for name,chapters in [('native-first-five-manifest.json',range(1,6)),('native-chapters-six-to-ten-manifest.json',range(6,11)),('native-chapters-eleven-to-fifteen-manifest.json',range(11,16))]:
        manifest=[]
        for ch in chapters:
            for q in json.loads((root/f'app/src/main/assets/content/chapter{ch}.json').read_text()):
                payload=json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()
                manifest.append(dict(id=q['canonicalId'],questionId=q['id'],sourceQuestion=q['sourceQuestionVisual'],sourceAnswer=q['answerVisual'],nativeSha256=hashlib.sha256(payload).hexdigest()))
        (root/'qa/source'/name).write_text(json.dumps(dict(edition=1405,questions=manifest),ensure_ascii=False,indent=2)+'\n')
    qs=[q for c in range(10,16)for q in json.loads((root/f'app/src/main/assets/content/chapter{c}.json').read_text())]
    inventory=[dict(id=q['canonicalId'],questionId=q['id'],nativeBlocks=len(q['nativeQuestion']),
        practiceUsesPrintedKey=q.get('practiceUsesPrintedKey',True),sourceReviewNote=q.get('sourceReviewNote'),
        steps=[dict(id=s['id'],kind=s['kind'],hint1=s['hint1'],hint2=s['hint2'],
            controls=s.get('pairs')or s.get('options')or s.get('items'),reusable=s.get('allowTokenReuse',True))for s in q['steps']])for q in qs]
    (root/'qa/chapters_10_15_practice_review.json').write_text(json.dumps(dict(scope='chapters 10–15',
        questions=len(qs),policy='Separate scientific practice and literal source keys; reusable banks; explicit whole-question checks and reveal',reviewed=inventory),ensure_ascii=False,indent=2)+'\n')

def review_ten():
    root=Path(__file__).resolve().parents[1];path=root/'app/src/main/assets/content/chapter10.json';qs=json.loads(path.read_text())
    by_id={q['sourceNumber']:q for q in qs if q['section']=='تمرین‌های اصلی'}
    by_id[21]['steps'][0]['hint2']='در کاربرد وسیله، هدف از نگه‌داشتن یا انتقال انرژی را مشخص کن. برای چای و بدن جانور، بررسی کن که آیا رنگِ دیده‌شده به‌تنهایی همهٔ مسیرهای تبادل گرما را تعیین می‌کند.'
    by_id[20]['steps'][0]['pairs'][0]['choices']=['دمای جسم و رنگ جسم','فقط جرم جسم، مستقل از دما و ویژگی سطح','فقط نام ماده، مستقل از دما و ویژگی سطح']
    for q in qs:
        if q['section']=='چهارگزینه‌ای':q['steps'][0]['prompt']='گزینهٔ درست را با توجه به متن سؤال انتخاب کن.'
    path.write_text(json.dumps(qs,ensure_ascii=False,indent=2)+'\n')
    report_path=root/'qa/first_ten_practice_review.json';report=json.loads(report_path.read_text())
    for row in report['reviewed']:
        if row['id'].startswith('g7_ch10_'):
            q=next(q for q in qs if q['canonicalId']==row['id'])
            row['steps']=[dict(id=s['id'],kind=s['kind'],choices=s.get('options')or s.get('pairs')or s.get('items'),reusable=s.get('allowTokenReuse',True))for s in q['steps']]
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
