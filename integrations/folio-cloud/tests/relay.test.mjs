import {test} from 'node:test';import assert from 'node:assert/strict';import {DatabaseSync} from 'node:sqlite';import {readFileSync,readdirSync,writeFileSync} from 'node:fs';
import {pair,deviceForOwner,authenticate,submit,claim,reply,result,seal,open,roleToken,encode,invoke,getProfile,storeProfile} from '../lib/relay.mjs';
import {layout,sample} from '../lib/handwriting.mjs';import {rpc,tools,call} from '../lib/mcp.mjs';
import {importTrainingSample} from '../lib/training.mjs';
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
test('cloud MCP invokes tablet queue, reports offline and protects profile owners',async()=>{const env=environment();await pair(env,'alice','https://test.invalid');const s=await deviceForOwner(env,'alice');await assert.rejects(invoke(env,'alice','state',{}));await claim(env,s);const promise=invoke(env,'alice','state',{});await new Promise(r=>setTimeout(r,30));const task=await claim(env,s);assert(task.id);await reply(env,s,{id:task.id,lease:task.lease,envelope:await seal(s.key,{ok:true,result:{document_id:'doc',page_id:'page'}},'folio:'+task.id+':response')});assert.equal((await promise).page_id,'page');await storeProfile(env,'alice','mine',{samples:{A:{advance:1,paths:[[[0,0,1],[1,1,1]]]}}});assert.equal(Object.keys((await getProfile(env,'bob','mine')).samples).length,0);assert.equal(tools.length,13);const discovered=await rpc(env,new Request('https://test.invalid'),{jsonrpc:'2.0',id:1,method:'tools/list'});assert.equal(discovered.result.tools.length,13);const denied=await rpc(env,new Request('https://test.invalid'),{jsonrpc:'2.0',id:2,method:'tools/call',params:{name:'folio_status'}});assert.equal(denied.result.isError,true);});
test('cloud handwriting supports Cyrillic, personal samples and strict missing glyphs',()=>{assert(layout('Привет! 2 + 2 = 4',20,30).length>1);const s=sample([{points:[10,20,1,0,0,20,40,.5,0,0]}]);assert.deepEqual(layout('я',100,200,20,2,'#123456',900,{я:s})[0].points,[[100,200,1],[110,220,.5]]);assert.throws(()=>layout('АБ',0,0,28,2,'#123456',900,{А:s}));});
test('personal handwriting adds repeatable per-letter variation for each operation',()=>{const glyph={advance:1.2,paths:[[[0,.1,1],[.45,.65,.8],[.9,.1,1]]]},samples={а:glyph};const first=layout('ааа',0,0,20,2,'#123456',900,samples,'op-a');assert.deepEqual(first,layout('ааа',0,0,20,2,'#123456',900,samples,'op-a'));assert.notDeepEqual(first,layout('ааа',0,0,20,2,'#123456',900,samples,'op-b'));assert.equal(first.length,3);});
test('handwriting keeps a clean endpoint when a captured stroke has an incomplete tail',()=>{const captured=sample([{points:[0,0,.8,0,0,0,10,.8,0,0,10,10,.8,0,0,10,0,.7,0,0,999]}]);assert.equal(captured.paths[0].length,4);assert.deepEqual(captured.paths[0].at(-1),[1,0,.7]);});
test('personal word variation widens words and stays operation-stable',()=>{const glyph={advance:1.2,natural_height:10,paths:[[[0,.1,1],[.25,.45,.8],[.5,.6,.7],[.75,.3,.8],[1,.1,1]]]},samples={слово:glyph};const original=JSON.stringify(glyph),plain=layout('слово',0,0,40,2,'#123456',900,samples)[0].points,first=layout('слово',0,0,40,2,'#123456',900,samples,'write-a')[0].points,again=layout('слово',0,0,40,2,'#123456',900,samples,'write-a')[0].points,other=layout('слово',0,0,40,2,'#123456',900,samples,'write-b')[0].points;assert.deepEqual(first,again);assert.notDeepEqual(first,other);assert.equal(JSON.stringify(glyph),original);assert(first.every((p,i)=>Number.isFinite(p[0])&&Number.isFinite(p[1])&&(!i||p[0]>first[i-1][0])));const span=points=>Math.max(...points.map(p=>p[0]))-Math.min(...points.map(p=>p[0]));assert(span(first)>span(plain)*1.05);assert(Math.max(...first.map((p,i)=>Math.hypot(p[0]-plain[i][0],p[1]-plain[i][1])))<7);});
test('personal sample corners are rounded while stroke endpoints stay anchored',()=>{const path=[[0,0,.5],[1,0,.7],[1,1,.9]],samples={слово:{advance:1,natural_height:10,paths:[path]}},points=layout('слово',0,0,20,2,'#123456',900,samples)[0].points;assert(points.length>path.length);assert.deepEqual(points[0],[0,1,.5]);assert.deepEqual(points.at(-1),[20,20,.9]);assert(!points.some(p=>p[0]===20&&p[1]===0));assert(points.every(p=>p[2]>=.5&&p[2]<=.9));});
test('dense descender turns are softened without moving stroke endpoints',()=>{const path=[...Array.from({length:13},(_,i)=>[i*.025,0,.8]),...Array.from({length:12},(_,i)=>[.3,(i+1)*.025,.8])],samples={у:{advance:1,natural_height:1,paths:[path]}},points=layout('у',0,0,20,2,'#123456',900,samples)[0].points;assert.deepEqual(points[0],[0,0,.8]);assert.deepEqual(points.at(-1),[6,6,.8]);assert(points.some(p=>p[0]>5.8&&p[0]<6&&p[1]>.1&&p[1]<.7));});
test('short upward hooks are removed and word heights stay consistent with spacing',()=>{
 const oval=Array.from({length:41},(_,i)=>{const angle=2*Math.PI*i/40;return [.2+.12*Math.cos(angle),.55+.18*Math.sin(angle),.8];}),top=30,anchor=oval[top];
 const hooked=[...oval.slice(0,top),anchor,[anchor[0],anchor[1]-.06,.8],[anchor[0]+.005,anchor[1]-.12,.8],[anchor[0]+.01,anchor[1]-.06,.8],anchor,...oval.slice(top+1)];
 const paths=layout('о',0,0,20,2,'#123456',900,{о:{advance:1,natural_height:1,paths:[hooked]}})[0].points;
 assert(paths[0][1]===hooked[0][1]*20);assert.equal(paths.at(-1)[1],hooked.at(-1)[1]*20);assert(Math.min(...paths.map(p=>p[1]))>Math.min(...hooked.map(p=>p[1]))*20+2);
 const line=[[[0,0,.8],[1,1,.8]]],samples={хорошая:{advance:1.3,natural_height:10,paths:line},Проведём:{advance:1.3,natural_height:100,paths:line}};
 const bounds=word=>{const points=layout(word,0,0,40,2,'#123456',900,samples)[0].points;return [Math.min(...points.map(p=>p[1])),Math.max(...points.map(p=>p[1]))];};
 const small=bounds('хорошая'),tall=bounds('Проведём');assert(Math.abs((tall[1]-tall[0])-(small[1]-small[0]))<40*.09);
 const phrase=layout('хорошая Проведём',0,0,40,2,'#123456',900,samples,'space-check'),gap=Math.min(...phrase[1].points.map(p=>p[0]))-Math.max(...phrase[0].points.map(p=>p[0]));assert(gap>40*.35);
});
test('long personal words get extra horizontal room',()=>{const word='увеличивается',samples={[word]:{advance:1,natural_height:40,paths:[[[0,0,.8],[1,1,.8]]]}},points=layout(word,0,0,40,2,'#123456',900,samples,'long-word')[0].points,width=Math.max(...points.map(p=>p[0]))-Math.min(...points.map(p=>p[0]));assert(width>40*1.14);});
test('layout report keeps word sizes uniform across explicit lines',()=>{const samples={хорошая:{advance:1,natural_height:40,paths:[[[0,0,.8],[1,1,.8]]]},Проведём:{advance:1,natural_height:100,paths:[[[0,0,.8],[1,1,.8]]]}};const result=layout('хорошая Проведём\nхорошая',0,0,40,2,'#123456',900,samples,'report',true);assert.equal(result.metrics.line_count,2);assert.equal(result.metrics.tokens.length,3);assert(result.metrics.tokens.every(item=>Math.abs((item.bounds[3]-item.bounds[1])/40-.95)<.001));});
test('personal words with tall ascenders and descenders share a visual height',()=>{const samples={Сегодня:{advance:1,natural_height:40,paths:[[[0,0,.8],[1,1,.8]]]},функция:{advance:1,natural_height:40,paths:[[[0,-.2,.8],[1,1.1,.8]]]}},height=word=>{const points=layout(word,0,0,40,2,'#123456',900,samples).flatMap(stroke=>stroke.points);return Math.max(...points.map(p=>p[1]))-Math.min(...points.map(p=>p[1]));};assert(Math.abs(height('Сегодня')-height('функция'))<.01);assert(Math.abs(height('функция')-38)<.01);});
test('captured connected е keeps the same local height as its neighbor',()=>{const word='ае',samples={[word]:{advance:1,paths:[[[0,0,.8],[.25,1,.8],[.5,0,.8]],[[.5,0,.8],[.75,1,.8],[1,0,.8]]]}},strokes=layout(word,0,0,28,2,'#123456',900,samples,'e-shape'),height=stroke=>Math.max(...stroke.points.map(p=>p[1]))-Math.min(...stroke.points.map(p=>p[1]));assert.equal(strokes.length,2);assert(Math.abs(height(strokes[0])-height(strokes[1]))<.03*height(strokes[0]));});
test('stored handwriting pressure replays safely on already-installed Folio',()=>{
 const source=[0.019999999552965164,.02,0,2,3,NaN,undefined];
 const glyph={advance:1,paths:[source.map((pressure,i)=>[i,i,pressure])]};
 const points=layout('П',10,20,28,2,'#123456',900,{П:glyph})[0].points;
 assert.deepEqual(points.map(p=>p[2]),[.02000001,.02000001,.02000001,2,2,1,1]);
 assert(points.every(p=>Math.fround(p[2])>=.02&&Math.fround(p[2])<=2));
 assert.equal(glyph.paths[0][0][2],source[0], 'saved samples are never rewritten');
});
test('paired tablet imports encrypted setup samples into the owner profile, with safe retries',async()=>{
 const env=environment(),a=await pair(env,'alice','https://test.invalid'),b=await pair(env,'bob','https://test.invalid');
 const auth=new Request('https://test.invalid',{headers:{authorization:'Bearer '+await roleToken(a.token,'device',a.room)}});
 const session=await authenticate(env,auth,a,'device'),id='a'.repeat(32);
 await storeProfile(env,'alice','my-handwriting',{samples:{'а':{advance:1,paths:[[[0,0,1],[1,1,1]]]}}});
 const raw={text:'Привет',strokes:[{points:[10,20,.02,0,0,25,45,.8,0,0]}]};
 const body={id,envelope:await seal(a.token,raw,'folio:training:'+id)};
 assert.equal((await importTrainingSample(env,session,body)).stored,true);
 const profile=await getProfile(env,'alice','my-handwriting');assert.deepEqual(Object.keys(profile.samples).sort(),['а','Привет'].sort());
 assert.equal(Object.keys((await getProfile(env,'bob','my-handwriting')).samples).length,0);
 profile.samples['Привет']={advance:1,paths:[[[0,0,.5],[1,1,.5]]]};await storeProfile(env,'alice','my-handwriting',profile);
 assert.equal((await importTrainingSample(env,session,body)).already_saved,true);
 assert.equal((await getProfile(env,'alice','my-handwriting')).samples['Привет'].paths[0][0][2],.5,'a retry must not overwrite a later edit');
 const autoId='c'.repeat(32),auto={text:'Привет',strokes:raw.strokes,if_missing:true};
 assert.equal((await importTrainingSample(env,session,{id:autoId,envelope:await seal(a.token,auto,'folio:training:'+autoId)})).skipped,true);
 assert.equal((await getProfile(env,'alice','my-handwriting')).samples['Привет'].paths[0][0][2],.5,'automatic analysis must not replace a confirmed sample');
 const id2='d'.repeat(32),newAuto={text:'слово',strokes:raw.strokes,if_missing:true};
 assert.equal((await importTrainingSample(env,session,{id:id2,envelope:await seal(a.token,newAuto,'folio:training:'+id2)})).profile,'my-handwriting-auto');
 assert.deepEqual(Object.keys((await getProfile(env,'alice','my-handwriting-auto')).samples),['слово']);
 assert.equal((await getProfile(env,'alice','my-handwriting')).samples['слово'],undefined,'automatic samples must never modify the manual profile');
 const changed={...body,envelope:await seal(a.token,{...raw,text:'мир'},'folio:training:'+id)};
 await assert.rejects(importTrainingSample(env,session,changed),e=>e.status===409);
 const wrong={...body,envelope:await seal(b.token,raw,'folio:training:'+id)};
 await assert.rejects(importTrainingSample(env,session,wrong),e=>e.status===400);
 const client=new Request('https://test.invalid',{headers:{authorization:'Bearer '+await roleToken(a.token,'client',a.room)}});
 await assert.rejects(authenticate(env,client,a,'device'));
 const invalid={id:'b'.repeat(32),envelope:await seal(a.token,{text:'x',strokes:[{points:[1,1,9,0,0,3,9,1,0,0]}]},'folio:training:'+'b'.repeat(32))};
 await assert.rejects(importTrainingSample(env,session,invalid),e=>e.status===400);
 assert.equal(Object.keys((await getProfile(env,'alice','my-handwriting')).samples).length,2);
});
test('personal writing merges automatic examples and verifies saved text offline',async()=>{
 const env=environment();await pair(env,'alice','https://test.invalid');const s=await deviceForOwner(env,'alice');await claim(env,s);
 const manual={advance:1,paths:[[[0,0,.8],[1,1,.8]]]},automatic={advance:1,paths:[[[0,0,.4],[1,1,.4]]]};
 await storeProfile(env,'alice','my-handwriting',{samples:{'я':manual}});await storeProfile(env,'alice','my-handwriting-auto',{samples:{'я':automatic,'ты':automatic}});
 const promise=call(env,'alice','folio_write_text',{document_id:'d',page_id:'p',revision:'1',style:'personal',text:'я ты',x:0,y:0});
 await new Promise(r=>setTimeout(r,40));const task=await claim(env,s),request=await open(s.key,task.envelope,'folio:'+task.id+':request');
 assert.equal(request.payload.strokes[0].points[0][2],.8,'manual sample has priority');assert.equal(request.payload.strokes[1].points[0][2],.4,'new automatic sample is used');
 await reply(env,s,{id:task.id,lease:task.lease,envelope:await seal(s.key,{ok:true,result:{added_strokes:2,revision:'2'}},'folio:'+task.id+':response')});
 await new Promise(r=>setTimeout(r,550));const verify=await claim(env,s),check=await open(s.key,verify.envelope,'folio:'+verify.id+':request');
 assert.equal(check.route,'verify_text');assert.equal(check.payload.expected_text,'я ты');assert.equal(check.payload.revision,'2');
 await reply(env,s,{id:verify.id,lease:verify.lease,envelope:await seal(s.key,{ok:true,result:{status:'pass',exact_match:true,readable:true,size_ok:true}},'folio:'+verify.id+':response')});
 const result=await promise;assert.equal(result.text_validation.status,'pass');assert.equal(result.text_validation.layout.naturalness.status,'pass');assert.equal(result.text_validation.ocr.status,'pass');
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
