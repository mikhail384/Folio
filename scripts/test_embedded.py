"""Run temporary self-instrumentation, then restore the normal signed release."""
from build import build,BUILD,ROOT
from device import adb
def main():
    build(embedded_tests=True)
    try:
        adb('install','-r',str(BUILD/'embedded/Folio.apk'))
        result=adb('shell','am','instrument','-w','ru.folio.notes/ru.folio.notes.DeviceTests')
        (ROOT/'dist/device-tests.txt').write_text(result,encoding='utf-8')
        adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa',str(ROOT/'dist/qa'))
        if 'PASS: ' not in result or 'FAIL' in result:raise RuntimeError('Device checks failed')
    finally:
        build()
        adb('install','-r',str(ROOT/'dist/Folio.apk'))
        adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
if __name__=='__main__':main()
