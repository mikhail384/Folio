"""Read-only route assertions with real foreground transitions; run after install-shared."""
import json,time
from device import adb,ROOT

def status(authority):return adb('shell','content','call','--uri','content://'+authority,'--method','diagnostics',quiet=True)
def wait_route(priority,seconds=4):
 deadline=time.monotonic()+seconds
 while True:
  folio=status('ru.folio.notes.pen');focus=status('ru.focusflow.pen.bridge')
  if 'shared=true' in folio and 'connected=true' in folio and 'connected=true' in focus and ('priority='+str(priority).lower()) in folio and ('folioPriority='+str(priority).lower()) in focus:
   if priority and 'owner=focusflow' in folio:raise RuntimeError('FocusFlow still owns input in Folio')
   return {'folio':folio,'focusflow':focus}
  if time.monotonic()>deadline:raise RuntimeError('Priority did not converge: '+folio+' / '+focus)
  time.sleep(.1)

def main():
 pids=adb('shell','pidof','folio-pen-shared',quiet=True).split()
 if len(pids)!=1:raise RuntimeError('Expected exactly one shared shell helper')
 report={'helperPid':pids[0]}
 try:
  adb('shell','am','start','-n','ru.folio.notes/.MainActivity',quiet=True);report['folioForeground']=wait_route(True)
  adb('shell','input','keyevent','KEYCODE_HOME',quiet=True);report['home']=wait_route(False)
  adb('shell','am','start','-n','ru.focusflow.pen/.MainActivity',quiet=True);report['focusflowForeground']=wait_route(False)
 finally:adb('shell','am','start','-n','ru.folio.notes/.MainActivity',quiet=True)
 report['folioReopened']=wait_route(True)
 report['physicalGesturesTested']=False
 (ROOT/'dist/installed-shared-pen.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 print('PASS: one helper, foreground priority and return of FocusFlow verified via device providers. Physical gestures still need testing.')
if __name__=='__main__':main()
