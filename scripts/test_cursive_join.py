"""Regression checks for pressure-preserving personal handwriting joins."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    output=ROOT/'build/cursive-join-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',SDK/'android.jar','-d',output,
        source/'Ink.java',source/'InkGeometry.java',source/'CursiveJoin.java',ROOT/'tests/CursiveJoinTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output)+os.pathsep+str(SDK/'android.jar'),'ru.folio.notes.CursiveJoinTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    report=result.stdout+result.stderr;print(report);(ROOT/'dist/cursive-join-tests.txt').write_text(report,encoding='utf-8')
    if result.returncode:raise RuntimeError('Personal handwriting join tests failed')

if __name__=='__main__':main()
