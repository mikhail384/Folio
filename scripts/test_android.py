from pathlib import Path
import os,sys,zipfile
from build import ROOT,JDK,SDK,BT,BUILD,EXE,run
from device import adb
TEST=ROOT/'tests'
def main():
    folder=BUILD/'tests';folder.mkdir(parents=True,exist_ok=True)
    classes=folder/'classes';classes.mkdir(exist_ok=True)
    dex=folder/'dex';dex.mkdir(exist_ok=True)
    run(BT/('aapt2'+EXE),'link','-o',folder/'unsigned.apk','--manifest',TEST/'AndroidManifest.xml','-I',SDK/'android.jar')
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-classpath',os.pathsep.join([str(SDK/'android.jar'),str(BUILD/'classes')]),'-d',classes,TEST/'DeviceTests.java')
    with zipfile.ZipFile(folder/'tests.jar','w',zipfile.ZIP_DEFLATED) as z:
        for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    run(JDK/'bin'/('java'+EXE),'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',SDK/'android.jar','--classpath',BUILD/'classes.jar','--min-api','26','--output',dex,folder/'tests.jar')
    with zipfile.ZipFile(folder/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
        for f in dex.glob('*.dex'):z.write(f,f.name)
    run(BT/('zipalign'+EXE),'-f','4',folder/'unsigned.apk',folder/'aligned.apk')
    run(JDK/'bin'/('java'+EXE),'-jar',BT/'lib/apksigner.jar','sign','--ks',ROOT/'.tools/folio-local.jks','--ks-key-alias','folio','--ks-pass','pass:android','--out',folder/'tests.apk',folder/'aligned.apk')
    adb('install','-r',str(folder/'tests.apk'))
    result=adb('shell','am','instrument','-w','ru.folio.notes.tests/ru.folio.notes.DeviceTests')
    (ROOT/'dist/device-tests.txt').write_text(result,encoding='utf-8')
    if 'PASS: ' not in result or 'FAIL' in result:raise RuntimeError('Device checks failed')
    adb('pull','/sdcard/Android/data/ru.folio.notes/files/qa',str(ROOT/'dist/qa'))
if __name__=='__main__':main()
