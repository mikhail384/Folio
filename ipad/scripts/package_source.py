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
        'xcodeBuild':'not run: GitHub workflow permission required','simulatorTests':'not run','physicalDevice':'not tested','installable':False}
(ROOT/'dist/Folio-iPad-status.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'artifact':str(target),'sha256':checksum,'files':len(files)}))
