from build import ROOT,JDK,SDK,EXE,run
import os,subprocess
out=ROOT/'build/agent-tests';out.mkdir(parents=True,exist_ok=True)
json=ROOT/'build/test-deps/json-20240303.jar'
source=ROOT/'app/src/main/java/ru/folio/notes'
cp=os.pathsep.join(map(str,[out,json,SDK/'android.jar']))
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,*[source/n for n in ['Ink.java','InkGeometry.java','AgentInk.java']],ROOT/'tests/AgentInkTests.java')
r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.AgentInkTests'],capture_output=True,text=True,encoding='utf-8');print(r.stdout+r.stderr)
(ROOT/'dist/agent-ink-tests.txt').write_text(r.stdout+r.stderr,encoding='utf-8')
if r.returncode:raise RuntimeError('Agent ink tests failed')
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,*list((ROOT/'tests/agent-stubs').rglob('*.java')),source/'AgentCrypto.java',source/'AgentCloud.java',source/'AgentBridge.java',ROOT/'tests/AgentBridgeTests.java')
r=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.AgentBridgeTests'],capture_output=True,text=True,encoding='utf-8',timeout=30);print(r.stdout+r.stderr)
with (ROOT/'dist/agent-ink-tests.txt').open('a',encoding='utf-8') as report:report.write(r.stdout+r.stderr)
if r.returncode:raise RuntimeError('Agent LAN checks failed')
