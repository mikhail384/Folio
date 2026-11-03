import {test} from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import {spawn,execFileSync} from 'node:child_process';
import {mkdtempSync,mkdirSync,rmSync,writeFileSync,statSync,utimesSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createApplication} from '../server.mjs';
import {createGateway} from '../deploy/failover/gateway-core.mjs';
import {roleToken} from '../../folio-cloud/lib/relay.mjs';

const here=resolve(fileURLToPath(new URL('..',import.meta.url)));
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function freePort(){const s=http.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
async function health(port){for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:'+port+'/health')).status===200)return;}catch{}await sleep(100);}throw new Error('server did not start');}
async function stop(child){if(!child||child.exitCode!==null)return;child.kill('SIGTERM');await new Promise(r=>{child.once('exit',r);setTimeout(r,3000).unref();});}

test('gateway checkpoints real Folio sessions and profiles, promotes once, and keeps backup authoritative',async()=>{
  const root=mkdtempSync(join(tmpdir(),'folio-failover-test-')),data=join(root,'primary'),standby=join(root,'standby'),key=join(root,'checkpoint.key');
  mkdirSync(data);writeFileSync(key,'k'.repeat(64));
  const origin='https://folio.test',token='s'.repeat(48),password='a valid test password',primaryPort=await freePort(),backupPort=await freePort();
  const app=createApplication({origin,dataDir:data,setupToken:token});let primary,backup,gateway,front;
  try{
    const setup=http.createServer((req,res)=>app.handle(req,res));await new Promise(r=>setup.listen(0,'127.0.0.1',r));
    const result=await fetch('http://127.0.0.1:'+setup.address().port+'/api/setup',{method:'POST',headers:{origin,'content-type':'application/json'},body:JSON.stringify({token,password})});
    assert.equal(result.status,200);const cookie=result.headers.getSetCookie()[0].split(';')[0];await new Promise(r=>setup.close(r));app.close();
    const env={...process.env,FOLIO_ORIGIN:origin,FOLIO_DATA_DIR:data,FOLIO_PORT:String(primaryPort),FOLIO_CHECKPOINT_KEY_FILE:key,FOLIO_ARCHIVE_SCRIPT:join(here,'deploy/failover/archive.py')};
    execFileSync('python3',[env.FOLIO_ARCHIVE_SCRIPT,'make',data,join(root,'manual.tar.gz')],{stdio:'inherit'});
    primary=spawn(process.execPath,[join(here,'deploy/failover/primary.mjs')],{env,stdio:'ignore'});await health(primaryPort);
    function startBackup(directory){if(backup&&backup.exitCode===null&&!backup.killed)return;backup=spawn(process.execPath,[join(here,'server.mjs')],{env:{...env,FOLIO_PORT:String(backupPort),FOLIO_DATA_DIR:directory},stdio:'ignore'});}
    gateway=createGateway({origin,root:standby,checkpointKey:'k'.repeat(64),archiveScript:env.FOLIO_ARCHIVE_SCRIPT,primaryPort,backupPort,startBackup,misses:1,probeTimeout:250});
    front=http.createServer((req,res)=>gateway.handle(req,res));await new Promise(r=>front.listen(0,'127.0.0.1',r));await gateway.checkpoint();
    const base='http://127.0.0.1:'+front.address().port;
    async function call(path,options={}){return fetch(base+path,{...options,headers:{cookie,origin,...options.headers}});}
    async function post(path,value){return call(path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(value)});}
    const pairedResponse=await post('/api/session',{}),paired=await pairedResponse.json();assert.equal(pairedResponse.status,200,JSON.stringify(paired));assert.equal(paired.connection.url,origin);
    const snapshot=join(standby,'latest.tar.gz'),old=new Date('2020-01-01T00:00:00Z');utimesSync(snapshot,old,old);const before=statSync(snapshot).mtimeMs,c=paired.connection;
    const idle=await call('/relay/v1/poll',{method:'POST',headers:{'content-type':'application/json','oai-sites-authorization':'Bearer '+c.service_token,authorization:'Bearer '+await roleToken(c.token,'device',c.room)},body:JSON.stringify({room:c.room,wait:false})});
    assert.equal(idle.status,200);assert.equal((await idle.json()).idle,true);assert.equal(statSync(snapshot).mtimeMs,before,'idle polling does not copy the entire profile store');
    const profile={samples:{'A':{advance:1,paths:[[[0,0,1],[1,1,.5]]]}}};
    assert.equal((await post('/api/profiles',{name:'mine',profile})).status,200);
    assert.equal((await call('/internal/checkpoint',{method:'POST'})).status,404);
    await stop(primary);await gateway.probe();assert.equal(gateway.getMode(),'backup');
    assert.deepEqual((await (await call('/api/profiles?name=mine')).json()).profile,profile);
    const after=await (await post('/api/session',{})).json();assert.deepEqual(after,paired,'same channel credentials survive promotion');
    const later={samples:{'B':{advance:1,paths:[[[0,0,1],[1,2,.5]]]}}};
    assert.equal((await post('/api/profiles',{name:'second',profile:later})).status,200);
    await new Promise(r=>front.close(r));gateway.close();
    gateway=createGateway({origin,root:standby,checkpointKey:'k'.repeat(64),archiveScript:env.FOLIO_ARCHIVE_SCRIPT,primaryPort,backupPort,startBackup,misses:1,probeTimeout:250});
    assert.equal(gateway.getMode(),'backup','restart never overwrites live backup with an older checkpoint');
    front=http.createServer((req,res)=>gateway.handle(req,res));await new Promise(r=>front.listen(0,'127.0.0.1',r));
    const restored=await fetch('http://127.0.0.1:'+front.address().port+'/api/profiles?name=second',{headers:{cookie,origin}});
    assert.deepEqual((await restored.json()).profile,later);
  }finally{gateway?.close();if(front?.listening)await new Promise(r=>front.close(r));await stop(primary);await stop(backup);try{app.close();}catch{}rmSync(root,{recursive:true,force:true});}
});
