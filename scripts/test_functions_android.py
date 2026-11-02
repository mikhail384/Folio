"""Test real controls with isolated fixtures and always restore the production APK."""
from device import adb, ROOT
try:
    adb('install','-r',str(ROOT/'build/embedded/Folio.apk'))
    result=adb('shell','am','instrument','-w','ru.folio.notes/ru.folio.notes.FunctionalTests')
    (ROOT/'dist/native-functional-tests.txt').write_text(result+'\n',encoding='utf-8')
    if 'PASS ' not in result or 'FAIL ' in result or 'INSTRUMENTATION_FAILED' in result:
        raise RuntimeError('Native functional checks failed')
    adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa/three-presets.png',str(ROOT/'dist/Folio-three-presets.png'))
finally:
    adb('install','-r',str(ROOT/'dist/Folio.apk'))
    adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
