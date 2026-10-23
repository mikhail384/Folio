"""Package a reviewable Xcode project; no signing keys, tools or extracted APK assets."""
from pathlib import Path
import zipfile,hashlib,json
ROOT=Path(__file__).resolve().parents[2]
target=ROOT/'dist/Folio-iPad-source.zip'
files=[]
for p in sorted((ROOT/'ipad').rglob('*')):
    if p.is_file() and not any(x in p.parts for x in ['__pycache__','xcuserdata','DerivedData']) and p.suffix not in ['.pyc','.p12','.mobileprovision','.key']:
        files.append(p)
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
    for p in files:z.write(p,p.relative_to(ROOT).as_posix())
with zipfile.ZipFile(target) as z:
    assert z.testzip() is None
    assert 'ipad/Folio.xcodeproj/project.pbxproj' in z.namelist()
    assert 'ipad/Folio/Assets.xcassets/AppIcon.appiconset/AppIcon.png' in z.namelist()
checksum=hashlib.sha256(target.read_bytes()).hexdigest()
(ROOT/'dist/Folio-iPad-source.zip.sha256').write_text(checksum+'  Folio-iPad-source.zip\n',encoding='ascii')
report={'version':'0.1.0','artifact':'Folio-iPad-source.zip','sha256':checksum,'files':len(files),'sourceArchiveVerified':True,
        'localChecks':['project structure','Info.plist and shared scheme','all Swift grammar parsed','ZIP integrity'],
        'xcodeBuild':'unverified: a completed build receipt is required','simulatorTests':'unverified','physicalDevice':'not tested','installable':False}
verified=ROOT/'dist/Folio-iPad-build-checks.json'
if verified.exists():
    build=json.loads(verified.read_text(encoding='utf-8'))
    hashes=build.get('sourceGitBlobHashes',{})
    changed=[]
    for relative,expected in hashes.items():
        source=ROOT/relative
        if not source.exists(): changed.append(relative);continue
        data=source.read_bytes()
        if source.suffix!='.png':data=data.replace(b'\r\n',b'\n')
        digest=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
        if digest!=expected:changed.append(relative)
    if hashes and not changed:
        report.update(xcodeBuild=build.get('xcodeBuild','unknown'),simulatorTests=build.get('simulatorTests','unknown'),buildReport='Folio-iPad-build-checks.json',installable=False)
    else:report.update(unverifiedSourceChanges=changed,xcodeBuild='unverified: source differs from the recorded build')
(ROOT/'dist/Folio-iPad-status.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'artifact':str(target),'sha256':checksum,'files':len(files)}))
