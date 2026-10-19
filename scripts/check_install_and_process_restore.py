"""Verify stable-key APK update and local checkpoint restoration on a real emulator.

Called after the normal connected UI suite. Uses the previous source-built review APK.
No credentials, backend, network, or user data is needed. Fixtures exist only on the test device.
"""
import json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/build/persistence-verification';OUT.mkdir(parents=True,exist_ok=True)
APP='com.oloomyar.app.grade7';RUNNER=f'{APP}.test/androidx.test.runner.AndroidJUnitRunner'

def run(*args,input=None):
    p=subprocess.run(args,input=input,text=True,capture_output=True,check=True,timeout=180)
    return p.stdout+p.stderr

def test(method):
    output=run('adb','shell','am','instrument','-w','-r','-e','timeout_msec','120000','-e','class',f'com.oloomyar.app.Grade7PersistenceUiTest#{method}',RUNNER)
    (OUT/f'{method}.txt').write_text(output)
    assert 'OK (1 test)' in output and 'FAILURES!!!' not in output and 'INSTRUMENTATION_FAILED' not in output,output

def install(path):
    assert path.exists(),path
    output=run('adb','install','-r',str(path));assert 'Success' in output,output

baseline=ROOT/'app/build/upgrade/grade7_0.4.0.apk'
current=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
test_apk=ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
run('adb','uninstall',APP) if 'package:'+APP in run('adb','shell','pm','list','packages',APP).splitlines() else None
install(baseline)
run('adb','shell','am','start','-W','-n',f'{APP}/com.oloomyar.app.MainActivity')
run('adb','shell','am','force-stop',APP)
qs=json.loads((ROOT/'app/src/main/assets/content/chapter1.json').read_text());q=qs[0];legacy=qs[1]
values=['دانش','کنجکاوی','فناوری'];step=q['steps'][0]['id']
draft=dict(questionPracticeVersion=3,answers={step:values},checked={step:values},incorrectSubmissions=[[values]],shownHints=1,revealed=False,shuffleSeed=7413,scrollIndex=1,scrollOffset=0)
xml=ET.Element('map')
ET.SubElement(xml,'string',name='draft_'+q['id']).text=json.dumps(draft,ensure_ascii=False)
for key,value in [('done_'+legacy['id'],True),('perfect_'+legacy['id'],False),('mistake_'+legacy['id'],True)]:ET.SubElement(xml,'boolean',name=key,value=str(value).lower())
raw=ET.tostring(xml,encoding='unicode',xml_declaration=True)
run('adb','shell','run-as',APP,'mkdir','-p','shared_prefs')
run('adb','shell','run-as',APP,'sh','-c',"'cat > shared_prefs/workbook_1405_grade7_chapter1.xml'",input=raw)
# Installation must replace the APK while retaining the old data directory.
install(current);install(test_apk)
test('upgradeKeepsLegacyCompletionSelectionsAndLockedHints')
test('prepareCheckpointForRealProcessTermination')
run('adb','shell','am','start','-W','-n',f'{APP}/com.oloomyar.app.MainActivity')
run('adb','shell','input','keyevent','KEYCODE_HOME')
pid_before=run('adb','shell','pidof',APP).strip();assert pid_before
run('adb','shell','am','force-stop',APP)
pid_after=subprocess.run(['adb','shell','pidof',APP],capture_output=True,text=True).stdout.strip();assert not pid_after
run('adb','shell','am','start','-W','-n',f'{APP}/com.oloomyar.app.MainActivity')
pid_restored=run('adb','shell','pidof',APP).strip();assert pid_restored and pid_restored != pid_before
test('restoreCheckpointAfterRealProcessTermination')
report=dict(status='PASS',oldVersion='0.4.0',newVersion='0.5.0',installReplacement=True,oldCompletionPreserved=True,
    draftSelectionsHintsAndSeedPreserved=True,viewportPreserved=True,answerStayedLocked=True,bookmarksAndDisplaySettingsPreserved=True,
    pidBefore=pid_before,pidAfterStop=pid_after,pidRestored=pid_restored,tests=3)
(OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
