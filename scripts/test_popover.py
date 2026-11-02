"""Check the exact card motion and frame lifecycle; generate a clearly labelled desktop motion model."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/popover-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,source/'PopoverMotion.java',source/'FrameClock.java',ROOT/'tests/desktop/android/view/Choreographer.java',ROOT/'tests/PopoverMotionTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-cp',str(output),'ru.folio.notes.PopoverMotionTests',str(ROOT/'build/popover-preview')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout+result.stderr)
    (ROOT/'dist/popover-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Popover checks failed')

if __name__=='__main__':main()
