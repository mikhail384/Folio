import asyncio,json,sys
from pathlib import Path
from unittest.mock import patch
import httpx
import relay
ROOT=Path(__file__).resolve().parents[2]
async def main():
    vector=json.loads((ROOT/'build/folio-cloud-crypto-vector.json').read_text(encoding='utf-8'))
    assert relay.open_envelope(vector['key'],vector['value'],vector['aad'])=={'text':'Привет, Folio!'}
    assert relay.role_token(vector['key'],'device','a'*32)==vector['role']
    java=json.loads((ROOT/'build/folio-cloud-java-envelope.json').read_text(encoding='utf-8'))
    assert relay.open_envelope(vector['key'],java['value'],vector['aad'])=={'text':'Android → Folio'}
    key=vector['key'];room='a'*32;calls=[];saved={};proxy_seen=[];real=httpx.AsyncClient
    async def endpoint(request):
        body=json.loads(request.content);calls.append(request.url.path)
        assert request.headers['Authorization']=='Bearer '+relay.role_token(key,'client',room)
        assert request.headers['OAI-Sites-Authorization']=='Bearer test-service-access-not-a-real-credential'
        if request.url.path.endswith('/request'):
            saved['id']=body['id'];value=relay.open_envelope(key,body['envelope'],'folio:'+body['id']+':request');assert value=={'route':'state','payload':{}};return httpx.Response(200,json={'id':body['id']})
        return httpx.Response(200,json={'envelope':relay.seal(key,{'ok':True,'result':{'document_id':'doc','page_id':'page'}},'folio:'+saved['id']+':response')})
    def client(**kwargs):proxy_seen.append(kwargs.pop('proxy'));assert kwargs.pop('trust_env') is True;return real(transport=httpx.MockTransport(endpoint),trust_env=False,**kwargs)
    with patch.object(relay,'getproxies',return_value={'https':'http://127.0.0.1:8989'}),patch.object(relay.httpx,'AsyncClient',client):
        result=await relay.call({'url':'https://relay.example','room':room,'token':key,'service_token':'test-service-access-not-a-real-credential'},'state',{})
    assert result['page_id']=='page' and proxy_seen==['http://127.0.0.1:8989'] and len(calls)==2
    with patch.object(relay,'getproxies',return_value={'https':'http://127.0.0.1:8989'}):assert relay.system_proxy('http://127.0.0.1:18766') is None
    print('PASS JavaScript/Java/Python AES-GCM+HMAC interoperability, encrypted relay round trip, account headers, system HTTPS proxy selection and loopback exclusion; HTTP transport substituted')
if __name__=='__main__':asyncio.run(main())
