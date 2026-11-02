"""Exercise the production laser model without Android and render its light primitives on desktop."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/laser-tests';output.mkdir(parents=True,exist_ok=True)
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,ROOT/'app/src/main/java/ru/folio/notes/LaserEffect.java',ROOT/'tests/LaserTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-cp',str(output),'ru.folio.notes.LaserTests',str(ROOT/'build/laser-preview')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout+result.stderr)
    (ROOT/'dist/laser-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Laser checks failed')

if __name__=='__main__':main()
