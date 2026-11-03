import {test} from 'node:test';import assert from 'node:assert/strict';import {DatabaseSync} from 'node:sqlite';import {readFileSync,readdirSync,writeFileSync} from 'node:fs';
import {pair,deviceForOwner,authenticate,submit,claim,reply,result,seal,open,roleToken,encode,invoke,getProfile,storeProfile} from '../lib/relay.mjs';
import {layout,sample} from '../lib/handwriting.mjs';import {rpc,tools,call} from '../lib/mcp.mjs';
function environment(){
 const sqlite=new DatabaseSync(':memory:');
 for(const f of readdirSync(new URL('../drizzle/',import.meta.url)).filter(x=>x.endsWith('.sql')))sqlite.exec(readFileSync(new URL('../drizzle/'+f,import.meta.url),'utf8'));
 const objects=new Map();
 const DB={prepare(sql){let args=[];return {
  bind(...values){args=values;return this;},
  async first(){return sqlite.prepare(sql).get(...args)||null;},
  async all(){return {results:sqlite.prepare(sql).all(...args)};},
  async run(){return {meta:{changes:Number(sqlite.prepare(sql).run(...args).changes)}};}
 };}};
 const BUCKET={async put(key,value){objects.set(key,value);},async get(key){return objects.has(key)?{text:async()=>objects.get(key)}:null;},async delete(keys){for(const key of Array.isArray(keys)?keys:[keys])objects.delete(key);}};
 return {FOLIO_SERVICE_ACCESS:'test-service-access-not-a-real-credential',DB,BUCKET,sqlite,objects};
}

test('encrypted envelopes bind request ID and message direction',async()=>{const key=encode(new Uint8Array(32).fill(7)),aad='folio:test:request';const value=await seal(key,{text:'Привет, Folio!'},aad);assert.deepEqual(await open(key,value,aad),{text:'Привет, Folio!'});await assert.rejects(open(key,value,'folio:other:request'));await assert.rejects(open(key,value,'folio:test:response'));writeFileSync(new URL('../../../build/folio-cloud-crypto-vector.json',import.meta.url),JSON.stringify({key,aad,value,role:await roleToken(key,'device','a'.repeat(32))}));});
test('private account pairing and device/client roles stay separate',async()=>{const env=environment(),a=await pair(env,'alice','https://test.invalid'),b=await pair(env,'bob','https://test.invalid');assert.notEqual(a.room,b.room);const request=new Request('https://test.invalid',{headers:{Authorization:'Bearer '+await roleToken(a.token,'device',a.room)}});assert.equal((await authenticate(env,request,a,'device')).owner,'alice');await assert.rejects(authenticate(env,request,a,'client'));await assert.rejects(authenticate(env,request,{room:b.room},'device'));const stored=env.sqlite.prepare('SELECT secret FROM folio_sessions WHERE owner=?').get('alice');assert(!stored.secret.includes(a.token));});
test('one claim, authenticated reply, result, revocation and logical expiration',async()=>{const env=environment(),c=await pair(env,'alice','https://test.invalid'),s=await deviceForOwner(env,'alice'),id=crypto.randomUUID();await submit(env,s,id,await seal(s.key,{route:'ink',payload:{operation_id:'abc12345'}},'folio:'+id+':request'));const [one,two]=await Promise.all([claim(env,s),claim(env,s)]);assert.equal([one,two].filter(x=>x.id).length,1);const task=one.id?one:two;assert.deepEqual(await open(s.key,task.envelope,'folio:'+id+':request'),{route:'ink',payload:{operation_id:'abc12345'}});assert.equal((await result(env,s,id)).pending,true);await assert.rejects(reply(env,s,{id,lease:'bad',envelope:{}}));await reply(env,s,{id,lease:task.lease,envelope:await seal(s.key,{ok:true,result:{added_strokes:1}},'folio:'+id+':response')});assert.equal((await open(s.key,(await result(env,s,id)).envelope,'folio:'+id+':response')).result.added_strokes,1);assert(![...env.objects.values()].some(v=>v.includes('added_strokes')));env.sqlite.prepare('UPDATE folio_tasks SET expires=0 WHERE id=?').run(id);await assert.rejects(result(env,s,id));await pair(env,'alice','https://test.invalid',true);await assert.rejects(authenticate(env,new Request('https://test.invalid',{headers:{Authorization:'Bearer '+await roleToken(c.token,'device',c.room)}}),c,'device'));});
test('cloud MCP invokes tablet queue, reports offline and protects profile owners',async()=>{const env=environment();await pair(env,'alice','https://test.invalid');const s=await deviceForOwner(env,'alice');await assert.rejects(invoke(env,'alice','state',{}));await claim(env,s);const promise=invoke(env,'alice','state',{});await new Promise(r=>setTimeout(r,30));const task=await claim(env,s);assert(task.id);await reply(env,s,{id:task.id,lease:task.lease,envelope:await seal(s.key,{ok:true,result:{document_id:'doc',page_id:'page'}},'folio:'+task.id+':response')});assert.equal((await promise).page_id,'page');await storeProfile(env,'alice','mine',{samples:{A:{advance:1,paths:[[[0,0,1],[1,1,1]]]}}});assert.equal(Object.keys((await getProfile(env,'bob','mine')).samples).length,0);assert.equal(tools.length,11);const discovered=await rpc(env,new Request('https://test.invalid'),{jsonrpc:'2.0',id:1,method:'tools/list'});assert.equal(discovered.result.tools.length,11);const denied=await rpc(env,new Request('https://test.invalid'),{jsonrpc:'2.0',id:2,method:'tools/call',params:{name:'folio_status'}});assert.equal(denied.result.isError,true);});
test('cloud handwriting supports Cyrillic, personal samples and strict missing glyphs',()=>{assert(layout('Привет! 2 + 2 = 4',20,30).length>1);const s=sample([{points:[10,20,1,0,0,20,40,.5,0,0]}]);assert.deepEqual(layout('я',100,200,20,2,'#123456',900,{я:s})[0].points,[[100,200,1],[110,220,.5]]);assert.throws(()=>layout('АБ',0,0,28,2,'#123456',900,{А:s}));});
test('stored handwriting pressure replays safely on already-installed Folio',()=>{
 const source=[0.019999999552965164,.02,0,2,3,NaN,undefined];
 const glyph={advance:1,paths:[source.map((pressure,i)=>[i,i,pressure])]};
 const points=layout('П',10,20,28,2,'#123456',900,{П:glyph})[0].points;
 assert.deepEqual(points.map(p=>p[2]),[.02000001,.02000001,.02000001,2,2,1,1]);
 assert(points.every(p=>Math.fround(p[2])>=.02&&Math.fround(p[2])<=2));
 assert.equal(glyph.paths[0][0][2],source[0], 'saved samples are never rewritten');
});
test('notebook learning requests bounded regions and stores original vector examples per account',async()=>{
 const env=environment();await pair(env,'alice','https://test.invalid');const s=await deviceForOwner(env,'alice');await claim(env,s);
 const params={document_id:'doc',page_id:'page',revision:'1',bounds:[0,0,100,100],samples:[{text:'я',rect:[0,.1,.2,.4]}]};
 const pending=call(env,'alice','folio_learn_notebook_regions',params);await new Promise(r=>setTimeout(r,30));
 const job=await claim(env,s),request=await open(s.key,job.envelope,'folio:'+job.id+':request');assert.equal(request.route,'notebook_samples');assert.deepEqual(request.payload.samples,params.samples);
 await reply(env,s,{id:job.id,lease:job.lease,envelope:await seal(s.key,{ok:true,result:{samples:[{text:'я',strokes:[{points:[0,0,1,0,0,10,20,.5,0,0]}]}]}},'folio:'+job.id+':response')});
 const result=await pending;assert.deepEqual(result.learned,['я']);assert.deepEqual(Object.keys((await getProfile(env,'bob','my-handwriting')).samples),[]);
 const profile=await getProfile(env,'alice','my-handwriting');assert.deepEqual(layout('я',100,200,20,2,'#123456',900,profile.samples)[0].points,[[100,200,1],[110,220,.5]]);
});
