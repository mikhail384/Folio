"""Dependency-free Android build. Uses ANDROID_HOME/JAVA_HOME or the local portable SDK."""
from pathlib import Path
import hashlib, os, subprocess, sys, zipfile
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
ROOT = Path(__file__).resolve().parents[1]
PORTABLE = ROOT.parent.parent/'2026-10-19'/'xiaomi-pad-7-xiaomi-focus-pen'/'.tools'
TOOLS = Path(os.environ.get('FOLIO_ANDROID_TOOLS', PORTABLE))
JDK = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else next((TOOLS/'jdk').glob('jdk-*'))
SDK_HOME = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
SDK = Path(SDK_HOME)/'platforms'/'android-35' if SDK_HOME else next((TOOLS/'platform').glob('android-*'))
BT = max((Path(SDK_HOME)/'build-tools').iterdir()) if SDK_HOME else next((TOOLS/'build-tools').glob('android-*'))
BUILD, DIST, SRC = ROOT/'build', ROOT/'dist', ROOT/'app/src/main'
EXE = '.exe' if os.name == 'nt' else ''
def run(*args):
    r = subprocess.run([str(a) for a in args], cwd=ROOT, env={**os.environ, 'JAVA_HOME':str(JDK)}, capture_output=True, text=True, encoding='utf-8', errors='replace')
    if r.stdout.strip(): print(r.stdout.strip())
    if r.stderr.strip(): print(r.stderr.strip())
    if r.returncode: raise RuntimeError('Command failed: '+str(args[0]))
def build(embedded_tests=False, handwriting_preview=False):
    if embedded_tests and handwriting_preview: raise RuntimeError('Choose one test packaging mode')
    if hashlib.sha256((ROOT/'app/libs/PenEngine-release_0.3.2.aar').read_bytes()).hexdigest()!='b3c0c9ff6c58a9df686ce1ec2585910b3991b6b867184d046be74eebf53a6053':raise RuntimeError('PenEngine SDK checksum mismatch')
    output=BUILD/'embedded' if embedded_tests else BUILD/'handwriting-preview' if handwriting_preview else DIST
    output.mkdir(parents=True,exist_ok=True)
    for p in [BUILD/'classes', BUILD/'dex', BUILD/'gen', DIST, ROOT/'.tools', SRC/'assets']: p.mkdir(parents=True, exist_ok=True)
    for folder, pattern in [('classes','*.class'),('dex','*.dex')]:
        for old in (BUILD/folder).rglob(pattern):
            if not old.resolve().is_relative_to(BUILD.resolve()): raise RuntimeError('Unsafe output path')
            old.unlink()
    run(BT/('aapt2'+EXE), 'compile', '--dir', SRC/'res', '-o', BUILD/'resources.zip')
    # AGP 8 takes the namespace from build.gradle; standalone aapt2 needs package.
    manifest=(SRC/'AndroidManifest.xml').read_text(encoding='utf-8').replace('<manifest ', '<manifest package="ru.folio.notes" ', 1)
    if embedded_tests:manifest=manifest.replace('</application>','<provider android:name="ru.folio.notes.BackupTestProvider" android:authorities="ru.folio.notes.qa.documents" android:exported="true" android:permission="android.permission.MANAGE_DOCUMENTS" android:grantUriPermissions="true" /></application>').replace('</manifest>','<instrumentation android:name="ru.folio.notes.DeviceTests" android:targetPackage="ru.folio.notes" android:label="Temporary Folio checks" /><instrumentation android:name="ru.folio.notes.DeviceTests$Textures" android:targetPackage="ru.folio.notes" android:label="Folio texture checks" /><instrumentation android:name="ru.folio.notes.EnhancementTests" android:targetPackage="ru.folio.notes" android:label="Folio materials and content checks" /><instrumentation android:name="ru.folio.notes.RadialOrderTests" android:targetPackage="ru.folio.notes" android:label="Folio radial ordering checks" /><instrumentation android:name="ru.folio.notes.FunctionalTests" android:targetPackage="ru.folio.notes" android:label="Folio color and motion checks" /><instrumentation android:name="ru.folio.notes.PerformanceTests" android:targetPackage="ru.folio.notes" android:label="Folio performance checks" /><instrumentation android:name="ru.folio.notes.CompatibilityEraserTests" android:targetPackage="ru.folio.notes" android:label="Folio compatibility and marker eraser checks" /><instrumentation android:name="ru.folio.notes.HandwritingSetupDeviceTests" android:targetPackage="ru.folio.notes" android:label="Folio handwriting setup checks" /><instrumentation android:name="ru.folio.notes.PersonalHandwritingPreviewDeviceTests" android:targetPackage="ru.folio.notes" android:label="Folio personal handwriting preview" /></manifest>')
    elif handwriting_preview:manifest=manifest.replace('</manifest>','<instrumentation android:name="ru.folio.notes.PersonalHandwritingPreviewDeviceTests" android:targetPackage="ru.folio.notes" android:label="Folio personal handwriting preview" /></manifest>')
    (BUILD/'AndroidManifest.xml').write_text(manifest,encoding='utf-8')
    run(BT/('aapt2'+EXE), 'link', '-o', BUILD/'unsigned.apk', '--manifest', BUILD/'AndroidManifest.xml', '-I', SDK/'android.jar', '--java', BUILD/'gen', '-A', SRC/'assets', BUILD/'resources.zip')
    library=ROOT/'app/libs/tesseract4android-4.9.0.aar'
    with zipfile.ZipFile(library) as aar:(BUILD/'ocr-classes.jar').write_bytes(aar.read('classes.jar'))
    with zipfile.ZipFile(ROOT/'app/libs/PenEngine-release_0.3.2.aar') as aar:(BUILD/'penengine-classes.jar').write_bytes(aar.read('classes.jar'))
    sources=list((SRC/'java').rglob('*.java'))+list((ROOT/'shared/penbridge/src').rglob('*.java'))+list((BUILD/'gen').rglob('*.java'))
    if embedded_tests:sources.extend([ROOT/'tests/DeviceTests.java',ROOT/'tests/EnhancementTests.java',ROOT/'tests/BackupTestProvider.java',ROOT/'tests/PerformanceTests.java',ROOT/'tests/FunctionalTests.java',ROOT/'tests/RadialOrderTests.java',ROOT/'tests/CompatibilityEraserTests.java',ROOT/'tests/HandwritingSetupDeviceTests.java',ROOT/'tests/PersonalHandwritingPreviewDeviceTests.java'])
    elif handwriting_preview:sources.append(ROOT/'tests/PersonalHandwritingPreviewDeviceTests.java')
    run(JDK/'bin'/('javac'+EXE), '-encoding','UTF-8','--release','8','-classpath',str(SDK/'android.jar')+os.pathsep+str(BUILD/'ocr-classes.jar')+os.pathsep+str(BUILD/'penengine-classes.jar'),'-d',BUILD/'classes',*sources)
    with zipfile.ZipFile(BUILD/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
        for f in (BUILD/'classes').rglob('*.class'): z.write(f,f.relative_to(BUILD/'classes').as_posix())
    run(JDK/'bin'/('java'+EXE),'-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',SDK/'android.jar','--min-api','26','--output',BUILD/'dex',BUILD/'classes.jar',BUILD/'ocr-classes.jar',BUILD/'penengine-classes.jar')
    with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
        for f in (BUILD/'dex').glob('*.dex'):z.write(f,f.name)
        with zipfile.ZipFile(library) as aar:
            for name in aar.namelist():
                if name.startswith('jni/') and name.endswith('.so'):z.writestr('lib/'+name[4:],aar.read(name))
    run(BT/('zipalign'+EXE),'-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
    key=ROOT/'.tools/folio-local.jks'
    if not key.exists():run(JDK/'bin'/('keytool'+EXE),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','folio','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Folio Local','-noprompt')
    run(JDK/'bin'/('java'+EXE),'-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','folio','--ks-pass','pass:android','--out',output/'Folio.apk',BUILD/'aligned.apk')
    run(JDK/'bin'/('java'+EXE),'-jar',BT/'lib/apksigner.jar','verify','--verbose',output/'Folio.apk')
    digest=hashlib.sha256((output/'Folio.apk').read_bytes()).hexdigest()
    (output/'Folio.apk.sha256').write_text(digest+'  Folio.apk\n',encoding='ascii')
    print('APK: '+str(output/'Folio.apk'))
if __name__=='__main__':build()
