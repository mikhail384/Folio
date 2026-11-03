package ru.folio.notes;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class AiTrainingUploadTests {
    static int checks;static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    static String readLine(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1&&c!='\n')if(c!='\r')b.write(c);return b.toString("UTF-8");}
    public static void main(String[] args)throws Exception{
        String token=AgentCrypto.encode(new byte[32]),room="a".repeat(32),id="b".repeat(32); // synthetic test data only
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            Future<JSONObject> received=worker.submit(()->{try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);InputStream in=socket.getInputStream();String request=readLine(in);Map<String,String> headers=new HashMap<>();String line;
                while(!(line=readLine(in)).isEmpty()){int colon=line.indexOf(':');if(colon>0)headers.put(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());}
                int size=Integer.parseInt(headers.get("content-length"));byte[] data=new byte[size];int n=0;while(n<size){int read=in.read(data,n,size-n);if(read<0)throw new EOFException();n+=read;}
                JSONObject body=new JSONObject(new String(data,StandardCharsets.UTF_8));JSONObject result=new JSONObject().put("id",id).put("stored",true).put("skipped",true);byte[] bytes=result.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
                return new JSONObject().put("request",request).put("authorization",headers.get("authorization")).put("site",headers.get("oai-sites-authorization")).put("body",body);
            }});
            JSONObject config=new JSONObject().put("url","http://127.0.0.1:"+server.getLocalPort()).put("room",room).put("token",token).put("service_token","synthetic-token-for-tests");
            check(AgentEndpoints.allowed(AgentEndpoints.TEST_VPS)&&AgentEndpoints.automatic(AgentEndpoints.TEST_VPS),"updated test backend may receive automatic samples");
            check(AgentEndpoints.allowed(AgentEndpoints.PREVIOUS_VPS)&&!AgentEndpoints.automatic(AgentEndpoints.PREVIOUS_VPS),"old server never receives automatic samples");
            check(!AgentEndpoints.allowed("https://109.66.236.217:17443.attacker.example")&&!AgentEndpoints.allowed("http://109.66.236.217:17443"),"untrusted host or cleartext is rejected");
            JSONObject item=new JSONObject().put("id",id).put("text","слово").put("strokes",new JSONArray("[{points:[0,0,1,0,0,10,20,1,0,0]}]"));
            JSONObject response=new AiTrainingUpload().send(config,item),captured=received.get(5,TimeUnit.SECONDS),body=captured.getJSONObject("body");
            check(response.optBoolean("stored")&&response.optBoolean("skipped"),"accept stored/skipped receipt");
            check(captured.getString("request").startsWith("POST /relay/v1/training "),"correct route");
            check(captured.getString("authorization").equals("Bearer "+AgentCrypto.role(token,"device",room)),"device role authentication");
            check(captured.getString("site").equals("Bearer synthetic-token-for-tests"),"site authorization");
            check(body.getString("room").equals(room)&&body.getString("id").equals(id)&&!body.toString().contains("слово"),"request contains only encrypted samples");
            JSONObject plain=AgentCrypto.open(token,body.getJSONObject("envelope"),"folio:training:"+id);
            check(plain.getBoolean("if_missing")&&plain.getString("text").equals("слово")&&plain.getJSONArray("strokes").length()==1,"automatic mode and sample survive authenticated encryption");
            System.out.println("PASS "+checks+" encrypted foreground sample transport checks");
        }finally{worker.shutdownNow();}
    }
}
