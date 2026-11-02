"""Check production squeeze state machine without Android or a physical pen."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/squeeze-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes/SqueezeGestures.java'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,source,ROOT/'tests/SqueezeGestureTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output),'ru.folio.notes.SqueezeGestureTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/squeeze-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Squeeze checks failed')

if __name__=='__main__':main()
