"""Publish only the iPad project to the already authorized Folio repository.

Uses GitHub CLI's OS-keyring credential; never reads or writes tokens itself.
Workflow publication is opt-in and needs the GitHub workflow scope.
"""
from pathlib import Path
import subprocess, json, base64, argparse, sys
sys.stdout.reconfigure(encoding='utf-8')
ROOT=Path(__file__).resolve().parents[2]
GH=ROOT/'.tools/gh/gh.exe'
REPO='repos/mikhail384/Folio'
def api(endpoint,method='GET',payload=None):
    cmd=[str(GH),'api',endpoint,'--method',method]
    if payload is not None: cmd+=['--input','-']
    r=subprocess.run(cmd,input=json.dumps(payload,ensure_ascii=False) if payload is not None else None,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=90)
    if r.returncode: raise RuntimeError(r.stderr)
    return json.loads(r.stdout) if r.stdout.strip() else None
def publish(workflow=False):
    head=api(REPO+'/git/ref/heads/main')['object']['sha']
    tree=api(REPO+'/git/commits/'+head)['tree']['sha']
    entries=[]
    for path in sorted((ROOT/'ipad').rglob('*')):
        if not path.is_file() or any(x in path.parts for x in ['__pycache__','xcuserdata','DerivedData']) or path.suffix in ['.pyc','.p12','.mobileprovision','.key']: continue
        data=path.read_bytes()
        if b'\x00' in data or path.suffix == '.png':
            blob=api(REPO+'/git/blobs','POST',{'content':base64.b64encode(data).decode(),'encoding':'base64'})
            entries.append({'path':path.relative_to(ROOT).as_posix(),'mode':'100644','type':'blob','sha':blob['sha']})
        else:
            entries.append({'path':path.relative_to(ROOT).as_posix(),'mode':'100644','type':'blob','content':data.decode('utf-8').replace('\r\n','\n')})
    if workflow:
        path=ROOT/'.github/workflows/ipad.yml'
        entries.append({'path':'.github/workflows/ipad.yml','mode':'100644','type':'blob','content':path.read_text(encoding='utf-8')})
    tree=api(REPO+'/git/trees','POST',{'base_tree':tree,'tree':entries})
    commit=api(REPO+'/git/commits','POST',{'message':'Add native Folio iPad app with PencilKit and document tests','tree':tree['sha'],'parents':[head]})
    api(REPO+'/git/refs/heads/main','PATCH',{'sha':commit['sha'],'force':False})
    print(json.dumps({'commit':commit['sha'],'files':len(entries),'workflow':workflow,'url':commit['html_url']},ensure_ascii=False))
if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('--workflow',action='store_true'); args=p.parse_args(); publish(args.workflow)
