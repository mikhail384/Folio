from build import ROOT,JDK,EXE,run
import subprocess
def main():
    out=ROOT/'build/stylus-compatibility-tests';out.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',out,
        *list((ROOT/'tests/stylus-stubs').rglob('*.java')),
        *[source/n for n in ['StylusSamples.java','StylusButtons.java','StylusDevices.java']],ROOT/'tests/StylusCompatibilityTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',str(out),'ru.folio.notes.StylusCompatibilityTests'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
    report=result.stdout+result.stderr;print(report);(ROOT/'dist/stylus-compatibility-tests.txt').write_text(report,encoding='utf-8')
    if result.returncode:raise RuntimeError('Stylus compatibility regression')
if __name__=='__main__':main()
