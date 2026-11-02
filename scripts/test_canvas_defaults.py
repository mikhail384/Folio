"""Production blank-canvas defaults and preferences scope; desktop memory transport only."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/canvas-default-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    names=['Ink.java','InkGeometry.java','ColorDefaults.java','PaperSizes.java','CanvasDefaults.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/n for n in names],ROOT/'tests/CanvasDefaultsTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.CanvasDefaultsTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/canvas-default-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Canvas defaults regression failed')

if __name__=='__main__':main()
