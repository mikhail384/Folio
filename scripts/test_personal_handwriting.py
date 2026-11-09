"""Regression checks for writer-calibrated vector handwriting layout."""
from build import ROOT,JDK,SDK,EXE,run
import os,subprocess

def main():
    classes=ROOT/'build/classes'
    if not classes.exists():
        raise RuntimeError('Build Folio before running the handwriting geometry checks')
    output=ROOT/'build/personal-handwriting-tests'
    output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'tests/PersonalHandwritingTests.java'
    classpath=str(classes)+os.pathsep+str(SDK/'android.jar')
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',classpath,'-d',output,source)
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output)+os.pathsep+classpath,'ru.folio.notes.PersonalHandwritingTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    report=result.stdout+result.stderr
    print(report)
    (ROOT/'build/personal-handwriting-tests.txt').write_text(report,encoding='utf-8')
    if result.returncode:
        raise RuntimeError('Personal handwriting geometry checks failed')

if __name__=='__main__':main()
