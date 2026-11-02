"""Check the installed release, including the real Home/restart lifecycle."""
from pathlib import Path
import json,subprocess,time
from device import adb,ADB,ROOT
def snapshot(name):
    data=subprocess.run([ADB,'exec-out','screencap','-p'],capture_output=True,check=True).stdout
    (ROOT/'dist'/name).write_bytes(data)
def diagnostic():
    return adb('shell','content','call','--uri','content://ru.folio.notes.pen','--method','diagnostics',quiet=True)
adb('shell','am','start','-n','ru.folio.notes/.MainActivity',quiet=True)
time.sleep(1)
foreground=diagnostic()
snapshot('Folio-editor.png')
adb('shell','input','keyevent','KEYCODE_HOME',quiet=True)
time.sleep(.9)
background=diagnostic()
if 'active=false' not in background or 'state=observe' not in background or 'rotationService=Вращение подключено' in background:
    raise RuntimeError('Pen bridge did not release after Home: '+background)
adb('shell','am','force-stop','ru.folio.notes',quiet=True)
adb('shell','am','start','-n','ru.folio.notes/.MainActivity',quiet=True)
time.sleep(1)
reopened=diagnostic()
snapshot('Folio-reopened.png')
report={'foreground':foreground,'afterHome':background,'afterRestart':reopened,'releaseAfterHome':True,'nativeRotationPausedAfterHome':True}
(ROOT/'dist/release-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
