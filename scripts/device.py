"""Install matching updates and manage the single shell helper shared by both apps."""
from pathlib import Path
import os, re, shlex, shutil, subprocess, sys, time
ROOT=Path(__file__).resolve().parents[1]
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ADB=os.environ.get('ADB') or shutil.which('adb') or str(Path.home()/'Desktop/platform-tools/adb.exe')

def adb(*args,quiet=False):
    r=subprocess.run([ADB,*args],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if r.stdout.strip() and not quiet:print(r.stdout.strip())
    if r.stderr.strip():print(r.stderr.strip())
    if r.returncode:raise RuntimeError('ADB command failed')
    return r.stdout.strip()

def wait_for_tablet(seconds=45):
    deadline=time.monotonic()+seconds
    print('Подключите планшет по USB и разрешите отладку. Ожидаю подключение...',flush=True)
    while True:
        devices=adb('devices',quiet=True)
        online=[s for s in devices.splitlines() if '\tdevice' in s]
        if len(online)==1:return
        if len(online)>1:raise RuntimeError('Оставьте подключённым один планшет.')
        if time.monotonic()>=deadline:raise RuntimeError('Планшет не подключён. Подключите USB с разрешённой отладкой и снова запустите Start-Shared-Pen.cmd.')
        time.sleep(.5)

def confirm_bridge(seconds=8,focusflow=False):
    deadline=time.monotonic()+seconds
    while True:
        status=adb('shell','content','call','--uri','content://ru.folio.notes.pen','--method','diagnostics',quiet=True)
        connected=bool(re.search(r'\bconnected=true\b',status))
        found=bool(re.search(r'\bdevice=/dev/input/event[0-9]+\b',status))
        shared=bool(re.search(r'\bshared=true\b',status))
        focus=adb('shell','content','call','--uri','content://ru.focusflow.pen.bridge','--method','diagnostics',quiet=True) if focusflow else ''
        if connected and found and shared and (not focusflow or 'connected=true' in focus):
            print('Общий помощник подключён, устройство пера найдено. В открытом Folio команды FocusFlow приостановлены. После перезагрузки помощник нужно запустить снова.')
            return
        if time.monotonic()>=deadline:
            if connected and shared and not found:raise RuntimeError('Помощник отвечает, но перо пока не найдено. Подключите Focus Pen Pro и повторите запуск.')
            raise RuntimeError('Подключение общего помощника не подтверждено для всех выбранных приложений. Проверьте /data/local/tmp/folio-pen-shared.log и версии Folio 1.13.2 / FocusFlow 1.7.1.')
        time.sleep(.25)

def start_shared(focusflow=False):
    paths=adb('shell','pm','path','ru.folio.notes',quiet=True).splitlines()
    if not paths:raise RuntimeError('Сначала установите Folio 1.13.2.')
    path=paths[0].removeprefix('package:')
    if not path.startswith('/data/app/') or not path.endswith('.apk') or '\n' in path:raise RuntimeError('Unexpected APK path')
    template=(ROOT/'shared/penbridge/launch.sh').read_text(encoding='utf-8-sig').strip()
    result=adb('shell',template.replace('@APK@',shlex.quote(path)))
    if 'FOLIO_FOCUSFLOW_STARTED' not in result:raise RuntimeError('Общий процесс не подтвердил запуск.')
    confirm_bridge(focusflow=focusflow)

def stop_shared():
    # A stop requested from FocusFlow itself only suspends that app's commands.
    # This explicit host command stops the common daemon, after verifying its UID/name.
    processes=adb('shell','ps','-A','-o','USER,PID,ARGS',quiet=True)
    for line in processes.splitlines():
        match=re.fullmatch(r'\s*shell\s+(\d+)\s+folio-pen-shared\s*',line)
        if match:
            pid=match.group(1)
            identity=adb('shell','cat',f'/proc/{pid}/cmdline',quiet=True).replace('\0',' ').strip()
            if identity!='folio-pen-shared':raise RuntimeError('Helper process identity changed; not stopping it')
            adb('shell','kill',pid)

def main():
    mode=sys.argv[1] if len(sys.argv)>1 else 'install'
    if mode=='recover':wait_for_tablet();mode='bridge'
    devices=adb('devices');online=[s for s in devices.splitlines() if '\tdevice' in s]
    if len(online)!=1:raise RuntimeError('Подключите один планшет с разрешённой USB-отладкой.')
    if mode in ('install','install-shared'):
        adb('install','-r',str(ROOT/'dist/Folio.apk'))
        if mode=='install-shared':adb('install','-r',str(ROOT/'dist/FocusFlow.apk'))
        adb('shell','am','start','-n','ru.folio.notes/.MainActivity')
        print('Installed native SDK builds. No ADB pen helper started; use the explicit bridge command only for legacy mode.')
    elif mode=='bridge':start_shared()
    elif mode=='stop-bridge':stop_shared()
    elif mode=='status':
        adb('shell','cat','/data/local/tmp/folio-pen-shared.log')
        adb('shell','content','call','--uri','content://ru.folio.notes.pen','--method','diagnostics')
        adb('shell','content','call','--uri','content://ru.focusflow.pen.bridge','--method','diagnostics')
    else:raise RuntimeError('Use install, install-shared, recover, bridge, stop-bridge or status')
if __name__=='__main__':
    try:main()
    except Exception as e:print(str(e));sys.exit(1)
