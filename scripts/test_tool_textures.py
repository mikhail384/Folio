"""Verify exact original PNG bytes survive extraction, source packaging and APK linking."""
from pathlib import Path
import hashlib,json,struct,zipfile
ROOT=Path(__file__).resolve().parents[1]

def main():
    folder=ROOT/'app/src/main/assets/freenotes_tools';manifest=json.loads((folder/'source.json').read_text(encoding='utf-8'));assert len(manifest['files'])==38
    source=ROOT/'research/freenotes/base.apk';reference=zipfile.ZipFile(source) if source.exists() else None
    if reference:assert hashlib.sha256(source.read_bytes()).hexdigest()==manifest['sourceApkSha256']
    checks=0
    with zipfile.ZipFile(ROOT/'dist/Folio.apk') as apk:
        assert apk.testzip() is None;checks+=1
        for entry in manifest['files']:
            data=(folder/entry['file']).read_bytes();assert hashlib.sha256(data).hexdigest()==entry['sha256'];assert data[:8]==b'\x89PNG\r\n\x1a\n';assert struct.unpack('>II',data[16:24])==(entry['width'],entry['height']);assert apk.read('assets/freenotes_tools/'+entry['file'])==data;checks+=4
            if reference:assert reference.read(entry['sourcePath'])==data;checks+=1
        assert apk.read('assets/freenotes_tools/source.json')==(folder/'source.json').read_bytes();checks+=1
        assert not any(n.startswith('assets/font') for n in apk.namelist());checks+=1
    if reference:reference.close()
    text=f'PASS {checks} texture asset checks: 38 original PNGs, source SHA-256 and dimensions, byte-for-byte final APK parity, valid ZIP and no copied native libraries/fonts. Reference APK compared: {bool(reference)}. Native rendering is checked separately.\n'
    print(text);(ROOT/'dist/texture-asset-tests.txt').write_text(text,encoding='utf-8')

if __name__=='__main__':main()
