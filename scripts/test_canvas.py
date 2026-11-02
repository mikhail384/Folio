"""Check production world index, viewport and export geometry without an Android device."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/canvas-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/name for name in ['Ink.java','InkGeometry.java','CanvasViewport.java','SpatialIndex.java','StrokeChunks.java']],ROOT/'tests/CanvasTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.CanvasTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout+result.stderr)
    (ROOT/'dist/canvas-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Canvas checks failed')

if __name__=='__main__':main()
