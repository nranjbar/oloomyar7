"""User-authorized cleanup, only after successful checks on Grade 7 main.

Preflight every remote branch before deleting any. Never discard a commit not
contained in main, and use a lease so a concurrent branch update is protected.
No Grade 9 repository or ref is touched.
"""
import os,subprocess

def run(*args):
    return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT).strip()

assert os.environ.get('GITHUB_REPOSITORY')=='nranjbar/oloomyar7'
assert os.environ.get('GITHUB_EVENT_NAME')=='push'
assert os.environ.get('GITHUB_REF')=='refs/heads/main'
heads={ref.removeprefix('refs/heads/'):sha for sha,ref in
    (line.split()for line in run('git','ls-remote','--heads','origin').splitlines())}
main=run('git','rev-parse','HEAD')
assert heads['main']==main,'Main advanced; let its newer successful workflow perform cleanup'
targets={branch:sha for branch,sha in heads.items()if branch!='main'}
for branch,sha in targets.items():
    run('git','fetch','--no-tags','origin','refs/heads/'+branch)
    assert run('git','rev-parse','FETCH_HEAD')==sha,'Branch changed during preflight'
    subprocess.run(['git','merge-base','--is-ancestor',sha,main],check=True)
for branch,sha in targets.items():
    result=run('git','push','--force-with-lease=refs/heads/'+branch+':'+sha,'origin',':refs/heads/'+branch)
    print(result)
remaining=run('git','ls-remote','--heads','origin').splitlines()
assert len(remaining)==1 and remaining[0].split()[1]=='refs/heads/main'
print('Grade 7 branch cleanup complete: only main remains; all deleted tips were already in main.')
