import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:http';
import {createHash,randomBytes} from 'node:crypto';
import {createApplication} from '../server.mjs';
import {roleToken,open,seal} from '../../folio-cloud/lib/relay.mjs';

test('private server: setup, OAuth PKCE, encrypted tablet queue and portable profiles',async()=>{
 const directory=mkdtempSync(join(tmpdir(),'folio-vps-test-'));let app;
 const server=createServer((req,res)=>app.handle(req,res));await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const origin='http://127.0.0.1:'+server.address().port,setup='one-time-setup-token-0123456789abcdef',password='this is a long test password';
 app=createApplication({origin,dataDir:directory,setupToken:setup,allowTestRedirect:true});
 let cookies={};
 async function request(path,options={}){
   const headers=new Headers(options.headers||{});if(Object.keys(cookies).length)headers.set('cookie',Object.entries(cookies).map(([k,v])=>k+'='+v).join('; '));
   const res=await fetch(origin+path,{...options,headers,redirect:'manual'});
   for(const raw of res.headers.getSetCookie()){const pair=raw.split(';')[0],at=pair.indexOf('=');cookies[pair.slice(0,at)]=pair.slice(at+1);}
   return res;
 }
 async function post(path,data){return request(path,{method:'POST',headers:{origin,'content-type':'application/json'},body:JSON.stringify(data)});}
 try{
   assert.equal((await post('/api/setup',{token:'wrong',password})).status,403);
   assert.equal((await post('/api/setup',{token:setup,password})).status,200);
   assert.equal((await post('/api/setup',{token:setup,password})).status,403);
   assert.equal((await request('/api/session',{method:'POST',headers:{origin:'https://evil.invalid','content-type':'application/json'},body:'{}'})).status,403);
   const session=await (await post('/api/session',{})).json(),c=session.connection;assert.equal(c.url,origin);
   const source={samples:{'я':{advance:1,paths:[[[0,0,1],[1,1,.5]]]}}};
   assert.equal((await post('/api/profiles',{name:'mine',profile:source})).status,200);
   assert.deepEqual((await (await request('/api/profiles?name=mine')).json()).profile,source);
   const unauth=await post('/mcp',{jsonrpc:'2.0',id:1,method:'tools/list'});assert.equal(unauth.status,401);assert.match(unauth.headers.get('www-authenticate'),/oauth-protected-resource/);
   const metadata=await (await request('/.well-known/openid-configuration')).json();
   const redirect='http://127.0.0.1:34567/callback';
   const registration=await post(new URL(metadata.registration_endpoint).pathname,{client_name:'Test',redirect_uris:[redirect],grant_types:['authorization_code','refresh_token'],response_types:['code'],token_endpoint_auth_method:'none'});
   assert.equal(registration.status,201,await registration.clone().text());const client=await registration.json();
   const verifier=randomBytes(32).toString('base64url'),challenge=createHash('sha256').update(verifier).digest('base64url');
   const q=new URLSearchParams({client_id:client.client_id,response_type:'code',redirect_uri:redirect,scope:'openid offline_access folio',prompt:'consent',resource:origin+'/mcp',state:'state1',code_challenge:challenge,code_challenge_method:'S256'});
   let r=await request(new URL(metadata.authorization_endpoint).pathname+'?'+q);let destination=r.headers.get('location');
   for(let i=0;i<12&&!destination?.startsWith(redirect);i++){
     assert(destination,'authorization redirected');const u=new URL(destination,origin);
     if(u.pathname.startsWith('/interaction/')){
       const view=await request(u.pathname+u.search);const html=await view.text();assert.equal(view.status,200,html);
       const form=new URLSearchParams();if(html.includes('name="password"'))form.set('password',password);
       r=await request(u.pathname,{method:'POST',headers:{origin,'content-type':'application/x-www-form-urlencoded'},body:form});
     } else r=await request(u.pathname+u.search);
     destination=r.headers.get('location');
   }
   assert(destination?.startsWith(redirect),'OAuth callback reached');const authorizationCode=new URL(destination).searchParams.get('code');assert(authorizationCode);
   const tokenRes=await request(new URL(metadata.token_endpoint).pathname,{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:client.client_id,code:authorizationCode,redirect_uri:redirect,code_verifier:verifier,resource:origin+'/mcp'})});
   assert.equal(tokenRes.status,200,await tokenRes.clone().text());const token=await tokenRes.json();assert(token.access_token);assert(token.refresh_token,'Token fields: '+JSON.stringify({scope:token.scope,keys:Object.keys(token)}));
   const refreshed=await request(new URL(metadata.token_endpoint).pathname,{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'refresh_token',client_id:client.client_id,refresh_token:token.refresh_token,resource:origin+'/mcp'})});assert.equal(refreshed.status,200,await refreshed.clone().text());
   const headers={authorization:'Bearer '+token.access_token,'content-type':'application/json'};
   let list=await request('/mcp',{method:'POST',headers,body:JSON.stringify({jsonrpc:'2.0',id:2,method:'tools/list'})});assert.equal(list.status,200);assert.equal((await list.json()).result.tools.length,11);
   const deviceHeaders={'oai-sites-authorization':'Bearer '+c.service_token,authorization:'Bearer '+await roleToken(c.token,'device',c.room),'content-type':'application/json'};
   const trainingId='b'.repeat(32),training={text:'H₂O',strokes:[{points:[10,20,.02,0,0,30,40,.8,0,0]}]};
   const trainingResponse=await request('/relay/v1/training',{method:'POST',headers:deviceHeaders,body:JSON.stringify({room:c.room,id:trainingId,envelope:await seal(c.token,training,'folio:training:'+trainingId)})});
   assert.equal(trainingResponse.status,200,await trainingResponse.clone().text());
   const trained=await (await request('/api/profiles?name=my-handwriting')).json();assert(trained.profile.samples['H₂O']);
   const immediate=await request('/relay/v1/poll',{method:'POST',headers:deviceHeaders,body:JSON.stringify({room:c.room,wait:false})});assert.equal((await immediate.json()).idle,true);
   const poll=()=>request('/relay/v1/poll',{method:'POST',headers:deviceHeaders,body:JSON.stringify({room:c.room})});
   const pending=poll();await new Promise(r=>setTimeout(r,100));
   const called=request('/mcp',{method:'POST',headers,body:JSON.stringify({jsonrpc:'2.0',id:3,method:'tools/call',params:{name:'folio_status',arguments:{}}})});
   const task=await (await pending).json();assert(task.id);assert.equal((await open(c.token,task.envelope,'folio:'+task.id+':request')).route,'state');
   const body={room:c.room,id:task.id,lease:task.lease,envelope:await seal(c.token,{ok:true,result:{document_id:'test-doc'}},'folio:'+task.id+':response')};
   assert.equal((await request('/relay/v1/reply',{method:'POST',headers:deviceHeaders,body:JSON.stringify(body)})).status,200);
   const response=await (await called).json();assert.match(response.result.content[0].text,/test-doc/);
   const replay=await request(new URL(metadata.token_endpoint).pathname,{method:'POST',headers:{'content-type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:client.client_id,code:authorizationCode,redirect_uri:redirect,code_verifier:verifier,resource:origin+'/mcp'})});assert.equal(replay.status,400);
 } finally {await new Promise(r=>server.close(r));app.close();rmSync(directory,{recursive:true,force:true});}
});
