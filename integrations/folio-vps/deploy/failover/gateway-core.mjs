import http from 'node:http';
import {mkdirSync,readFileSync,writeFileSync,renameSync,existsSync,openSync,fsyncSync,closeSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';

const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const maxResponse=512*1024*1024;
function request(port,method,path,headers={},body=Buffer.alloc(0),timeout=45000,limit=maxResponse){return new Promise((resolve,reject)=>{
  const call=http.request({host:'127.0.0.1',port,method,path,headers,timeout},response=>{
    const chunks=[];let total=0;response.on('data',chunk=>{total+=chunk.length;if(total>limit){call.destroy(new Error('Response too large'));return;}chunks.push(chunk);});
    response.on('aborted',()=>reject(new Error('Response interrupted')));response.on('error',reject);response.on('end',()=>resolve({status:response.statusCode,headers:response.headers,body:Buffer.concat(chunks)}));
  });call.on('timeout',()=>call.destroy(new Error('Upstream timeout')));call.on('error',reject);call.end(body);
});}
function durable(path,contents){const temp=path+'.'+randomUUID()+'.tmp';const fd=openSync(temp,'wx',0o600);try{writeFileSync(fd,contents);fsyncSync(fd);}finally{closeSync(fd);}renameSync(temp,path);if(process.platform!=='win32'){const dir=openSync(join(path,'..'),'r');try{fsyncSync(dir);}finally{closeSync(dir);}}}
const readonly=path=>['/','/login','/setup','/app.js','/style.css','/favicon.svg','/profile-archive.mjs','/training-archive.mjs','/health','/api/session','/api/profiles','/api/training-corpus'].includes(path)||path.startsWith('/.well-known/');
function needsCheckpoint(method,path,response){
  if(method==='GET'&&readonly(path))return false;
  // An idle poll only refreshes the tablet's ephemeral last-seen timestamp.
  // Checkpoint actual claimed tasks before acknowledging them to the tablet.
  if(method==='POST'&&path==='/relay/v1/poll'&&response.status===200&&response.body.length<128){try{if(JSON.parse(response.body).idle===true)return false;}catch{}}
  return true;
}

export function createGateway({origin,root,checkpointKey,archiveScript,primaryPort=18181,backupPort=18080,startBackup,probeMs=2500,probeTimeout=1500,misses=3}){
  mkdirSync(root,{recursive:true,mode:0o700});const latest=join(root,'latest.tar.gz'),modePath=join(root,'mode'),active=join(root,'active');
  let mode=existsSync(modePath)&&readFileSync(modePath,'utf8').trim()==='backup'?'backup':'primary';
  let failures=0,promoting=null,checkpointChain=Promise.resolve(),timer,closed=false;
  function checkpoint(){
    const operation=checkpointChain.catch(()=>{}).then(async()=>{
      if(mode!=='primary')throw new Error('Primary retired');
      const response=await request(primaryPort,'POST','/internal/checkpoint',{authorization:'Bearer '+checkpointKey},Buffer.alloc(0),60000);
      if(response.status!==200||!response.body.length)throw new Error('Checkpoint failed');
      const candidate=join(root,'candidate-'+randomUUID()+'.tar.gz'),verify=join(root,'verify-'+randomUUID());
      try{writeFileSync(candidate,response.body,{mode:0o600,flag:'wx'});execFileSync('python3',[archiveScript,'restore',candidate,verify],{timeout:60000,stdio:'ignore'});if(mode!=='primary')throw new Error('Primary retired');durable(latest,response.body);}
      finally{rmSync(candidate,{force:true});rmSync(verify,{recursive:true,force:true});}
    });checkpointChain=operation;return operation;
  }
  async function ensureBackup(){
    startBackup(active);
    for(let i=0;i<50;i++){try{const response=await request(backupPort,'GET','/health',{},Buffer.alloc(0),1000,4096);if(response.status===200&&JSON.parse(response.body).ready)return;}catch{}await pause(100);}
    throw new Error('Backup did not start');
  }
  function promote(){
    if(promoting)return promoting;if(mode==='backup')return ensureBackup();if(!existsSync(latest))return Promise.reject(new Error('No confirmed checkpoint'));
    mode='promoting';
    promoting=(async()=>{const staging=join(root,'restore-'+randomUUID());try{
      execFileSync('python3',[archiveScript,'restore',latest,staging],{timeout:60000,stdio:'ignore'});
      if(existsSync(active))throw new Error('Backup data already exists');renameSync(staging,active);durable(modePath,Buffer.from('backup\n'));mode='backup';await ensureBackup();
    }catch(error){rmSync(staging,{recursive:true,force:true});if(mode!=='backup')mode='primary';throw error;}finally{promoting=null;}})();return promoting;
  }
  async function probe(){if(closed||mode!=='primary')return;try{const response=await request(primaryPort,'GET','/health',{},Buffer.alloc(0),probeTimeout,4096);if(response.status!==200||!JSON.parse(response.body).ready)throw new Error('Primary not ready');failures=0;if(!existsSync(latest))await checkpoint();}
    catch{if(++failures>=misses){failures=0;try{await promote();}catch{}}}}
  async function handle(req,res){
    const path=new URL(req.url,origin).pathname;if(path.startsWith('/internal/')){res.writeHead(404);res.end();return;}
    let body;try{const chunks=[];let size=0;for await(const chunk of req){size+=chunk.length;if(size>8*1024*1024)throw new Error('Request too large');chunks.push(chunk);}body=Buffer.concat(chunks);}catch{res.writeHead(413);res.end();return;}
    const headers={...req.headers};for(const name of Object.keys(headers))if(name==='forwarded'||name.startsWith('x-forwarded-')||name==='connection'||name==='transfer-encoding')delete headers[name];
    const url=new URL(origin);headers.host=url.host;headers['x-forwarded-proto']='https';headers['x-forwarded-host']=url.host;headers['x-forwarded-port']=url.port||'443';headers['x-forwarded-for']=req.socket.remoteAddress||'';headers['content-length']=String(body.length);
    try{
      if(mode==='promoting')throw new Error('Switching');if(mode==='backup')await ensureBackup();const selected=mode,port=selected==='primary'?primaryPort:backupPort;
      const response=await request(port,req.method,req.url,headers,body,45000,16*1024*1024);
      if(selected==='primary'&&needsCheckpoint(req.method,path,response))await checkpoint();
      if(mode!==selected)throw new Error('Uncertain request');
      const outgoing={...response.headers};delete outgoing.connection;delete outgoing['transfer-encoding'];outgoing['content-length']=String(response.body.length);res.writeHead(response.status,outgoing);res.end(response.body);
    }catch{if(!res.headersSent)res.writeHead(503,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify({error:'Folio временно переключается. Прочитайте холст перед повторением записи.'}));}
  }
  function start(){if(mode==='backup')ensureBackup().catch(()=>{});else probe();timer=setInterval(probe,probeMs);timer.unref?.();}
  function close(){closed=true;clearInterval(timer);}
  return {handle,start,close,probe,promote,checkpoint,getMode:()=>mode};
}
