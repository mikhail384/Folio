import os,subprocess
from build import ROOT,JDK,SDK,EXE
out=ROOT/'build/ai-learning-queue-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar',SDK/'android.jar']))
src=ROOT/'app/src/main/java/ru/folio/notes/AiLearningQueue.java'
subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','--release','8','-cp',cp,'-d',str(out),str(src),str(ROOT/'tests/AiLearningQueueTests.java')],check=True)
subprocess.run([str(JDK/'bin'/('java'+EXE)),'-cp',cp,'ru.folio.notes.AiLearningQueueTests'],check=True)
