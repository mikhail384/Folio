import os,subprocess
from build import ROOT,JDK,SDK,EXE
out=ROOT/'build/ai-learning-core-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar',SDK/'android.jar']))
source=ROOT/'app/src/main/java/ru/folio/notes'
subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','--release','8','-cp',cp,'-d',str(out),*[str(source/n) for n in ['Ink.java','InkGeometry.java','AiLearningCore.java']],str(ROOT/'tests/AiLearningCoreTests.java')],check=True)
subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.AiLearningCoreTests'],check=True)
