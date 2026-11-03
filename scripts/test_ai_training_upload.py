import os, subprocess
from build import ROOT, JDK, EXE
out=ROOT/'build/ai-training-upload-tests';out.mkdir(parents=True,exist_ok=True)
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar']))
java=ROOT/'app/src/main/java/ru/folio/notes'
subprocess.run([str(JDK/'bin'/('javac'+EXE)),'-encoding','UTF-8','-cp',cp,'-d',str(out),str(java/'AgentCrypto.java'),str(java/'AgentEndpoints.java'),str(java/'AiTrainingUpload.java'),str(ROOT/'tests/AiTrainingUploadTests.java')],check=True)
subprocess.run([str(JDK/'bin'/('java'+EXE)),'-Dfile.encoding=UTF-8','-cp',cp,'ru.folio.notes.AiTrainingUploadTests'],check=True)
