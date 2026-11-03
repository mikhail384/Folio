import os,subprocess
from build import ROOT,JDK,SDK,EXE
out=ROOT/'build/ai-learning-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,SDK/'android.jar']))
src=ROOT/'app/src/main/java/ru/folio/notes/AiLearningPermissions.java'
test=ROOT/'tests/AiLearningPermissionsTests.java'
subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','--release','8','-cp',cp,'-d',str(out),str(src),str(test)],check=True)
subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.AiLearningPermissionsTests'],check=True)
