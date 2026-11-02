"""Run only read-only native tool artwork checks, then always restore the ordinary release APK."""
from pathlib import Path
import sys,shlex
from device import adb,ROOT

def main():
    online=[line for line in adb('devices',quiet=True).splitlines() if '\tdevice' in line]
    if len(online)!=1:raise RuntimeError('One authorized tablet is required')
    installed=False
    try:
        adb('install','-r',str(ROOT/'build/embedded/Folio.apk'));installed=True
        result=adb('shell','am','instrument','-w',shlex.quote('ru.folio.notes/ru.folio.notes.DeviceTests$Textures'))
        (ROOT/'dist/native-texture-tests.txt').write_text(result+'\n',encoding='utf-8')
        if 'native Freenotes texture checks' not in result or 'PASS ' not in result or 'FAIL ' in result or 'INSTRUMENTATION_FAILED' in result:raise RuntimeError('Native texture checks failed')
        adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa/freenotes-textures.png',str(ROOT/'dist/Folio-freenotes-textures.png'))
    finally:
        if installed:
            adb('install','-r',str(ROOT/'dist/Folio.apk'))
            adb('shell','am','start','-n','ru.folio.notes/.MainActivity')

if __name__=='__main__':main()
