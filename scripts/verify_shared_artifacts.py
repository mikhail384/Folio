"""Verify the exact APKs shipped together, signatures, versions and shared bytecode."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
from build import ROOT,BUILD,DIST,JDK,BT,EXE

def call(*args):return subprocess.run(list(map(str,args)),check=True,capture_output=True,text=True,encoding='utf-8',errors='replace').stdout

def main():
 report={'scope':'Offline verification of the delivered production APKs; no tablet connected','apps':{},'sharedClasses':{}}
 for name,package,version,code in [('Folio','ru.folio.notes','1.14.0',30),('FocusFlow','ru.focusflow.pen','1.7.1',11)]:
  apk=DIST/(name+'.apk');badging=call(BT/('aapt2'+EXE),'dump','badging',apk)
  assert "name='"+package+"'" in badging and "versionCode='"+str(code)+"'" in badging and "versionName='"+version+"'" in badging,(name,badging[:300])
  manifest=call(BT/('aapt2'+EXE),'dump','xmltree',apk,'--file','AndroidManifest.xml')
  assert 'E: instrumentation' not in manifest
  verified=call(JDK/'bin'/('java'+EXE),'-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs',apk)
  cert=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',verified).group(1)
  assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in verified and 'Verified using v3 scheme (APK Signature Scheme v3): true' in verified
  if name=='FocusFlow':assert cert=='ea853d1d0ef82bb10e4c735bcc93556f506729be367812f5f592ae5883117eaf'
  else:assert 'android.permission.INTERNET' not in badging
  with zipfile.ZipFile(apk) as z:
   dex=b''.join(z.read(n) for n in z.namelist() if re.fullmatch(r'classes[0-9]*\.dex',n))
   for klass in ['SharedPenBridge','PenOwner','ActionFence','ShellClient','BridgeLaunch','AutoStartGate','AutoAdbClient','WirelessDebugging']:assert ('Lru/folio/penbridge/'+klass+';').encode() in dex
   assert b'Lru/folio/notes/CompatibilityEraserTests;' not in dex and b'Lru/folio/notes/DeviceTests;' not in dex and b'Lru/focusflow/pen/SharedRuntimeTests;' not in dex
   if name=='FocusFlow':
    assert '.AutoAdbService' in manifest
    assert b'Lru/focusflow/pen/AutoAdbService;' in dex
    assert 'lib/arm64-v8a/libadb.so' in z.namelist() or any(n.startswith('lib/arm64-v8a/') for n in z.namelist())
    for asset in (ROOT/'integrations/focusflow/app/src/main/assets').rglob('*'):
     if asset.is_file():assert z.read('assets/'+asset.relative_to(ROOT/'integrations/focusflow/app/src/main/assets').as_posix())==asset.read_bytes()
  sha=hashlib.sha256(apk.read_bytes()).hexdigest();assert (DIST/(name+'.apk.sha256')).read_text().split()[0]==sha
  report['apps'][name]={'package':package,'version':version,'versionCode':code,'sha256':sha,'certificateSha256':cert,'bytes':apk.stat().st_size,'signatureV2V3':True,'testComponentsAbsent':True}
 common=BUILD/'classes/ru/folio/penbridge';other=BUILD/'focusflow/classes/ru/folio/penbridge'
 for f in sorted(common.glob('*.class')):
  assert f.read_bytes()==(other/f.name).read_bytes(),f.name
  report['sharedClasses'][f.name]=hashlib.sha256(f.read_bytes()).hexdigest()
 assert len(report['sharedClasses'])>=7
 (DIST/'shared-artifact-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 print('PASS: both production APK versions/signatures/assets; same shared helper bytecode; test components excluded')
 print(json.dumps({n:{'version':v['version'],'sha256':v['sha256']} for n,v in report['apps'].items()},indent=2))
if __name__=='__main__':main()
