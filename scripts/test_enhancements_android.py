from pathlib import Path
import sys,shlex
sys.path.insert(0,'scripts')
from device import adb,ROOT
try:
 adb('install','-r',str(ROOT/'build/embedded/Folio.apk'))
 result=adb('shell','am','instrument','-w','ru.folio.notes/ru.folio.notes.EnhancementTests')
 (ROOT/'dist/native-enhancement-tests.txt').write_text(result+'\n',encoding='utf-8')
 if 'PASS ' not in result or 'FAIL ' in result or 'INSTRUMENTATION_FAILED' in result:raise RuntimeError('Native enhancements failed')
 for name in ['materials-and-tools.png','workshop.png','ocr-test.png']:
  adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa/'+name,str(ROOT/'dist'/('Folio-'+name)))
finally:
 adb('install','-r',str(ROOT/'dist/Folio.apk'))
 adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
