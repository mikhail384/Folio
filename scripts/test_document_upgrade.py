"""Run production JSON persistence against pinned JSON-java, without an Android runtime."""
from build import ROOT,JDK,EXE,run
import os,subprocess,urllib.request,hashlib

def main():
    output=ROOT/'build/document-upgrade-tests';output.mkdir(parents=True,exist_ok=True)
    dep=ROOT/'build/test-deps/json-20240303.jar';dep.parent.mkdir(parents=True,exist_ok=True)
    if not dep.exists():
        url='https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar'
        data=urllib.request.urlopen(url,timeout=30).read()
        expected=urllib.request.urlopen(url+'.sha1',timeout=30).read().decode().strip()
        if hashlib.sha1(data).hexdigest()!=expected:raise RuntimeError('JSON-java artifact digest mismatch')
        dep.write_bytes(data)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    names=['Ink.java','InkGeometry.java','CanvasViewport.java','SpatialIndex.java','StrokeChunks.java','PaperSizes.java','InkJsonWriter.java']
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',dep,'-d',output,*[source/n for n in names],ROOT/'tests/DocumentUpgradeTests.java',ROOT/'tests/ContentModelTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(dep),'ru.folio.notes.DocumentUpgradeTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/document-upgrade-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Document upgrade checks failed')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(output)+os.pathsep+str(dep),'ru.folio.notes.ContentModelTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/content-model-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Content model checks failed')

if __name__=='__main__':main()
