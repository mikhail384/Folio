"""Exercise production notebook naming with real temporary files; no tablet required."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    out=ROOT/'build/notebook-name-tests';out.mkdir(parents=True,exist_ok=True)
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',out,
        ROOT/'app/src/main/java/ru/folio/notes/NotebookNames.java',ROOT/'tests/NotebookNameTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(out),
        'ru.folio.notes.NotebookNameTests',str(out)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
    report=result.stdout+result.stderr;print(report)
    (ROOT/'dist/notebook-name-tests.txt').write_text(report,encoding='utf-8')
    if result.returncode:raise RuntimeError('Notebook naming checks failed')

if __name__=='__main__':main()
