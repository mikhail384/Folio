"""Routing plus real FocusFlow runtime; deterministic Android transport, no device claims."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess
out=ROOT/'build/shared-tests';out.mkdir(parents=True,exist_ok=True)
source=ROOT/'shared/penbridge/src/ru/folio/penbridge'
ff=ROOT/'integrations/focusflow/app/src/main/java/ru/focusflow/pen'
json=ROOT/'build/test-deps/json-20240303.jar'
if not json.is_file():raise RuntimeError('Run scripts/test_document_upgrade.py to fetch its pinned JSON-java dependency first')
cp=os.pathsep.join(map(str,[out,json,SDK/'android.jar']))
sources=[source/n for n in ['PenOwner.java','ActionFence.java','BridgeLaunch.java']]+[ff/n for n in ['PenRuntime.java','FolioPriority.java','Config.java','Gestures.java','PenGestureDetector.java','SequenceMatcher.java','ScreenPath.java']]+list((ROOT/'tests/shared-stubs').rglob('*.java'))+[ROOT/'tests/SharedPenTests.java',ROOT/'tests/SharedRuntimeTests.java',ROOT/'integrations/focusflow/tests/GestureTest.java',ROOT/'integrations/focusflow/tests/AndroidLogicTest.java']
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,*sources)
reports=[]
for name in ['ru.folio.penbridge.SharedPenTests','ru.focusflow.pen.SharedRuntimeTests','GestureTest','AndroidLogicTest']:
 r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',cp,name],capture_output=True,text=True,encoding='utf-8',errors='replace')
 reports.append(r.stdout+r.stderr);print(reports[-1])
 if r.returncode:raise RuntimeError('Shared checks failed: '+name)
# Guard the USB and in-app launchers against divergence, including shell quoting.
for apk in ['/data/app/a/base.apk',"/data/app/space and ' quote/$safe`/base.apk"]:
 r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.penbridge.SharedPenTests',apk],capture_output=True,text=True,encoding='utf-8',check=True)
 quoted="'"+apk.replace("'","'\"'\"'")+"'"
 template=(ROOT/'shared/penbridge/launch.sh').read_text(encoding='utf-8-sig').strip().replace('@APK@',quoted)
 if r.stdout!=template:raise AssertionError('USB/wireless shell launchers differ')
reports.append('PASS: USB and wireless ADB launch commands identical, including quoted APK paths\n')
(ROOT/'dist/shared-pen-tests.txt').write_text(''.join(reports),encoding='utf-8')
