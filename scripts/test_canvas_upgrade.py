"""Production gradient, measured ruler, naming and bounded long-stroke hit checks."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/canvas-upgrade-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    names=['Ink.java','InkGeometry.java','CanvasViewport.java','SpatialIndex.java','StrokeChunks.java','ColorDefaults.java','ColorModel.java','PaperSizes.java','NotebookNames.java','ToolColors.java','RulerGeometry.java','CompassTrace.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/n for n in names],ROOT/'tests/CanvasUpgradeTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.CanvasUpgradeTests',str(ROOT/'dist/Folio-canvas-upgrade.png')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/canvas-upgrade-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Canvas upgrade checks failed')

if __name__=='__main__':main()
