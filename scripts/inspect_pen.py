"""Collect only dedicated Focus Pen input events, never other keyboard input."""
from pathlib import Path
import subprocess,sys,time
from device import ADB
folder=Path(__file__).resolve().parents[1]/'dist'
commands=[['shell','getevent','-lt','/dev/input/event18'],['shell','getevent','-lt','/dev/input/event19'],['logcat','-v','brief','-s','FolioPen:I']]
processes=[subprocess.Popen([ADB,*c],stdout=subprocess.PIPE,stderr=subprocess.STDOUT) for c in commands]
time.sleep(int(sys.argv[1]) if len(sys.argv)>1 else 50)
chunks=[]
for c,p in zip(commands,processes):
    p.kill();data,_=p.communicate();chunks.append((' '.join(c)+'\n').encode()+data)
data=b'\n'.join(chunks)
(folder/'pen-events.txt').write_bytes(data)
print(data.decode('utf-8',errors='replace'))
