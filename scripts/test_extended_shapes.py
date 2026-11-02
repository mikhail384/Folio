"""Check independent geometric and symbol fixtures against production contour fitting."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/extended-shapes';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/name for name in ['Ink.java','InkGeometry.java','ShapeRecognizer.java','ShapeTemplates.java','PolygonFitter.java']],ROOT/'tests/ExtendedShapeTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.ExtendedShapeTests',str(ROOT/'dist/Folio-shape-recognition.png')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout+result.stderr)
    (ROOT/'dist/extended-shape-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Extended shape checks failed')

if __name__=='__main__':main()
