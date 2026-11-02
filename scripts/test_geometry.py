"""Run shared ink and popover geometry checks on the desktop JDK, without a tablet."""
from pathlib import Path
from build import ROOT, JDK, SDK, EXE, run
import os,subprocess

def main():
    output=ROOT/'build/geometry';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,*[source/name for name in ['Ink.java','InkGeometry.java','PopoverLayout.java']],ROOT/'tests/GeometryTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.GeometryTests',str(ROOT/'dist/Folio-ink-geometry.png')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout,result.stderr)
    (ROOT/'dist/geometry-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Geometry checks failed')

if __name__=='__main__':main()
