from build import ROOT,JDK,SDK,EXE,run
import os,subprocess
out=ROOT/'build/auto-adb-tests';out.mkdir(parents=True,exist_ok=True)
source=ROOT/'shared/penbridge/src/ru/folio/penbridge';ff=ROOT/'integrations/focusflow/app/src/main/java/ru/focusflow/pen'
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar',SDK/'android.jar']))
stubs=[f for f in (ROOT/'tests/shared-stubs').rglob('*.java') if f.as_posix().split('shared-stubs/')[-1]!='ru/focusflow/pen/LocalAdb.java']+list((ROOT/'tests/auto-adb-stubs').rglob('*.java'))
sources=stubs+[source/n for n in ['AutoStartGate.java','WirelessDebugging.java','BridgeLaunch.java','ActionFence.java']]+[ff/n for n in ['LocalAdb.java','AdbIdentity.java','PenRuntime.java','FolioPriority.java','Config.java','Gestures.java','PenGestureDetector.java','SequenceMatcher.java','ScreenPath.java']]+[ROOT/'tests/AutoStartGateTests.java',ROOT/'tests/AutoAdbTests.java']
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,*sources)
reports=[]
for name in ['ru.folio.penbridge.AutoStartGateTests','ru.focusflow.pen.AutoAdbTests']:
 r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-Dtest.files='+str(out/'identity'),'-cp',cp,name],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=35)
 reports.append(r.stdout+r.stderr);print(reports[-1])
 if r.returncode:raise RuntimeError('Automatic startup checks failed: '+name)
(ROOT/'dist/auto-adb-tests.txt').write_text(''.join(reports),encoding='utf-8')

