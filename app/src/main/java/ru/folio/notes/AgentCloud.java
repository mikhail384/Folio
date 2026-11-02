package ru.folio.notes;
import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;

/** Outbound HTTPS only; uses Android's normal VPN/proxy routing, never a physical-network binding. */
final class AgentCloud {
    static final String ORIGIN="https://folio-canvas-link.mikhailllm.chatgpt.site";
    final AgentBridge bridge;volatile boolean running;volatile HttpURLConnection connection;volatile String status="Облако не подключено";Thread thread;
    AgentCloud(AgentBridge bridge){this.bridge=bridge;}
    boolean configured(){return !bridge.a.prefs.getString("agent_cloud","").isEmpty();}
    JSONObject profile()throws Exception{return new JSONObject(bridge.a.prefs.getString("agent_cloud","{}"));}
    static JSONObject validate(String raw)throws Exception{
        if(raw.length()>8192)throw new IllegalArgumentException("Invalid profile");JSONObject p=new JSONObject(raw);if(!"relay".equals(p.optString("transport"))||!ORIGIN.equals(p.optString("url"))||!p.optString("room").matches("[a-f0-9]{32}")||!p.optString("token").matches("[A-Za-z0-9_-]{43}")||AgentCrypto.decode(p.getString("token")).length!=32)throw new IllegalArgumentException("Invalid profile");String service=p.getString("service_token");if(service.length()<16||service.length()>2048||service.indexOf('\r')>=0||service.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid profile");return p;
    }
    void start(int generation){if(running)return;running=true;status="Подключаем облако · VPN можно оставить включённым";thread=new Thread(()->loop(generation),"folio-agent-cloud");thread.setDaemon(true);thread.start();}
    boolean active(int generation){return running&&bridge.epoch==generation&&bridge.a.resumed&&bridge.enabled();}
    void stop(){running=false;HttpURLConnection c=connection;if(c!=null)c.disconnect();if(thread!=null)thread.interrupt();status=configured()?"Ожидание открытой тетради":"Облако не подключено";}
    JSONObject post(JSONObject config,String route,JSONObject body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(config.getString("url")+"/relay/v1/"+route).openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(14000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+AgentCrypto.role(config.getString("token"),"device",config.getString("room")));c.setRequestProperty("OAI-Sites-Authorization","Bearer "+config.getString("service_token"));body.put("room",config.getString("room"));byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        try{try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();if(code!=200)throw new IOException(code==401||code==403?"Нужно заново скопировать облачное подключение":"Облако недоступно · HTTP "+code);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>8*1024*1024)throw new IOException("Слишком большая команда");}}return new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();if(connection==c)connection=null;}
    }
    void loop(int generation){int delay=1000;try{final JSONObject config=validate(bridge.a.prefs.getString("agent_cloud",""));while(active(generation))try{
        JSONObject task=post(config,"poll",new JSONObject());if(!active(generation))break;status="Облако подключено · доступ из разных сетей";delay=1000;if(task.optBoolean("idle",false))continue;
        String id=task.getString("id");JSONObject plain=AgentCrypto.open(config.getString("token"),task.getJSONObject("envelope"),"folio:"+id+":request");if(!active(generation))break;
        FutureTask<JSONObject> action=new FutureTask<>(()->bridge.handle("/v1/"+plain.getString("route"),plain.getJSONObject("payload"),generation));bridge.a.ui.post(action);JSONObject response=new JSONObject();
        try{response.put("ok",true).put("result",action.get(15,TimeUnit.SECONDS));}catch(Exception error){action.cancel(false);Throwable cause=error instanceof ExecutionException?error.getCause():error;response.put("ok",false).put("status",cause instanceof AgentBridge.Fault?((AgentBridge.Fault)cause).code:409).put("error",cause.getMessage()==null?"Холст занят":cause.getMessage());}
        if(active(generation))post(config,"reply",new JSONObject().put("id",id).put("lease",task.getString("lease")).put("envelope",AgentCrypto.seal(config.getString("token"),response,"folio:"+id+":response")));
    }catch(Exception error){if(!active(generation))break;status="Переподключение к облаку · "+(error instanceof IOException?error.getMessage():"проверьте сопряжение");try{Thread.sleep(delay);}catch(InterruptedException stop){break;}delay=Math.min(15000,delay*2);}}catch(Exception invalid){status="Вставьте новое подключение со страницы Folio Cloud";}finally{if(bridge.epoch==generation)running=false;}}
}
