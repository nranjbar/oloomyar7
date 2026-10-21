"""Regenerate all native source layouts, then apply the separate practice review."""
from native_first_five import write as first_five
from native_chapters_six_to_ten import write as next_five
from clarify_practice_choices import write as clarify
import hashlib
import json
from pathlib import Path

def write():
    first_five();next_five();clarify()
    root=Path(__file__).resolve().parents[1]
    for name,chapters in [('native-first-five-manifest.json',range(1,6)),('native-chapters-six-to-ten-manifest.json',range(6,11))]:
        manifest=[]
        for ch in chapters:
            for q in json.loads((root/f'app/src/main/assets/content/chapter{ch}.json').read_text()):
                payload=json.dumps(dict(question=q['nativeQuestion'],answer=q['nativeAnswer']),ensure_ascii=False,sort_keys=True).encode()
                manifest.append(dict(id=q['canonicalId'],questionId=q['id'],sourceQuestion=q['sourceQuestionVisual'],sourceAnswer=q['answerVisual'],nativeSha256=hashlib.sha256(payload).hexdigest()))
        (root/'qa/source'/name).write_text(json.dumps(dict(edition=1405,questions=manifest),ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':write()
