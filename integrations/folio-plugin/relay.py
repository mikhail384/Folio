"""VPN-friendly outbound HTTPS relay transport shared with Folio Android/Cloud."""
import asyncio,base64,hashlib,hmac,json,uuid
from urllib.request import getproxies
from urllib.parse import urlparse
import httpx
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from secrets import token_bytes

def encode(data):return base64.urlsafe_b64encode(data).decode().rstrip('=')
def decode(value):return base64.urlsafe_b64decode(value+'='*((-len(value))%4))
def role_token(token,role,room):return encode(hmac.new(decode(token),('folio-'+role+':'+room).encode(),hashlib.sha256).digest())
def seal(token,value,aad):
    iv=token_bytes(12);data=AESGCM(decode(token)).encrypt(iv,json.dumps(value,ensure_ascii=False,separators=(',',':')).encode(),aad.encode());return {'iv':encode(iv),'data':encode(data)}
def open_envelope(token,value,aad):return json.loads(AESGCM(decode(token)).decrypt(decode(value['iv']),decode(value['data']),aad.encode()))
def system_proxy(url):
    target=urlparse(url)
    if target.hostname in ('localhost','127.0.0.1','::1'):return None
    proxies=getproxies();return proxies.get(target.scheme) or proxies.get('all')

async def call(config,route,payload):
    url=config['url'].rstrip('/');room=config['room'];token=config['token'];request_id=uuid.uuid4().hex
    headers={'Authorization':'Bearer '+role_token(token,'client',room),'OAI-Sites-Authorization':'Bearer '+config['service_token']}
    async with httpx.AsyncClient(timeout=40,trust_env=True,proxy=system_proxy(url),follow_redirects=False) as client:
        async def post(operation,body):
            response=await client.post(url+'/relay/v1/'+operation,json={'room':room,**body},headers=headers)
            if response.status_code!=200:
                try:message=response.json().get('error','Ошибка облака Folio')
                except ValueError:message='Облачное подключение недоступно. Скопируйте новое подключение со страницы Folio Cloud.'
                raise ValueError(f'Folio Cloud {response.status_code}: {message}')
            return response.json()
        await post('request',{'id':request_id,'envelope':seal(token,{'route':route,'payload':payload},'folio:'+request_id+':request')})
        until=asyncio.get_running_loop().time()+35
        while asyncio.get_running_loop().time()<until:
            result=await post('result',{'id':request_id})
            if result.get('envelope'):
                value=open_envelope(token,result['envelope'],'folio:'+request_id+':response')
                if not value.get('ok'):raise ValueError('Folio: '+value.get('error','Холст отклонил команду'))
                return value['result']
            await asyncio.sleep(.5)
        raise ValueError('Планшет не ответил. Откройте Folio и включите облачный доступ. VPN оставьте включённым. Перед повторной записью прочитайте холст.')
