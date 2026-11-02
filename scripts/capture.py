from pathlib import Path
import subprocess,sys,time
from device import ADB
out=Path(__file__).resolve().parents[1]/'dist'/(sys.argv[1] if len(sys.argv)>1 else 'screen.png')
if '--launch' in sys.argv:
    subprocess.run([ADB,'shell','am','start','-n','ru.folio.notes/.MainActivity'],check=True,capture_output=True)
    time.sleep(1)
result=subprocess.run([ADB,'exec-out','screencap','-p'],capture_output=True)
result.check_returncode()
out.write_bytes(result.stdout)
print(out)
