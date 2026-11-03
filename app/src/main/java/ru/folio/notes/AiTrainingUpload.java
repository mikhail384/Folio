package ru.folio.notes;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import org.json.*;

/** Same authenticated, encrypted relay as manual samples; no network is bypassed. */
final class AiTrainingUpload {
    volatile HttpURLConnection connection;
    void stop(){HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    JSONObject send(JSONObject config,JSONObject item)throws Exception{
        String id=item.getString("id"),room=config.getString("room"),token=config.getString("token");
        JSONObject payload=new JSONObject().put("text",item.getString("text")).put("strokes",item.getJSONArray("strokes")).put("if_missing",true);
        JSONObject body=new JSONObject().put("room",room).put("id",id).put("envelope",AgentCrypto.seal(token,payload,"folio:training:"+id));
        HttpURLConnection c=(HttpURLConnection)new URL(config.getString("url")+"/relay/v1/training").openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(7000);c.setReadTimeout(14000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+AgentCrypto.role(token,"device",room));c.setRequestProperty("OAI-Sites-Authorization","Bearer "+config.getString("service_token"));byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        try{try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();if(code!=200)throw new IOException("Ошибка облака: HTTP "+code);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>65536)throw new IOException("Ответ облака слишком большой");}}JSONObject response=new JSONObject(out.toString("UTF-8"));if(!id.equals(response.optString("id"))||!response.optBoolean("stored"))throw new IOException("Облако не подтвердило образец");return response;}finally{c.disconnect();if(connection==c)connection=null;}
    }
}
