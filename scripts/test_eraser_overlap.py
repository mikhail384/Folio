"""Production eraser geometry/damage, independent composition and reproducible desktop benchmark."""
from build import ROOT,JDK,SDK,EXE,run
import subprocess,os

def main():
    out=ROOT/'build/eraser-overlap-tests';out.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',out,
        *[source/n for n in ['Ink.java','InkGeometry.java','CanvasViewport.java','SpatialIndex.java','StrokeChunks.java','EraserDamage.java']],ROOT/'tests/EraserOverlapTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',str(out)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.EraserOverlapTests',str(ROOT/'dist/eraser-overlap-benchmark.txt')],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
    report=result.stdout+result.stderr;print(report);(ROOT/'dist/eraser-overlap-tests.txt').write_text(report,encoding='utf-8')
    if result.returncode:raise RuntimeError('Eraser overlap regression')

if __name__=='__main__':main()
