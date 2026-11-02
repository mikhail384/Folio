import http from 'node:http';
import {readFileSync,writeFileSync,renameSync,existsSync,mkdirSync} from 'node:fs';
import {join,resolve} from 'node:path';
import {randomBytes,generateKeyPairSync,createHash,scrypt as scryptCallback,timingSafeEqual} from 'node:crypto';
import {promisify} from 'node:util';
import {fileURLToPath} from 'node:url';
import {openStorage} from './storage.mjs';
import {createOidc} from './oidc.mjs';
import {pair,authenticate,claim,reply,result,submit,deviceForOwner,listProfiles,getProfile,storeProfile,ApiError} from '../folio-cloud/lib/relay.mjs';
import {rpc} from '../folio-cloud/lib/mcp.mjs';
import {validateProfile} from '../folio-cloud/lib/profile-archive.mjs';

const scrypt=promisify(scryptCallback),here=fileURLToPath(new URL('.',import.meta.url));
const encode=bytes=>Buffer.from(bytes).toString('base64url'),digest=text=>createHash('sha256').update(text).digest('hex');
const escape=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');
function equal(a,b){const x=Buffer.from(String(a)),y=Buffer.from(String(b));return x.length===y.length&&timingSafeEqual(x,y);}
function atomic(path,data){const temp=path+'.tmp';writeFileSync(temp,JSON.stringify(data),{mode:0o600});renameSync(temp,path);}

export function createApplication({origin,dataDir,setupToken,allowTestRedirect=false}){
  const originUrl=new URL(origin);if(originUrl.origin!==origin||!(originUrl.protocol==='https:'||allowTestRedirect&&originUrl.protocol==='http:'&&originUrl.hostname==='127.0.0.1'))throw new Error('An explicit HTTPS origin is required');
  mkdirSync(dataDir,{recursive:true,mode:0o700});const secretsPath=join(dataDir,'secrets.json');let secrets;
  if(existsSync(secretsPath))secrets=JSON.parse(readFileSync(secretsPath,'utf8'));
  else{
    if(typeof setupToken!=='string'||setupToken.length<32)throw new Error('A one-time setup token is required');
    const {privateKey}=generateKeyPairSync('rsa',{modulusLength:2048});const jwk=privateKey.export({format:'jwk'});Object.assign(jwk,{kid:encode(randomBytes(12)),use:'sig',alg:'RS256'});
    secrets={setupHash:digest(setupToken),jwk,cookieKey:encode(randomBytes(32)),serviceToken:encode(randomBytes(32))};atomic(secretsPath,secrets);
  }
  const storage=openStorage(dataDir),env={...storage,FOLIO_SERVICE_ACCESS:secrets.serviceToken},db=storage.database;
  const limiterState=new Map();let settingPassword=false;
  function limiter(req,group){const key=group+':'+(req.socket.remoteAddress||'unknown'),now=Date.now(),old=limiterState.get(key)||[],recent=old.filter(t=>now-t<300000),limit=group==='oauth-registration'?30:8;if(recent.length>=limit)return false;recent.push(now);limiterState.set(key,recent);return true;}
  async function verifyPassword(password){if(!secrets.passwordHash||typeof password!=='string'||password.length>512)return false;const actual=await scrypt(password,Buffer.from(secrets.passwordSalt,'base64url'),32,{N:32768,r:8,p:1,maxmem:64*1024*1024});return equal(encode(actual),secrets.passwordHash);}
  async function setPassword(password){if(typeof password!=='string'||password.length<12||password.length>512)throw new ApiError(400,'Пароль должен содержать не менее 12 символов');const salt=randomBytes(16),hash=await scrypt(password,salt,32,{N:32768,r:8,p:1,maxmem:64*1024*1024});secrets.passwordSalt=encode(salt);secrets.passwordHash=encode(hash);delete secrets.setupHash;atomic(secretsPath,secrets);}
  function allowedOrigin(req){return req.headers.origin===origin;}
  async function readRaw(req,limit=8*1024*1024){let length=0;const chunks=[];for await(const chunk of req){length+=chunk.length;if(length>limit)throw new ApiError(413,'Слишком большой запрос');chunks.push(chunk);}return Buffer.concat(chunks).toString('utf8');}
  async function readJson(req,limit){try{return JSON.parse(await readRaw(req,limit));}catch(e){if(e instanceof ApiError)throw e;throw new ApiError(400,'Некорректный запрос');}}
  async function readForm(req){return new URLSearchParams(await readRaw(req,4096));}
  const oidc=createOidc({origin,adapter:storage.OidcAdapter,jwk:secrets.jwk,cookieKey:secrets.cookieKey,verifyPassword,readForm,allowedOrigin,limiter,allowTestRedirect});const oidcHandler=oidc.provider.callback();
  function send(res,status,body,type='application/json; charset=utf-8',headers={}){res.writeHead(status,{'Content-Type':type,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',...headers});res.end(typeof body==='string'?body:JSON.stringify(body));}
  function webSession(req){const cookie=(req.headers.cookie||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('folio_sid='));if(!cookie)return false;const raw=cookie.slice('folio_sid='.length);if(!/^[A-Za-z0-9_-]{43}$/.test(raw))return false;return !!db.prepare('SELECT id FROM web_sessions WHERE id=? AND expires>?').get(digest(raw),Date.now());}
  function createSession(res){const raw=encode(randomBytes(32));db.prepare('DELETE FROM web_sessions WHERE expires<?').run(Date.now());db.prepare('INSERT INTO web_sessions(id,expires) VALUES(?,?)').run(digest(raw),Date.now()+8*3600000);res.setHeader('Set-Cookie',`folio_sid=${raw}; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800${originUrl.protocol==='https:'?'; Secure':''}`);}
  function requireWeb(req){if(!webSession(req))throw new ApiError(401,'Войдите в Folio Cloud');}
  function requirePostOrigin(req){if(!allowedOrigin(req)||!String(req.headers['content-type']||'').toLowerCase().startsWith('application/json'))throw new ApiError(403,'Недопустимый источник запроса');}
  const staticFiles={'/':'index.html','/login':'login.html','/setup':'setup.html','/app.js':'app.js','/style.css':'style.css'};
  const sendStatic=(res,name)=>{const path=join(here,'public',name),mime=name.endsWith('.js')?'text/javascript; charset=utf-8':name.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8';send(res,200,readFileSync(path,'utf8'),mime,{'Content-Security-Policy':"default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"});};
  async function handle(req,res){try{
    const url=new URL(req.url,origin),path=url.pathname;
    if(path.startsWith('/interaction/')){await oidc.interaction(req,res);return;}
    if(path==='/.well-known/oauth-protected-resource'||path==='/.well-known/oauth-protected-resource/mcp'){send(res,200,{resource:oidc.resource,authorization_servers:[origin],scopes_supported:['folio']});return;}
    if(path==='/health'){send(res,200,{service:'Folio VPS',ready:!!secrets.passwordHash});return;}
    if(path==='/profile-archive.mjs'){send(res,200,readFileSync(resolve(here,'../folio-cloud/lib/profile-archive.mjs'),'utf8'),'text/javascript; charset=utf-8');return;}
    if(path==='/mcp'){
      const auth=String(req.headers.authorization||''),token=auth.startsWith('Bearer ')?await oidc.provider.AccessToken.find(auth.slice(7)):null;
      if(!token||token.accountId!=='owner'||token.aud!==oidc.resource||!String(token.scope||'').split(' ').includes('folio')){send(res,401,{error:'Authentication required'},undefined,{'WWW-Authenticate':`Bearer resource_metadata="${origin}/.well-known/oauth-protected-resource", scope="folio"`});return;}
      if(req.method!=='POST'){send(res,405,{error:'Use POST'});return;}
      const body=await readJson(req),trusted=new Request(origin+'/mcp',{headers:{'oai-authenticated-user-id':'owner'}}),answer=await rpc(env,trusted,body);if(answer===null){res.writeHead(202);res.end();}else {if(body.method==='tools/list'&&answer.result?.tools)answer.result.tools=answer.result.tools.map(tool=>({...tool,securitySchemes:[{type:'oauth2',scopes:['folio']}]}));send(res,200,answer);}return;
    }
    if(path.startsWith('/relay/v1/')){
      if(req.method!=='POST'||!equal(req.headers['oai-sites-authorization']||'','Bearer '+secrets.serviceToken))throw new ApiError(401,'Подключение отклонено');
      const op=path.slice('/relay/v1/'.length);if(!['poll','reply','request','result'].includes(op))throw new ApiError(404,'Unknown route');
      const body=await readJson(req),headers=new Headers();if(req.headers.authorization)headers.set('authorization',req.headers.authorization);
      const session=await authenticate(env,new Request(origin+path,{headers}),body,['poll','reply'].includes(op)?'device':'client');
      if(op==='request'){send(res,200,await submit(env,session,body.id,body.envelope));return;}
      if(op==='result'){send(res,200,await result(env,session,body.id));return;}
      if(op==='reply'){send(res,200,await reply(env,session,body));return;}
      const until=Date.now()+9000;do{if(req.aborted||res.destroyed)return;const task=await claim(env,session);if(!task.idle){send(res,200,task);return;}await new Promise(r=>setTimeout(r,600));}while(Date.now()<until);send(res,200,{idle:true});return;
    }
    if(path==='/api/setup'&&req.method==='POST'){
      requirePostOrigin(req);if(!limiter(req,'setup'))throw new ApiError(429,'Повторите позже');const body=await readJson(req,4096);
      if(settingPassword||!secrets.setupHash||!equal(digest(String(body.token||'')),secrets.setupHash))throw new ApiError(403,'Ссылка настройки недействительна');settingPassword=true;try{await setPassword(body.password);}finally{settingPassword=false;}createSession(res);send(res,200,{ok:true});return;
    }
    if(path==='/api/login'&&req.method==='POST'){
      requirePostOrigin(req);if(!limiter(req,'login'))throw new ApiError(429,'Повторите позже');const body=await readJson(req,4096);if(!await verifyPassword(body.password))throw new ApiError(401,'Неверный пароль');createSession(res);send(res,200,{ok:true});return;
    }
    if(path==='/api/password'&&req.method==='POST'){
      requireWeb(req);requirePostOrigin(req);if(!limiter(req,'password'))throw new ApiError(429,'Повторите позже');const body=await readJson(req,4096);if(!await verifyPassword(body.current))throw new ApiError(401,'Неверный текущий пароль');
      await setPassword(body.next);db.exec("DELETE FROM web_sessions; DELETE FROM oidc WHERE model IN ('AccessToken','RefreshToken','Grant','Session','Interaction','AuthorizationCode');");createSession(res);send(res,200,{ok:true});return;
    }
    if(path==='/api/session'&&req.method==='GET'){
      requireWeb(req);try{const s=await deviceForOwner(env,'owner');send(res,200,{paired:true,online:Date.now()-s.seen<40000});}catch(e){if(e.status===409)send(res,200,{paired:false,online:false});else throw e;}return;
    }
    if(path==='/api/session'&&req.method==='POST'){
      requireWeb(req);requirePostOrigin(req);const body=await readJson(req,4096);send(res,200,{connection:await pair(env,'owner',origin,body.reset===true)});return;
    }
    if(path==='/api/profiles'&&req.method==='GET'){
      requireWeb(req);const name=url.searchParams.get('name');if(!name)send(res,200,await listProfiles(env,'owner'));else send(res,200,{name,profile:validateProfile(await getProfile(env,'owner',name))});return;
    }
    if(path==='/api/profiles'&&req.method==='POST'){
      requireWeb(req);requirePostOrigin(req);const body=await readJson(req),name=body.name;if(typeof name!=='string'||!/^[A-Za-z0-9_-]{1,48}$/.test(name))throw new ApiError(400,'Недопустимое имя профиля');const incoming=validateProfile(body.profile),current=validateProfile(await getProfile(env,'owner',name)),merged=validateProfile({samples:{...current.samples,...incoming.samples}});await storeProfile(env,'owner',name,merged);send(res,200,{imported:Object.keys(incoming.samples).length,total:Object.keys(merged.samples).length});return;
    }
    if(staticFiles[path]&&req.method==='GET'){
      if(path==='/'&&!secrets.passwordHash){res.writeHead(303,{Location:'/setup'});res.end();return;}
      if(path==='/'&&!webSession(req)){res.writeHead(303,{Location:'/login'});res.end();return;}
      sendStatic(res,staticFiles[path]);return;
    }
    if(path==='/reg'&&req.method==='POST'&&!limiter(req,'oauth-registration')){send(res,429,{error:'rate_limited'});return;}
    oidcHandler(req,res);
  }catch(e){if(res.headersSent){res.destroy();return;}send(res,e instanceof ApiError?e.status:500,{error:e instanceof ApiError?e.message:'Ошибка Folio Cloud'});}}
  return {handle,storage,oidc,close(){storage.close();}};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const origin=process.env.FOLIO_ORIGIN,dataDir=process.env.FOLIO_DATA_DIR,setupToken=process.env.FOLIO_SETUP_TOKEN;
  if(!origin||!dataDir)throw new Error('FOLIO_ORIGIN and FOLIO_DATA_DIR are required');
  const app=createApplication({origin,dataDir,setupToken});const server=http.createServer(app.handle);
  server.listen(Number(process.env.FOLIO_PORT||18080),'127.0.0.1',()=>console.log('Folio VPS listening on loopback'));
  const stop=()=>server.close(()=>{app.close();process.exit(0)});process.on('SIGTERM',stop);process.on('SIGINT',stop);
}
