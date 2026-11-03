// Dedicated Folio instance behind the failover gateway. Its checkpoint is taken
// while this process's event loop is blocked, so SQLite and objects agree.
import http from 'node:http';
import {readFileSync,createReadStream,statSync,unlinkSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {randomUUID,timingSafeEqual} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {createApplication} from '../../server.mjs';

const origin=process.env.FOLIO_ORIGIN,dataDir=process.env.FOLIO_DATA_DIR;
const checkpointKey=readFileSync(process.env.FOLIO_CHECKPOINT_KEY_FILE,'utf8').trim();
const archiveScript=resolve(process.env.FOLIO_ARCHIVE_SCRIPT);
if(!origin||!dataDir||checkpointKey.length<40)throw new Error('Missing failover configuration');
const app=createApplication({origin,dataDir});
const server=http.createServer((req,res)=>{
  if(req.url!=='/internal/checkpoint')return app.handle(req,res);
  const actual=Buffer.from(String(req.headers.authorization||''));
  const expected=Buffer.from('Bearer '+checkpointKey);
  if(req.method!=='POST'||actual.length!==expected.length||!timingSafeEqual(actual,expected)){res.writeHead(403);res.end();return;}
  const archive=join(tmpdir(),'folio-checkpoint-'+randomUUID()+'.tar.gz');
  const cleanup=()=>{try{unlinkSync(archive);}catch{}};
  try{
    execFileSync('python3',[archiveScript,'make',dataDir,archive],{timeout:60000,stdio:'ignore'});
    const size=statSync(archive).size;if(size>512*1024*1024)throw new Error('Checkpoint too large');
    res.writeHead(200,{'Content-Type':'application/octet-stream','Content-Length':size,'Cache-Control':'no-store'});
    const stream=createReadStream(archive);stream.on('error',()=>res.destroy());res.on('close',cleanup);stream.pipe(res);
  }catch{cleanup();if(!res.headersSent){res.writeHead(503);res.end();}else res.destroy();}
});
server.listen(Number(process.env.FOLIO_PORT||18181),'127.0.0.1');
const stop=()=>server.close(()=>{app.close();process.exit(0)});
process.on('SIGTERM',stop);process.on('SIGINT',stop);
