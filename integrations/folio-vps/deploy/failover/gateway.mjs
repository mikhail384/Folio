import https from 'node:https';
import {readFileSync} from 'node:fs';
import {spawn} from 'node:child_process';
import {createGateway} from './gateway-core.mjs';

const origin=process.env.FOLIO_ORIGIN,host=new URL(origin).host;
const keyFile=process.env.FOLIO_TLS_KEY,certFile=process.env.FOLIO_TLS_CERT;
const checkpointKey=readFileSync(process.env.FOLIO_CHECKPOINT_KEY_FILE,'utf8').trim();
const backupPort=Number(process.env.FOLIO_BACKUP_PORT||18080),primaryPort=Number(process.env.FOLIO_PRIMARY_PORT||18181);
let backup;
function startBackup(dataDir){
  if(backup&&!backup.killed&&backup.exitCode===null)return;
  backup=spawn(process.execPath,[process.env.FOLIO_SERVER_SCRIPT],{env:{...process.env,FOLIO_DATA_DIR:dataDir,FOLIO_PORT:String(backupPort)},stdio:'inherit'});
  backup.on('error',()=>{});
}
const gateway=createGateway({origin,root:process.env.FOLIO_FAILOVER_STATE,checkpointKey,archiveScript:process.env.FOLIO_ARCHIVE_SCRIPT,startBackup,primaryPort,backupPort,probeMs:5000,probeTimeout:8000});
const server=https.createServer({key:readFileSync(keyFile),cert:readFileSync(certFile),minVersion:'TLSv1.2'},(req,res)=>{
  if(req.headers.host!==host){res.writeHead(400);res.end();return;}
  gateway.handle(req,res).catch(()=>{if(!res.headersSent)res.writeHead(503);res.end();});
});
server.requestTimeout=120000;server.headersTimeout=15000;
gateway.start();server.listen(Number(process.env.FOLIO_TLS_PORT||17443),process.env.FOLIO_BIND_HOST||'0.0.0.0');
const stop=()=>{gateway.close();if(backup)backup.kill('SIGTERM');server.close(()=>process.exit(0));};
process.on('SIGTERM',stop);process.on('SIGINT',stop);
