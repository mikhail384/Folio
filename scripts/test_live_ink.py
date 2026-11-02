"""Check production incremental stroke geometry and timestamped roll history."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/live-ink-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/name for name in ['Ink.java','InkGeometry.java','StrokeCursor.java','RotationHistory.java']],ROOT/'tests/LiveInkTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.LiveInkTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/live-ink-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Live ink tests failed')

if __name__=='__main__':main()
