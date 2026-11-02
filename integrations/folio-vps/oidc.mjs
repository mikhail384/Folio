import Provider,{errors} from 'oidc-provider';

const escape=s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');
const page=(title,body)=>`<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(title)}</title><link rel="stylesheet" href="/style.css"><main class="card"><div class="brand">Folio <small>private cloud</small></div><h1>${escape(title)}</h1>${body}</main></html>`;

export function createOidc({origin,adapter,jwk,cookieKey,verifyPassword,readForm,allowedOrigin,limiter,allowTestRedirect=false}){
  const resource=origin+'/mcp';
  const provider=new Provider(origin,{
    adapter,jwks:{keys:[jwk]},cookies:{keys:[cookieKey]},
    features:{devInteractions:{enabled:false},registration:{enabled:true},resourceIndicators:{enabled:true,defaultResource:async()=>resource,getResourceServerInfo:async(_ctx,id)=>{if(id!==resource)throw new errors.InvalidTarget();return {scope:'folio',audience:resource,accessTokenTTL:3600,accessTokenFormat:'opaque'};}}},
    scopes:['openid','offline_access','folio'],responseTypes:['code'],pkce:{required:()=>true},
    findAccount:async(_ctx,sub)=>sub==='owner'?{accountId:sub,claims:async()=>({sub})}:undefined,
    interactions:{url:(_ctx,interaction)=>'/interaction/'+interaction.uid},
    issueRefreshToken:async(_ctx,client,code)=>client.grantTypes.includes('refresh_token')&&code.scopes.has('offline_access'),
  });
  provider.proxy=true;
  function redirectAllowed(value){try{const u=new URL(value);return (u.protocol==='https:'&&u.hostname==='chatgpt.com'&&(u.pathname==='/connector_platform_oauth_redirect'||u.pathname.startsWith('/connector/oauth/')))||(allowTestRedirect&&u.protocol==='http:'&&['127.0.0.1','localhost'].includes(u.hostname));}catch{return false;}}
  async function interaction(req,res){
    const details=await provider.interactionDetails(req,res),{uid,prompt,params,session,grantId}=details;
    if(!redirectAllowed(params.redirect_uri)){await provider.interactionFinished(req,res,{error:'access_denied',error_description:'Redirect address not allowed'},{mergeWithLastSubmission:false});return;}
    const action='/interaction/'+encodeURIComponent(uid);
    if(req.method==='GET'){
      const text=prompt.name==='login'?'<p>Введите пароль Folio Cloud для подключения ChatGPT к вашим тетрадям.</p><label>Пароль<input type="password" name="password" autocomplete="current-password" required></label><button>Войти</button>':'<p>Разрешить ChatGPT читать открытые тетради и по вашему запросу изменять холст?</p><button>Разрешить</button>';
      res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'",'X-Content-Type-Options':'nosniff'});res.end(page('Подключение Folio',`<form method="post" action="${action}">${text}</form>`));return;
    }
    if(req.method!=='POST'||!allowedOrigin(req)||!limiter(req,'oauth-login')){res.writeHead(403);res.end();return;}
    const form=await readForm(req);
    if(prompt.name==='login'){
      if(!await verifyPassword(form.get('password')||'')){res.writeHead(401,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});res.end(page('Не удалось войти','<p>Неверный пароль. Вернитесь назад и попробуйте снова.</p>'));return;}
      await provider.interactionFinished(req,res,{login:{accountId:'owner'}},{mergeWithLastSubmission:false});return;
    }
    if(prompt.name==='consent'){
      let grant=grantId?await provider.Grant.find(grantId):new provider.Grant({accountId:session.accountId,clientId:params.client_id});
      if(prompt.details.missingOIDCScope)grant.addOIDCScope(prompt.details.missingOIDCScope.join(' '));
      if(prompt.details.missingOIDCClaims)grant.addOIDCClaims(prompt.details.missingOIDCClaims);
      if(prompt.details.missingResourceScopes)for(const [indicator,scope] of Object.entries(prompt.details.missingResourceScopes))grant.addResourceScope(indicator,scope.join(' '));
      await provider.interactionFinished(req,res,{consent:{grantId:await grant.save()}},{mergeWithLastSubmission:true});return;
    }
    res.writeHead(400);res.end();
  }
  return {provider,resource,interaction};
}
