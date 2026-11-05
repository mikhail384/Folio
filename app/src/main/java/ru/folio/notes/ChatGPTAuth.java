package ru.folio.notes;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** OpenAI Sign in with ChatGPT for local/open-source apps. No API key or client secret is used. */
final class ChatGPTAuth {
    private static final String DISCOVERY="https://auth.openai.com/.well-known/openid-configuration";
    private static final String RESOURCE="https://api.openai.com/v1";
    private static final String PREFS="folio_chatgpt_siwc";
    private static final String ACCOUNT="account_v1";
    private static final String HOST="host_id";
    private static final String KEY_ALIAS="folio.chatgpt.siwc.credentials.v1";
    private static final String REQUIRED_SCOPE="chatgpt.tokens.use.direct";

    interface LoginCallback { void done(Account account, Exception error); }
    static final class Account {
        final JSONObject data;
        Account(JSONObject data){this.data=data;}
        String email(){return data.optString("email","");}
        String clientId(){return data.optString("client_id","");}
        String subject(){return data.optString("subject","");}
        String idToken(){return data.optString("id_token","");}
        String scope(){return data.optString("scope","");}
        boolean canUsePlan(){return hasScope(scope(),REQUIRED_SCOPE);}
    }
    static final class Model {
        final String slug,name;
        Model(String slug,String name){this.slug=slug;this.name=name;}
        public String toString(){return name;}
    }

    private ChatGPTAuth(){}

    static Account account(Context context){
        String sealed=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(ACCOUNT,"");
        if(sealed.isEmpty())return null;
        try{return new Account(new JSONObject(unseal(context,sealed)));}catch(Exception invalid){return null;}
    }

    static void signIn(MainActivity activity,LoginCallback callback){
        try{Intent keepAlive=new Intent(activity,ChatGPTAuthService.class);if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(keepAlive);else activity.startService(keepAlive);}
        catch(RuntimeException unavailable){callback.done(null,new IOException("Android не смог оставить вход Folio активным во время переключения в браузер. Повторите вход.",unavailable));return;}
        Thread worker=new Thread(()->{
            ServerSocket server=null;AtomicReference<Exception> launchError=new AtomicReference<>();
            try{
                JSONObject metadata=getJson(DISCOVERY,null);
                String authorization=metadata.getString("authorization_endpoint");
                String tokenEndpoint=metadata.getString("token_endpoint");
                String issuer=metadata.getString("issuer");
                String jwksUri=metadata.getString("jwks_uri");
                Account old=account(activity);
                String client=old==null||old.clientId().isEmpty()?"dynamic_agent_client":old.clientId();
                String state=randomToken(32),nonce=randomToken(32),verifier=randomToken(48);
                String challenge=b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));server.setSoTimeout(900000);
                String redirect="http://127.0.0.1:"+server.getLocalPort()+"/auth/callback";
                Uri.Builder url=Uri.parse(authorization).buildUpon()
                    .appendQueryParameter("client_id",client)
                    .appendQueryParameter("response_type","code")
                    .appendQueryParameter("redirect_uri",redirect)
                    .appendQueryParameter("scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct")
                    .appendQueryParameter("resource",RESOURCE)
                    .appendQueryParameter("state",state)
                    .appendQueryParameter("nonce",nonce)
                    .appendQueryParameter("code_challenge_method","S256")
                    .appendQueryParameter("code_challenge",challenge)
                    .appendQueryParameter("ext_agent_host_id",hostId(activity));
                if(old==null||old.clientId().isEmpty())url.appendQueryParameter("agent_name_hint","Folio");
                if(old!=null&&!old.idToken().isEmpty())url.appendQueryParameter("id_token_hint",old.idToken());
                if(old!=null&&!old.email().isEmpty())url.appendQueryParameter("login_hint",old.email());
                if(old!=null&&!old.canUsePlan())url.appendQueryParameter("prompt","consent");
                Uri authorizeUri=url.build();final ServerSocket callbackServer=server;
                activity.ui.post(()->{
                    try{if(activity.isFinishing()||activity.isDestroyed())throw new IllegalStateException("Folio уже закрыт");activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,authorizeUri));}
                    catch(Exception e){launchError.set(e);serverClose(callbackServer);}
                });
                CallbackData callbackData=readCallback(callbackServer,state);
                if(launchError.get()!=null)throw new IOException("Не удалось открыть защищённый вход в браузере",launchError.get());
                String issued=callbackData.clientId;
                if(old==null||old.clientId().isEmpty()){
                    if(issued.isEmpty()||issued.equals("dynamic_agent_client"))throw new IOException("OpenAI не вернул зарегистрированный client_id. Повторите подключение позже.");
                    client=issued;
                }else if(!issued.isEmpty()&&!issued.equals(client))throw new IOException("Ответ авторизации относится к другой регистрации. Повторите вход.");
                Map<String,String> form=new LinkedHashMap<>();form.put("grant_type","authorization_code");form.put("client_id",client);form.put("code",callbackData.code);form.put("code_verifier",verifier);form.put("redirect_uri",redirect);form.put("resource",RESOURCE);
                JSONObject tokens=postForm(tokenEndpoint,form);
                String idToken=tokens.optString("id_token","");
                if(idToken.isEmpty())throw new IOException("Ответ входа не содержит подтверждённую учётную запись.");
                JSONObject claims=verifyIdToken(idToken,client,nonce,issuer,jwksUri);
                String scopes=tokens.optString("scope",callbackData.scope);
                if(scopes.isEmpty()&&old!=null)scopes=old.scope();
                JSONObject saved=new JSONObject();
                saved.put("client_id",client).put("host_id",hostId(activity)).put("issuer",issuer)
                    .put("subject",claims.getString("sub")).put("email",claims.optString("email",""))
                    .put("id_token",idToken).put("access_token",tokens.optString("access_token",""))
                    .put("refresh_token",tokens.optString("refresh_token",old==null?"":old.data.optString("refresh_token","")))
                    .put("scope",scopes).put("expires_at",System.currentTimeMillis()+Math.max(1,tokens.optLong("expires_in",3600))*1000L);
                save(activity,saved);
                Account result=new Account(saved);
                if(!result.canUsePlan())throw new IOException("Вход выполнен, но доступ chatgpt.tokens.use.direct не выдан. Проверьте подтверждение доступа к плану ChatGPT и повторите вход.");
                deliver(activity,callback,result,null);
            }catch(Exception error){Exception launch=launchError.get();deliver(activity,callback,null,launch==null?error:new IOException("Не удалось открыть защищённый вход в браузере.",launch));}
            finally{if(server!=null)try{server.close();}catch(IOException ignored){}try{activity.getApplicationContext().stopService(new Intent(activity,ChatGPTAuthService.class));}catch(Exception ignored){}}
        },"folio-chatgpt-oauth");worker.setDaemon(true);worker.start();
    }

    private static void deliver(MainActivity a,LoginCallback callback,Account account,Exception error){
        a.ui.post(()->{if(!a.isFinishing()&&!a.isDestroyed())callback.done(account,error);});
    }

    private static final class CallbackData {String code="",state="",clientId="",scope="",error="";}
    private static CallbackData readCallback(ServerSocket server,String expectedState)throws Exception{
        long deadline=System.currentTimeMillis()+900000;
        while(System.currentTimeMillis()<deadline){
            try(Socket socket=server.accept()){
                socket.setSoTimeout(10000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                String first=in.readLine();if(first==null)continue;String[] part=first.split(" ");
                for(int i=0;i<40;i++){String header=in.readLine();if(header==null||header.isEmpty())break;}
                if(part.length<2||!part[0].equals("GET")){respond(socket,400,"Неверный запрос");continue;}
                Uri uri=Uri.parse("http://127.0.0.1"+part[1]);
                if(!"/auth/callback".equals(uri.getPath())){respond(socket,404,"Путь не найден");continue;}
                CallbackData result=new CallbackData();result.state=uri.getQueryParameter("state");result.error=uri.getQueryParameter("error");result.code=uri.getQueryParameter("code");result.clientId=uri.getQueryParameter("client_id");result.scope=uri.getQueryParameter("scope");
                if(!constantEquals(expectedState,result.state)){respond(socket,400,"Проверка входа не пройдена. Вернитесь в Folio и повторите попытку.");throw new IOException("Проверка OAuth state не пройдена.");}
                if(!result.error.isEmpty()){respond(socket,200,"Вход отменён. Можно вернуться в Folio.");throw new IOException(result.error.equals("access_denied")?"Доступ к плану ChatGPT не подтверждён.":"Вход в ChatGPT завершился ошибкой: "+result.error);}
                if(result.code==null||result.code.isEmpty()){respond(socket,400,"В ответе нет кода авторизации.");throw new IOException("OpenAI не вернул код авторизации.");}
                respond(socket,200,"Вход подтверждён. Возвращаемся в Folio — приложение завершит подключение.");return result;
            }
        }
        throw new SocketTimeoutException("Ожидание подтверждения входа истекло. Повторите подключение в Folio.");
    }

    private static void respond(Socket socket,int status,String message)throws IOException{
        String returnLink="folio://chatgpt-auth/return";
        String action=status==200?"<p><a id='return' href='"+returnLink+"' style='display:inline-block;margin-top:24px;padding:14px 20px;border-radius:14px;background:#b9a9ff;color:#17191f;text-decoration:none;font-weight:600'>Вернуться в Folio</a></p><script>setTimeout(function(){window.location.href='"+returnLink+"'},350)</script>":"";
        String body="<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Folio</title><body style='font:18px system-ui;background:#17191f;color:#f2f2f7;padding:32px'>"+message+action+"</body>";
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+(status==200?" OK":" Error")+"\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store, no-cache, must-revalidate\r\nPragma: no-cache\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    private static void serverClose(ServerSocket server){if(server!=null)try{server.close();}catch(IOException ignored){}}
    private static boolean constantEquals(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}

    static String accessToken(Context context)throws Exception{
        synchronized(ChatGPTAuth.class){
            Account current=account(context);if(current==null)throw new IOException("Сначала подключите ChatGPT.");
            if(!current.canUsePlan())throw new IOException("Вход сохранён без разрешения на использование плана ChatGPT. Подключите его повторно.");
            long expiry=current.data.optLong("expires_at",0);
            if(expiry>System.currentTimeMillis()+90000&&!current.data.optString("access_token","").isEmpty())return current.data.getString("access_token");
            String refresh=current.data.optString("refresh_token","");if(refresh.isEmpty())throw new IOException("Вход истёк. Подключите ChatGPT повторно.");
            JSONObject meta=getJson(DISCOVERY,null);Map<String,String> form=new LinkedHashMap<>();form.put("grant_type","refresh_token");form.put("client_id",current.clientId());form.put("refresh_token",refresh);form.put("resource",RESOURCE);
            JSONObject tokens=postForm(meta.getString("token_endpoint"),form);
            current.data.put("access_token",tokens.optString("access_token",""));current.data.put("refresh_token",tokens.optString("refresh_token",refresh));current.data.put("expires_at",System.currentTimeMillis()+Math.max(1,tokens.optLong("expires_in",3600))*1000L);
            if(tokens.has("scope"))current.data.put("scope",tokens.getString("scope"));if(tokens.has("id_token"))current.data.put("id_token",tokens.getString("id_token"));
            save(context,current.data);String access=current.data.optString("access_token","");if(access.isEmpty())throw new IOException("Не удалось обновить вход в ChatGPT.");return access;
        }
    }

    static List<Model> models(Context context)throws Exception{
        JSONObject response=getJson(RESOURCE+"/models",accessToken(context));JSONArray rows=response.optJSONArray("models");if(rows==null)throw new IOException("ChatGPT не вернул список моделей.");
        ArrayList<Model> out=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||!"list".equals(row.optString("visibility")))continue;String slug=row.optString("slug");if(slug.isEmpty())continue;out.add(new Model(slug,row.optString("display_name",slug)));}
        if(out.isEmpty())throw new IOException("Для этой учётной записи нет доступных моделей Responses API.");return out;
    }

    static JSONObject getJson(String url,String bearer)throws Exception{return requestJson(url,"GET",null,bearer);}
    private static JSONObject postForm(String url,Map<String,String> fields)throws Exception{
        StringBuilder data=new StringBuilder();for(Map.Entry<String,String> e:fields.entrySet()){if(data.length()>0)data.append('&');data.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}
        return requestJson(url,"POST",data.toString(),null);
    }
    private static JSONObject requestJson(String address,String method,String body,String bearer)throws Exception{
        HttpURLConnection con=(HttpURLConnection)new URL(address).openConnection();con.setRequestMethod(method);con.setConnectTimeout(25000);con.setReadTimeout(45000);con.setUseCaches(false);con.setRequestProperty("Accept","application/json");con.setRequestProperty("User-Agent","Folio/1.23.0-preview.7 (Android)");
        if(bearer!=null)con.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);con.setDoOutput(true);con.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=utf-8");con.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=con.getOutputStream()){out.write(bytes);}}
        int status=con.getResponseCode();InputStream stream=status>=200&&status<300?con.getInputStream():con.getErrorStream();String response=readUtf8(stream,2*1024*1024);con.disconnect();
        if(status<200||status>=300)throw httpError(status,response);
        return new JSONObject(response);
    }
    static IOException httpError(int status,String response){
        String code="",message="";try{JSONObject json=new JSONObject(response);JSONObject error=json.optJSONObject("error");if(error!=null){code=error.optString("code",error.optString("type",""));message=error.optString("message","");}}catch(Exception ignored){}
        if(code.contains("usage_limit")||code.contains("usage_unavailable")||status==429)return new IOException("Лимит использования плана ChatGPT достигнут или временно недоступен. Попробуйте позже.");
        if(status==401||status==403)return new IOException("OpenAI не принял разрешение входа. Подключите ChatGPT повторно.");
        if(status==400&&code.contains("subscription_sharing_unsupported_capability"))return new IOException("Этот запрос или модель пока не поддерживается доступом через план ChatGPT.");
        if(message.length()>220)message=message.substring(0,220);
        return new IOException("Запрос к OpenAI не выполнен (HTTP "+status+")"+(code.isEmpty()?"":" · "+code)+(message.isEmpty()?"":" · "+message));
    }
    static String readUtf8(InputStream in,int max)throws IOException{
        if(in==null)return "";try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=source.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Ответ сервера слишком велик.");out.write(buf,0,n);}return out.toString("UTF-8");}
    }

    private static JSONObject verifyIdToken(String token,String clientId,String nonce,String issuer,String jwksUri)throws Exception{
        String[] parts=token.split("\\.");if(parts.length!=3)throw new IOException("Неверный формат ID token.");
        JSONObject header=new JSONObject(new String(Base64.decode(parts[0],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8));
        JSONObject claims=new JSONObject(new String(Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8));
        String kid=header.optString("kid"),alg=header.optString("alg");if(kid.isEmpty())throw new IOException("ID token не содержит ключ проверки.");
        JSONObject keys=getJson(jwksUri,null);JSONArray list=keys.optJSONArray("keys");JSONObject jwk=null;if(list!=null)for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&kid.equals(item.optString("kid"))){jwk=item;break;}}
        if(jwk==null)throw new IOException("Не найден опубликованный ключ подписи ID token.");
        PublicKey publicKey=publicKey(jwk,alg);Signature signature=Signature.getInstance(alg.equals("RS256")?"SHA256withRSA":"SHA256withECDSA");signature.initVerify(publicKey);signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));byte[] raw=Base64.decode(parts[2],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);if(alg.equals("ES256"))raw=ecdsaDer(raw);if(!signature.verify(raw))throw new IOException("Подпись ID token OpenAI не прошла проверку.");
        if(!issuer.equals(claims.optString("iss")))throw new IOException("Издатель ID token не совпадает.");
        boolean audience=false;Object aud=claims.opt("aud");if(aud instanceof String)audience=clientId.equals(aud);else if(aud instanceof JSONArray){JSONArray listAud=(JSONArray)aud;for(int i=0;i<listAud.length();i++)if(clientId.equals(listAud.optString(i)))audience=true;}
        if(!audience)throw new IOException("ID token выдан для другой регистрации.");
        if(claims.optLong("exp",0)<=System.currentTimeMillis()/1000L)throw new IOException("Срок ID token истёк.");
        if(!constantEquals(nonce,claims.optString("nonce")))throw new IOException("Проверка OIDC nonce не пройдена.");
        if(claims.optString("sub").isEmpty())throw new IOException("В ID token нет идентификатора учётной записи.");
        return claims;
    }
    private static PublicKey publicKey(JSONObject jwk,String alg)throws Exception{
        if(alg.equals("RS256")&&"RSA".equals(jwk.optString("kty"))){BigInteger n=new BigInteger(1,Base64.decode(jwk.getString("n"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));BigInteger e=new BigInteger(1,Base64.decode(jwk.getString("e"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n,e));}
        if(alg.equals("ES256")&&"EC".equals(jwk.optString("kty"))&&"P-256".equals(jwk.optString("crv"))){AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));ECParameterSpec spec=parameters.getParameterSpec(ECParameterSpec.class);BigInteger x=new BigInteger(1,Base64.decode(jwk.getString("x"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));BigInteger y=new BigInteger(1,Base64.decode(jwk.getString("y"),Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x,y),spec));}
        throw new IOException("Алгоритм подписи OpenAI не поддерживается: "+alg);
    }
    private static byte[] ecdsaDer(byte[] raw)throws IOException{
        if(raw.length!=64)throw new IOException("Неверная длина подписи ES256.");byte[] r=integerDer(Arrays.copyOfRange(raw,0,32)),s=integerDer(Arrays.copyOfRange(raw,32,64));ByteArrayOutputStream seq=new ByteArrayOutputStream();seq.write(0x30);seq.write(r.length+s.length);seq.write(r);seq.write(s);return seq.toByteArray();
    }
    private static byte[] integerDer(byte[] raw)throws IOException{
        int p=0;while(p<raw.length-1&&raw[p]==0)p++;boolean prefix=(raw[p]&0x80)!=0;ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(0x02);out.write(raw.length-p+(prefix?1:0));if(prefix)out.write(0);out.write(raw,p,raw.length-p);return out.toByteArray();
    }
    private static boolean hasScope(String scopes,String wanted){for(String value:scopes.split("\\s+"))if(wanted.equals(value))return true;return false;}
    private static String hostId(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String id=prefs.getString(HOST,"");if(id.isEmpty()){id="urn:uuid:"+UUID.randomUUID();prefs.edit().putString(HOST,id).apply();}return id;
    }
    private static void save(Context context,JSONObject account)throws Exception{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(ACCOUNT,seal(context,account.toString())).commit();}
    private static String seal(Context context,String plain)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[] iv=cipher.getIV();byte[] packed=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,packed,0,iv.length);System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);return Base64.encodeToString(packed,Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING);
    }
    private static String unseal(Context context,String sealed)throws Exception{
        byte[] packed=Base64.decode(sealed,Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING);if(packed.length<29)throw new IOException("Повреждённое хранилище учётной записи.");byte[] iv=Arrays.copyOfRange(packed,0,12);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(packed,12,packed.length-12),StandardCharsets.UTF_8);
    }
    private static SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(KEY_ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();}
        return (SecretKey)store.getKey(KEY_ALIAS,null);
    }
    private static String randomToken(int bytes){byte[] raw=new byte[bytes];new SecureRandom().nextBytes(raw);return b64(raw);}
    private static String b64(byte[] bytes){return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
}
