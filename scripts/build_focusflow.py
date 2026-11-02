"""Build the matching FocusFlow update, retaining the user's original signing identity."""
from pathlib import Path
import hashlib, json, os, re, zipfile
from build import ROOT, BUILD, DIST, JDK, SDK, BT, EXE, PORTABLE, run
SRC=ROOT/'integrations/focusflow/app/src/main'
LIBS=ROOT/'integrations/focusflow/libs'
OUT=BUILD/'focusflow'
EXPECTED='ea853d1d0ef82bb10e4c735bcc93556f506729be367812f5f592ae5883117eaf'

def build():
    if hashlib.sha256((ROOT/'app/libs/PenEngine-release_0.3.2.aar').read_bytes()).hexdigest()!='b3c0c9ff6c58a9df686ce1ec2585910b3991b6b867184d046be74eebf53a6053':raise RuntimeError('PenEngine SDK checksum mismatch')
    key=Path(os.environ.get('FOCUSFLOW_KEYSTORE',PORTABLE/'focusflow-local.jks'))
    if not key.is_file():raise RuntimeError('Original FocusFlow signing key required. Set FOCUSFLOW_KEYSTORE; no replacement key will be generated.')
    lock=json.loads((LIBS.parent/'dependencies.lock.json').read_text(encoding='utf-8-sig'))
    for name,meta in lock.items():
        if hashlib.sha256((LIBS/name).read_bytes()).hexdigest()!=meta['sha256']:raise RuntimeError('Dependency checksum mismatch: '+name)
    for name in ('classes','dex','gen','libs'): (OUT/name).mkdir(parents=True,exist_ok=True)
    for folder,pattern in [('classes','*.class'),('dex','*.dex')]:
        for old in (OUT/folder).rglob(pattern):
            if not old.resolve().is_relative_to(OUT.resolve()):raise RuntimeError('Unsafe output path')
            old.unlink()
    DIST.mkdir(exist_ok=True)
    run(BT/('aapt2'+EXE),'compile','--dir',SRC/'res','-o',OUT/'resources.zip')
    run(BT/('aapt2'+EXE),'link','-o',OUT/'unsigned.apk','--manifest',SRC/'AndroidManifest.xml','-I',SDK/'android.jar','--java',OUT/'gen','-A',SRC/'assets',OUT/'resources.zip')
    with zipfile.ZipFile(ROOT/'app/libs/PenEngine-release_0.3.2.aar') as sdk:(OUT/'libs/penengine.jar').write_bytes(sdk.read('classes.jar'))
    jars=[OUT/'libs/penengine.jar']
    for library in sorted(LIBS.iterdir()):
        if library.suffix=='.aar':
            target=OUT/'libs'/(library.stem+'.jar')
            with zipfile.ZipFile(library) as aar: target.write_bytes(aar.read('classes.jar'))
            jars.append(target)
        elif library.suffix=='.jar':jars.append(library)
    sources=list((SRC/'java').rglob('*.java'))+list((ROOT/'shared/penbridge/src').rglob('*.java'))+list((OUT/'gen').rglob('*.java'))
    run(JDK/'bin'/('javac'+EXE),'-encoding','UTF-8','--release','8','-classpath',os.pathsep.join(map(str,[SDK/'android.jar',*jars])),'-d',OUT/'classes',*sources)
    with zipfile.ZipFile(OUT/'classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
        for f in (OUT/'classes').rglob('*.class'):jar.write(f,f.relative_to(OUT/'classes').as_posix())
    run(JDK/'bin'/('java'+EXE),'-Xmx1536m','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',SDK/'android.jar','--min-api','26','--output',OUT/'dex',OUT/'classes.jar',*jars)
    with zipfile.ZipFile(OUT/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
        for dex in (OUT/'dex').glob('*.dex'):apk.write(dex,dex.name)
        for library in LIBS.glob('*.aar'):
            with zipfile.ZipFile(library) as aar:
                for name in aar.namelist():
                    if name.startswith('jni/arm64-v8a/') and name.endswith('.so'):apk.writestr('lib/'+name[4:],aar.read(name))
    run(BT/('zipalign'+EXE),'-f','4',OUT/'unsigned.apk',OUT/'aligned.apk')
    apk=DIST/'FocusFlow.apk'
    run(JDK/'bin'/('java'+EXE),'-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','focusflow','--ks-pass','pass:android','--out',apk,OUT/'aligned.apk')
    import subprocess
    verified=subprocess.run([str(JDK/'bin'/('java'+EXE)),'-jar',str(BT/'lib/apksigner.jar'),'verify','--print-certs',str(apk)],capture_output=True,text=True,check=True).stdout
    if 'certificate SHA-256 digest: '+EXPECTED not in verified:raise RuntimeError('FocusFlow signature differs from the attached original APK')
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    (DIST/'FocusFlow.apk.sha256').write_text(digest+'  FocusFlow.apk\n',encoding='ascii')
    print('FocusFlow 1.7.1 signed with original certificate: '+EXPECTED)
    print('APK: '+str(apk))
if __name__=='__main__':build()
