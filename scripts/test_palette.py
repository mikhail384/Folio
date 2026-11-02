"""Test production radial geometry and hover motion without Android."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/palette-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,source/'RadialPalette.java',source/'SmoothSpring.java',ROOT/'tests/PaletteTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(output),'ru.folio.notes.PaletteTests'],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout+result.stderr)
    (ROOT/'dist/palette-tests.txt').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError('Palette checks failed')

if __name__=='__main__':main()
