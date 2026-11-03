package ru.folio.notes;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.*;
import org.json.*;

public final class TrainingUploadTests {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]),room="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",service="test-service-token";
        JSONObject preview=new JSONObject().put("transport","relay").put("url",AgentEndpoints.TEST_VPS).put("room",room).put("token",token).put("service_token",service);
        check(AgentCloud.validate(preview.toString()).getString("url").equals(AgentEndpoints.TEST_VPS),"new HTTPS VPS can be paired");
        preview.put("url","https://109.66.236.217:17444");try{AgentCloud.validate(preview.toString());throw new AssertionError("unexpected origin accepted");}catch(IllegalArgumentException expected){}
        Path dir=Files.createTempDirectory("folio-upload-test");File file=dir.resolve("training.json").toFile();
        MainActivity a=new MainActivity();a.handwritingTraining=new HandwritingTraining(file);AgentBridge bridge=new AgentBridge(a);bridge.epoch=7;bridge.cloud.running=true;
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger attempts=new AtomicInteger();
        server.createContext("/relay/v1/training",exchange->{try{
            check(("Bearer "+AgentCrypto.role(token,"device",room)).equals(exchange.getRequestHeaders().getFirst("Authorization")),"device role authentication");
            check(("Bearer "+service).equals(exchange.getRequestHeaders().getFirst("OAI-Sites-Authorization")),"service authentication");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;while((n=exchange.getRequestBody().read(buffer))!=-1)bytes.write(buffer,0,n);
            JSONObject body=new JSONObject(bytes.toString("UTF-8"));String id=body.getString("id");check(room.equals(body.getString("room")),"correct pairing room");
            JSONObject payload=AgentCrypto.open(token,body.getJSONObject("envelope"),"folio:training:"+id);
            check("H₂O".equals(payload.getString("text"))&&payload.getJSONArray("strokes").length()==1,"encrypted user sample reaches service");
            boolean fail=attempts.getAndIncrement()==0;byte[] answer=(fail?"{}":new JSONObject().put("id",id).put("stored",true).toString()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(fail?503:200,answer.length);exchange.getResponseBody().write(answer);exchange.close();
        }catch(Exception e){try{exchange.sendResponseHeaders(500,-1);exchange.close();}catch(Exception ignored){}}});
        server.start();
        try{
            String origin="http://127.0.0.1:"+server.getAddress().getPort(),target=origin+"|"+room;
            JSONArray strokes=new JSONArray("[{points:[10,20,0.02,0,0,20,40,0.8,0,0],tool:'fountain'}]");
            String id=a.handwritingTraining.save("H₂O",strokes,target);JSONObject config=new JSONObject().put("url",origin).put("room",room).put("token",token).put("service_token",service);
            try{bridge.cloud.uploadTraining(config,7);throw new AssertionError("temporary failure was accepted");}catch(IOException expected){}
            check(id.equals(a.handwritingTraining.next(target).getString("id")),"failed upload stays queued");
            check(bridge.cloud.uploadTraining(config,7),"retry sent");check(a.handwritingTraining.next(target)==null,"successful acknowledgement clears queue");
            check("synced".equals(new HandwritingTraining(file).sampleStatus("H₂O")),"success persists after restart");
            check(attempts.get()==2,"exactly one retry was made");
            System.out.println("PASS encrypted Android upload, device authentication, failed retry and durable acknowledgement over real HTTP");
        }finally{server.stop(0);Files.deleteIfExists(file.toPath());Files.deleteIfExists(dir);}
    }
}
