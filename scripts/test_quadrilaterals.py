"""Production quadrilateral constraints and pen feedback arbitration on the desktop."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/quadrilateral-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    names=['Ink.java','InkGeometry.java','ShapeRecognizer.java','ShapeTemplates.java','PolygonFitter.java','PenGestures.java','PenHapticPulse.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/name for name in names],ROOT/'tests/QuadrilateralTests.java',ROOT/'tests/PenFeedbackTests.java')
    for test,report in [('QuadrilateralTests','quadrilateral-tests.txt'),('PenFeedbackTests','pen-feedback-tests.txt')]:
        result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.'+test],capture_output=True,text=True,encoding='utf-8',errors='replace')
        text=result.stdout+result.stderr;print(text);(ROOT/'dist'/report).write_text(text,encoding='utf-8')
        if result.returncode:raise RuntimeError(test+' failed')

if __name__=='__main__':main()
