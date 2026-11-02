"""Validate the native picker's shared color math/data and export a labelled desktop model."""
from build import ROOT,JDK,EXE,run
import subprocess

def main():
    output=ROOT/'build/color-tests';output.mkdir(parents=True,exist_ok=True)
    source=ROOT/'app/src/main/java/ru/folio/notes'
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-d',output,source/'ColorModel.java',source/'ColorDefaults.java',ROOT/'tests/ColorPickerTests.java')
    result=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-cp',str(output),'ru.folio.notes.ColorPickerTests',str(ROOT/'dist/Folio-color-palette.png')],capture_output=True,text=True,encoding='utf-8',errors='replace')
    text=result.stdout+result.stderr;print(text);(ROOT/'dist/color-tests.txt').write_text(text,encoding='utf-8')
    if result.returncode:raise RuntimeError('Color checks failed')

if __name__=='__main__':main()
