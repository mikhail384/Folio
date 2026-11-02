from build import ROOT, JDK, EXE, run
import subprocess
output=ROOT/'build/command-timing-tests';output.mkdir(parents=True,exist_ok=True)
source=ROOT/'app/src/main/java/ru/folio/notes'
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,*[source/name for name in ['EventTiming.java','PenGestures.java','SmoothSpring.java']],ROOT/'tests/CommandTimingTests.java')
result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output),'ru.folio.notes.CommandTimingTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
text=result.stdout+result.stderr;print(text);(ROOT/'dist/command-timing-tests.txt').write_text(text,encoding='utf-8')
if result.returncode:raise RuntimeError('Command timing checks failed')
