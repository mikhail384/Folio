from pathlib import Path
import subprocess,sys,time
from device import ADB,ROOT
folder=ROOT/'research';folder.mkdir(exist_ok=True)
p=subprocess.Popen([ADB,'shell','getevent','-lt','/dev/input/event13'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
time.sleep(int(sys.argv[1]) if len(sys.argv)>1 else 20)
p.kill();raw,_=p.communicate();text=raw.decode('utf-8',errors='replace');lines=[line for line in text.splitlines() if 'ABS_BRAKE' in line]
(folder/'rotation-events.txt').write_text('\n'.join(lines),encoding='utf-8')
print('\n'.join(lines[-80:]))
