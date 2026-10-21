"""Validate original provenance, measured full-resolution derivatives and gated role."""
from pathlib import Path
from PIL import Image
import hashlib,json
R=Path(__file__).resolve().parents[1];A=R/'app/src/main/assets';m=json.loads((R/'qa/source/owner-art-2026-10-21.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
questions={q['canonicalId']:q for ch in range(1,16)for q in json.loads((A/f'content/chapter{ch}.json').read_text())}
assert len(m['scientific'])==125 and len(m['ui'])==7
assert json.loads((A/'content/owner-scientific-art.json').read_text())==m
for e in m['scientific']+m['ui']:
 p=A/'images'/e['asset'];src=R/e['originalRepositoryPath'] if e.get('originalRepositoryPath') else None;im=Image.open(p).convert('RGBA')
 if src is not None:assert sha(src.read_bytes())==e['sourceSha256']
 else:assert e.get('deliveredSource') and len(e['sourceSha256'])==64
 assert sha(p.read_bytes())==e['webpSha256'] and im.size==(e['width'],e['height'])
 assert sha(im.tobytes())==e['decodedRgbaSha256']
 if e in m['scientific']:
  enc=e['encoding'];assert enc['mode']in['lossless','lossy']
  assert e['sourceRgbaSha256'] and e['encodedBytes']==p.stat().st_size
  assert enc['lumaSsim']>=.995
  if enc['mode']=='lossless':assert e['sourceRgbaSha256']==e['decodedRgbaSha256']
  if src is not None:
   original=Image.open(src).convert('RGBA');assert original.size==im.size and sha(original.tobytes())==e['sourceRgbaSha256']
   if enc['mode']=='lossless':assert original.tobytes()==im.tobytes()
   else:
    from prepare_owner_art_v10 import luma_ssim
    assert luma_ssim(original,im)>=.995
    assert original.getchannel('A').tobytes()==im.getchannel('A').tobytes()
  q=questions[e['question']]
  if e['role']=='answer':assert q['answerStudyVisual']==e['asset']
  if e['applyFullQuestionFigure']:assert [b['asset']for b in q['nativeQuestion']if b['type']=='figure']==[e['asset']]
carbon=questions['g7_ch03_q15']['nativeQuestion'];assert carbon[1]['type']=='figure' and carbon[1]['asset']=='g7_ch03_q15_question.webp'
assert carbon[1]['asset'].startswith('g7_') and next(i for i,b in enumerate(carbon)if b['type']=='cloze')>1
assert sum(bool(q.get('answerStudyVisual'))for q in questions.values())==71
assert len({e['asset']for e in m['scientific']})==125
for cid in ['g7_ch04_q13','g7_ch06_q10']:assert any(e['question']==cid and e['role']=='question' and e['applyFullQuestionFigure']for e in m['scientific'])
assert 'g-'in ''.join(b.get('text','')for b in questions['g7_ch04_q13']['nativeQuestion'])
assert questions['g7_ch07_q14']['answerReviewNote'] and questions['g7_ch06_q07']['answerReviewNote']
assert m['deferredOutsideCurrentChapterScope']==['question_images/g7_final_q12_question.png']
print(json.dumps(dict(status='PASS',ownerScientificImages=125,fullResolution=True,minMeasuredLumaSsim=min(e['encoding']['lumaSsim']for e in m['scientific']),losslessScientificImages=sum(e['encoding']['mode']=='lossless'for e in m['scientific']),uiAssets=7,completeCarbonMapBeforeControls=True,gatedAnswerIllustrations=71,deferredImages=len(m['deferredOutsideCurrentChapterScope']))))
