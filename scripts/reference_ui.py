"""Read-only screenshot collection from the installed reference apps."""
from pathlib import Path
import subprocess,time,sys
from device import ADB,ROOT
folder=ROOT/'research';folder.mkdir(exist_ok=True)
package=sys.argv[1] if len(sys.argv)>1 else 'ai.note.notepad.memo.journal.freenote'
subprocess.run([ADB,'shell','monkey','-p',package,'-c','android.intent.category.LAUNCHER','1'],capture_output=True,check=True)
time.sleep(1.2)
out=folder/(sys.argv[2] if len(sys.argv)>2 else 'freenotes-current.png')
out.write_bytes(subprocess.run([ADB,'exec-out','screencap','-p'],capture_output=True,check=True).stdout)
print(out)
