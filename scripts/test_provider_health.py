from build import ROOT,JDK,SDK,EXE,run
import os,subprocess
out=ROOT/'build/provider-health-tests';out.mkdir(parents=True,exist_ok=True)
source=ROOT/'shared/penbridge/src/ru/folio/penbridge';ff=ROOT/'integrations/focusflow/app/src/main/java/ru/focusflow/pen'
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar',SDK/'android.jar']))
sources=list((ROOT/'tests/shared-stubs').rglob('*.java'))+list((ROOT/'tests/provider-stubs').rglob('*.java'))+[source/'ActionFence.java']+[ff/n for n in ['PenRuntime.java','PenProvider.java','FolioPriority.java','Config.java','Gestures.java','PenGestureDetector.java','SequenceMatcher.java','ScreenPath.java']]+[ROOT/'app/src/main/java/ru/folio/notes/PenProvider.java',ROOT/'tests/FocusProviderHealthTests.java',ROOT/'tests/FolioProviderHealthTests.java']
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,*sources)
reports=[]
for name in ['ru.focusflow.pen.FocusProviderHealthTests','ru.folio.notes.FolioProviderHealthTests']:
 r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',cp,name],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=15);reports.append(r.stdout+r.stderr);print(reports[-1])
 if r.returncode:raise RuntimeError('Provider health regression: '+name)
(ROOT/'dist/provider-health-tests.txt').write_text(''.join(reports),encoding='utf-8')
