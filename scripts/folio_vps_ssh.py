"""Run one command over verified SSH; credentials arrive only through stdin."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / '.tools' / 'folio-vps-deps'))
import paramiko

request = json.loads(sys.stdin.readline())
ssh = paramiko.SSHClient()
ssh.load_host_keys(str(Path.home() / '.ssh' / 'known_hosts'))
ssh.set_missing_host_key_policy(paramiko.RejectPolicy())
try:
    ssh.connect('31.77.128.190', username='root', password=request['password'],
                allow_agent=False, look_for_keys=False, timeout=12, auth_timeout=12)
    _, stdout, stderr = ssh.exec_command(request['command'], timeout=request.get('timeout', 30))
    output = stdout.read().decode('utf-8', 'replace')
    error = stderr.read().decode('utf-8', 'replace')
    code = stdout.channel.recv_exit_status()
    print(json.dumps({'exit_code': code, 'stdout': output, 'stderr': error}, ensure_ascii=False))
finally:
    ssh.close()
