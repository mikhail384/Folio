"""Verify original stroke PNGs, pinned offline OCR and its native libraries in the signed APK."""
from pathlib import Path
import hashlib,json,zipfile,struct
from contextlib import nullcontext
ROOT=Path(__file__).resolve().parents[1]
def main():
    checks=0;manifest=json.loads((ROOT/'app/src/main/assets/freenotes_strokes/source.json').read_text(encoding='utf-8'))
    reference=ROOT/'research/freenotes/base.apk'
    with (zipfile.ZipFile(reference) if reference.exists() else nullcontext()) as original,zipfile.ZipFile(ROOT/'dist/Folio.apk') as apk:
        assert len(manifest['files'])==6;checks+=1
        for entry in manifest['files']:
            name='assets/freenotes_strokes/'+entry['file'];data=(ROOT/'app/src/main'/name).read_bytes()
            assert hashlib.sha256(data).hexdigest()==entry['sha256'];assert apk.read(name)==data;assert struct.unpack('>II',data[16:24])==(entry['width'],entry['height']);checks+=3
            if original:assert original.read(entry['sourcePath'])==data;checks+=1
        models=json.loads((ROOT/'app/src/main/assets/ocr/source.json').read_text(encoding='utf-8'))
        for entry in models:
            data=apk.read('assets/ocr/'+entry['file']);assert hashlib.sha256(data).hexdigest()==entry['sha256'];checks+=1
        source=json.loads((ROOT/'app/libs/source.json').read_text(encoding='utf-8'));library=ROOT/'app/libs'/source['artifact'];assert hashlib.sha256(library.read_bytes()).hexdigest()==source['sha256'];checks+=1
        with zipfile.ZipFile(library) as aar:
            for name in aar.namelist():
                if name.startswith('jni/') and name.endswith('.so'):assert apk.read('lib/'+name[4:])==aar.read(name);checks+=1
        assert apk.read('assets/licenses/Apache-2.0.txt');assert apk.read('assets/licenses/NOTICE.txt');checks+=2
        assert apk.testzip() is None;checks+=1
    text=f'PASS {checks} material/OCR asset checks: 6 original Freenotes stroke PNGs copied exactly, model SHA-256, pinned open source OCR AAR and all 16 native libraries match, licenses present and signed APK ZIP valid.\n'
    print(text);(ROOT/'dist/material-asset-tests.txt').write_text(text,encoding='utf-8')
if __name__=='__main__':main()
