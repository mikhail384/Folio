"""Install private MCP dependencies and optionally pair using Folio's copied JSON."""
from pathlib import Path
import getpass,json,os,subprocess,sys,venv
root=Path(__file__).resolve().parent
data_dir=Path(os.environ.get('FOLIO_DATA',str(Path.home()/'.folio-plugin')))
runtime=data_dir/'runtime'
if not runtime.exists():venv.EnvBuilder(with_pip=True).create(runtime)
python=runtime/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
subprocess.run([str(python),'-m','pip','install','--disable-pip-version-check','-r',str(root/'requirements.txt')],check=True)
if '--deps-only' in sys.argv:sys.exit(0)
raw=getpass.getpass('Paste connection JSON from Folio settings (hidden): ')
data=json.loads(raw)
path=data_dir/'connection.json';path.parent.mkdir(parents=True,exist_ok=True)
if not isinstance(data.get('url'),str) or not isinstance(data.get('token'),str):raise ValueError('Expected Folio connection JSON')
# Validate with the same production parser before committing credentials.
from urllib.parse import urlparse
import re
url=urlparse(data['url'])
if url.scheme not in ('http','https') or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ('','/') or not re.fullmatch(r'[A-Za-z0-9_-]{32,128}',data['token']):raise ValueError('Invalid Folio connection')
if data.get('transport')=='relay':
    service=data.get('service_token','')
    if url.scheme!='https' or not re.fullmatch('[a-f0-9]{32}',data.get('room','')) or not isinstance(service,str) or not 16<=len(service)<=2048 or any(c in service for c in '\r\n'):raise ValueError('Invalid Folio cloud connection')
path.write_text(json.dumps(data),encoding='utf-8')
try:path.chmod(0o600)
except OSError:pass
print('Folio connection saved. The pairing key was not printed. Start a new Codex chat after enabling the plugin.')
