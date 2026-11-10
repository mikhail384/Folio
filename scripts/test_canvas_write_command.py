import tempfile
import subprocess
from pathlib import Path
from build import ROOT,JDK,EXE

source=ROOT/'app/src/main/java/ru/folio/notes/CanvasWriteCommand.java'
test=ROOT/'tests/CanvasWriteCommandTests.java'
with tempfile.TemporaryDirectory(prefix='folio-canvas-command-') as folder:
    out=Path(folder)
    subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','-d',str(out),str(source),str(test)],check=True)
    subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',str(out),'ru.folio.notes.CanvasWriteCommandTests'],check=True)
