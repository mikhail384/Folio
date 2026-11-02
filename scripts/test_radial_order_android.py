"""Use isolated test fixtures, then restore the signed production APK."""
from device import adb,ROOT
try:
    adb('install','-r',str(ROOT/'build/embedded/Folio.apk'))
    result=adb('shell','am','instrument','-w','ru.folio.notes/ru.folio.notes.RadialOrderTests')
    (ROOT/'dist/native-radial-order-tests.txt').write_text(result+'\n',encoding='utf-8')
    if 'PASS ' not in result or 'FAIL ' in result or 'INSTRUMENTATION_FAILED' in result:
        raise RuntimeError('Radial order checks failed')
    adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa/radial-order.png',str(ROOT/'dist/Folio-radial-order.png'))
finally:
    adb('install','-r',str(ROOT/'dist/Folio.apk'))
    adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
