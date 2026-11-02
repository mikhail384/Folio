"""Produce a secret-free plugin bundle, source snapshot, preview and release receipt."""
from pathlib import Path
import hashlib,json,zipfile,sys
ROOT=Path(__file__).resolve().parents[1];PLUGIN=ROOT/'integrations/folio-plugin';DIST=ROOT/'dist'
sys.path.insert(0,str(PLUGIN));from handwriting import layout
from PIL import Image,ImageDraw,ImageFont

def source_file(p):
    excluded={'.venv','__pycache__','build','handwriting-profiles','node_modules','dist','.git','.wrangler','.sites-runtime','.vinext','.next','outputs','.codex'}
    return p.is_file() and not any(x in excluded for x in p.relative_to(ROOT).parts) and p.suffix not in {'.pyc','.jks','.keystore','.tsbuildinfo'} and p.name not in {'connection.json','config.local.json','next-env.d.ts'} and not p.name.startswith(('.env','.dev.vars'))

def package():
    DIST.mkdir(exist_ok=True)
    files=[p for p in PLUGIN.rglob('*') if p.is_file() and not any(x in {'.venv','__pycache__','handwriting-profiles'} for x in p.relative_to(PLUGIN).parts) and p.suffix!='.pyc' and p.name not in {'connection.json','config.local.json'}]
    market=json.loads((ROOT/'.agents/plugins/marketplace.json').read_text(encoding='utf-8'));market['plugins'][0]['source']['path']='./folio-plugin'
    install=r'''@echo off
cd /d "%~dp0"
python folio-plugin\setup.py --deps-only
if errorlevel 1 goto done
codex plugin marketplace add "%~dp0"
if errorlevel 1 goto done
codex plugin add folio-canvas@folio-local
:done
pause
'''
    with zipfile.ZipFile(DIST/'Folio-Canvas-Plugin-0.2.0.zip','w',zipfile.ZIP_DEFLATED) as z:
        for p in files:z.write(p,'Folio-Plugin/folio-plugin/'+p.relative_to(PLUGIN).as_posix())
        z.writestr('Folio-Plugin/.agents/plugins/marketplace.json',json.dumps(market,ensure_ascii=False,indent=2))
        z.writestr('Folio-Plugin/Install-Codex-Plugin.cmd',install.replace('\r\n','\n').replace('\r','\n').replace('\n','\r\n'))
        z.writestr('Folio-Plugin/README.md',(PLUGIN/'README.md').read_text(encoding='utf-8'))
    roots=['app','shared','scripts','tests','integrations/folio-plugin','integrations/folio-cloud','integrations/focusflow','.agents']
    with zipfile.ZipFile(DIST/'Folio-Android-Plugin-source.zip','w',zipfile.ZIP_DEFLATED,strict_timestamps=False) as z:
        for directory in roots:
            for p in (ROOT/directory).rglob('*'):
                if source_file(p):z.write(p,'Folio-source/'+p.relative_to(ROOT).as_posix())
        for name in ['README.md','build.gradle','settings.gradle','.gitignore']:z.write(ROOT/name,'Folio-source/'+name)
    preview=Image.new('RGB',(1400,760),'#f5f3ee');draw=ImageDraw.Draw(preview)
    for y in range(120,740,42):draw.line((70,y,1330,y),fill='#dfe2df',width=1)
    for text,x,y,height,color in [('Привет! Это Folio.',90,170,36,'#28344b'),('Пишу прямо на твоём холсте.',90,290,28,'#6979de'),('2 + 2 = 4     √9 = 3',90,420,32,'#508879'),('Каждая линия — отдельный штрих.',90,550,24,'#28344b')]:
        for s in layout(text,x,y,height,2.2,color,1200):
            points=[(p[0],p[1]) for p in s['points']];draw.line(points,fill=color,width=3,joint='curve')
            for px,py in [points[0],points[-1]]:draw.ellipse((px-1.5,py-1.5,px+1.5,py+1.5),fill=color)
    title=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',32);draw.text((85,45),'Folio Canvas · встроенный рукописный печатный стиль',font=title,fill='#28344b')
    preview.save(DIST/'Folio-handwriting-preview.png')
    apk=DIST/'Folio-1.17.0-preview.1.apk';apk.write_bytes((DIST/'Folio.apk').read_bytes())
    artifacts=[apk,DIST/'Folio-Canvas-Plugin-0.2.0.zip',DIST/'Folio-Android-Plugin-source.zip',DIST/'Folio-handwriting-preview.png']
    receipt={'folio':'1.17.0-preview.1','plugin':'0.2.0','status':'preview; physical tablet and user VPN validation pending','verified':['APK compile/signature','20 ink model assertions','15 actual LAN server assertions with Android UI substituted','real MCP stdio exchange','5 cloud relay suites: authorization, queue, revocation, expiry, MCP','Java/JavaScript/Python AES-GCM and HMAC interoperability','desktop system proxy routing and mocked encrypted relay roundtrip'],'not_verified':['Physical tablet cloud connection/UI/saving','Actual user VPN network'],'files':{p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in artifacts}}
    (DIST/'Folio-plugin-validation.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    for p in artifacts:
        if p.suffix=='.zip':
            with zipfile.ZipFile(p) as z:assert z.testzip() is None
    print(json.dumps(receipt,ensure_ascii=False))
if __name__=='__main__':package()
