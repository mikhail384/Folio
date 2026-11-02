from build import ROOT,JDK,EXE,run
import json,os,subprocess,sys,asyncio,tempfile
out=ROOT/'build/agent-cloud-tests';out.mkdir(exist_ok=True)
cp=os.pathsep.join(map(str,[out,ROOT/'build/test-deps/json-20240303.jar']))
run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-cp',cp,'-d',out,ROOT/'app/src/main/java/ru/folio/notes/AgentCrypto.java',ROOT/'tests/AgentCryptoTests.java')
run(JDK/'bin'/('java'+EXE),'-Dfile.encoding=UTF-8','-cp',cp,'ru.folio.notes.AgentCryptoTests',ROOT/'build/folio-cloud-crypto-vector.json',ROOT/'build/folio-cloud-java-envelope.json')
