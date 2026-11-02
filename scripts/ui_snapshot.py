"""Capture Android UI using a visible text or accessibility label, without changing preferences."""
import subprocess,sys,time,re,xml.etree.ElementTree as ET
from device import adb,ADB,ROOT
def click(label):
    adb('shell','uiautomator','dump','/sdcard/folio-ui.xml',quiet=True)
    xml=adb('shell','cat','/sdcard/folio-ui.xml',quiet=True)
    root=ET.fromstring(xml)
    if not any(n.get('package')=='ru.folio.notes' for n in root.iter('node')):raise RuntimeError('Folio is not the foreground app')
    nodes=[n for n in root.iter('node') if n.get('content-desc')==label or n.get('text')==label]
    if not nodes:raise RuntimeError('Visible control not found: '+label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2),quiet=True)
    time.sleep(.35)
def capture(name):
    data=subprocess.run([ADB,'exec-out','screencap','-p'],capture_output=True,check=True).stdout
    (ROOT/'dist'/name).write_bytes(data)
if __name__=='__main__':
    for label in sys.argv[2:]:click(label)
    capture(sys.argv[1])
