"""Publish the current Android/plugin source to a separate GitHub branch; main is untouched."""
from pathlib import Path
import base64,concurrent.futures,hashlib,json,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1];GH=ROOT/'.tools/gh/gh.exe';REPO='repos/mikhail384/Folio';BRANCH='codex/folio-canvas-plugin'
sys.stdout.reconfigure(encoding='utf-8')
def api(endpoint,payload=None):
    args=[str(GH),'api',REPO+'/'+endpoint]
    if payload is not None:args+=['--input','-']
    r=subprocess.run(args,input=json.dumps(payload) if payload is not None else None,capture_output=True,text=True,encoding='utf-8')
    if r.returncode:raise RuntimeError(r.stderr[:800])
    return json.loads(r.stdout)
def main():
    base=api('git/ref/heads/main')['object']['sha'];tree=api('git/trees/'+base+'?recursive=1');assert not tree.get('truncated')
    existing={x['path']:x['sha'] for x in tree['tree'] if x['type']=='blob'}
    files=[]
    for folder in ['app','shared','scripts','tests','integrations/focusflow','integrations/folio-plugin','.agents']:
        files.extend(p for p in (ROOT/folder).rglob('*') if p.is_file() and not any(part in {'build','__pycache__','.venv','handwriting-profiles'} for part in p.relative_to(ROOT).parts) and p.suffix not in {'.pyc','.jks','.keystore'} and p.name not in {'connection.json','config.local.json'})
    files.extend(ROOT/name for name in ['README.md','build.gradle','settings.gradle','.gitignore'])
    changed=[]
    for p in files:
        data=p.read_bytes();path=p.relative_to(ROOT).as_posix();sha=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
        if existing.get(path)==sha:continue
        if re.search(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9_-]{35,})',data):raise RuntimeError('Possible credential in '+path)
        changed.append((path,data,sha))
    print('Changed source files:',len(changed),flush=True)
    def blob(item):
        path,data,sha=item
        result=api('git/blobs',{'content':base64.b64encode(data).decode(),'encoding':'base64'})
        assert result['sha']==sha
        return {'path':path,'mode':'100644','type':'blob','sha':sha}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:entries=list(pool.map(blob,changed))
    result=api('git/trees',{'base_tree':tree['sha'],'tree':entries})
    commit=api('git/commits',{'message':'Add Folio canvas MCP plugin and foreground LAN ink API','tree':result['sha'],'parents':[base]})
    # Never overwrite an existing branch on a retry; require a new publication name instead.
    api('git/refs',{'ref':'refs/heads/'+BRANCH,'sha':commit['sha']})
    receipt={'repository':'mikhail384/Folio','branch':BRANCH,'commit':commit['sha'],'base':base,'changed_files':[x['path'] for x in entries]}
    (ROOT/'dist/Folio-plugin-source-publication.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Published source:',commit['sha'])
if __name__=='__main__':main()
