"""Stress Folio's production spatial index, lasso selector and streaming JSON at 100k strokes."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/canvas-load-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    dep=ROOT/'build/test-deps/json-20240303.jar'
    if not dep.exists():
        from test_document_upgrade import main as ensure_json
        ensure_json()
    names=['Ink.java','InkGeometry.java','CanvasViewport.java','SpatialIndex.java','StrokeChunks.java','LassoSelection.java','InkJsonWriter.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',dep,'-d',output,*[source/name for name in names],ROOT/'tests/CanvasLoadTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Xmx2g','-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(dep),'ru.folio.notes.CanvasLoadTests'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=600)
    report=result.stdout+result.stderr;print(report);(ROOT/'dist/canvas-load-100k.txt').write_text(report,encoding='utf-8')
    if result.returncode:raise RuntimeError('100k canvas stress test failed')

if __name__=='__main__':main()
