from pathlib import Path
import os,subprocess
from build import ROOT,JDK,SDK,EXE
out=ROOT/'build/handwriting-training-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar',SDK/'android.jar']))
source=ROOT/'app/src/main/java/ru/folio/notes/HandwritingTraining.java'
test=ROOT/'tests/HandwritingTrainingTests.java'
subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','--release','8','-cp',cp,'-d',str(out),str(source),str(test)],check=True)
subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.HandwritingTrainingTests'],check=True)
