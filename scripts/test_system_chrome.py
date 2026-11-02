"""Regress transient status-bar layout jumps using the production window policy."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/chrome-tests';output.mkdir(parents=True,exist_ok=True)
    stubs=list((ROOT/'tests/chrome-stubs').rglob('*.java'))
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,*stubs,ROOT/'app/src/main/java/ru/folio/notes/SystemChrome.java',ROOT/'tests/SystemChromeTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output),'ru.folio.notes.SystemChromeTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/system-chrome-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('System bar checks failed')

if __name__=='__main__':main()
