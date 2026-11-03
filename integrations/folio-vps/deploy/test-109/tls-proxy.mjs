import {createServer} from 'node:https';
import {request} from 'node:http';
import {readFileSync} from 'node:fs';
const host='109.66.236.217:17443',dir='/opt/folio-canvas-test/tls';
const server=createServer({key:readFileSync(dir+'/privkey.pem'),cert:readFileSync(dir+'/fullchain.pem'),minVersion:'TLSv1.2'},(req,res)=>{
  if(req.headers.host!==host){res.writeHead(400);res.end();return;}
  const headers={...req.headers};for(const name of Object.keys(headers))if(name==='forwarded'||name.startsWith('x-forwarded-'))delete headers[name];
  headers.host=host;headers['x-forwarded-proto']='https';headers['x-forwarded-host']=host;headers['x-forwarded-port']='17443';headers['x-forwarded-for']=req.socket.remoteAddress||'';
  const upstream=request({hostname:'127.0.0.1',port:18080,path:req.url,method:req.method,headers},response=>{res.writeHead(response.statusCode||502,response.headers);response.pipe(res);});
  upstream.on('error',()=>{if(!res.headersSent)res.writeHead(502,{'content-type':'text/plain; charset=utf-8'});res.end('Folio недоступен');});
  req.on('aborted',()=>upstream.destroy());req.pipe(upstream);
});
server.requestTimeout=120000;server.headersTimeout=15000;server.listen(17443,'0.0.0.0',()=>console.log('Folio HTTPS test is listening'));
