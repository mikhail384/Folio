"""Independent desktop references for production infinite ruler, touch geometry and styled compass."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/guide-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    names=['Ink.java','InkGeometry.java','RulerGeometry.java','RulerManipulation.java','GuideTools.java','CompassTrace.java','ColorDefaults.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/n for n in names],ROOT/'tests/GuideTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.GuideTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/guide-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Drawing-aid regression failed')

if __name__=='__main__':main()
