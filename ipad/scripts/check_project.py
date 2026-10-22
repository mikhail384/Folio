"""Local structural checks. These are deliberately not reported as an iPad build."""
from pathlib import Path
import plistlib, json, xml.etree.ElementTree as ET, subprocess, sys

ROOT=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(ROOT/'scripts/project.py')],check=True)
info=plistlib.loads((ROOT/'Folio/Info.plist').read_bytes())
assert info['UIApplicationSceneManifest']['UIApplicationSupportsMultipleScenes'] is False
assert len(info['UISupportedInterfaceOrientations~ipad'])==4
assert info['CFBundleDocumentTypes'][0]['LSHandlerRank']=='Owner'
project=(ROOT/'Folio.xcodeproj/project.pbxproj').read_text(encoding='utf-8')
for folder in ['Folio','FolioTests','FolioUITests']:
    for source in (ROOT/folder).glob('*.swift'):
        assert source.name in project, str(source)
assert project.count('isa = PBXNativeTarget;')==3
assert 'version = 0.9.20' in project
assert 'IPHONEOS_DEPLOYMENT_TARGET = "17.0"' in project
ET.parse(ROOT/'Folio.xcodeproj/xcshareddata/xcschemes/Folio.xcscheme')
assets=ROOT/'Folio/Assets.xcassets/AppIcon.appiconset'
for entry in json.loads((assets/'Contents.json').read_text())['images']:
    assert (assets/entry['filename']).stat().st_size>1000
for source in (ROOT/'Folio').glob('*.swift'):
    assert 'fatalError()' not in source.read_text(encoding='utf-8').split('required init?')[0], f'Unexpected runtime placeholder {source}'
assert (ROOT/'THIRD-PARTY-NOTICES.md').stat().st_size>900
print('Project structure, plist, scheme, sources, icon and dependency checks passed. Xcode compilation not performed here.')
