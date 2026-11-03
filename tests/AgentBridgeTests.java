package ru.folio.notes;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
public final class AgentBridgeTests {
    static int checks;static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static String token="ttttttttttttttttttttttttttttttttttttttttttt";
    static String post(String route,String body,String auth,String extra)throws Exception{
        try(Socket socket=new Socket("127.0.0.1",AgentBridge.PORT)){socket.setSoTimeout(3000);byte[] data=body.getBytes(StandardCharsets.UTF_8);String header="POST /v1/"+route+" HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nAuthorization: Bearer "+auth+"\r\nContent-Length: "+data.length+"\r\n"+extra+"\r\n";socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(data);socket.getOutputStream().flush();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=socket.getInputStream().read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}
    }
    static JSONObject data(String response)throws Exception{return new JSONObject(response.substring(response.indexOf("\r\n\r\n")+4));}
    public static void main(String[] args)throws Exception{
        MainActivity a=new MainActivity();AgentBridge bridge=new AgentBridge(a);bridge.resume();check(bridge.server!=null,"listener started");
        try{
            check(post("state","{}","wrong","").startsWith("HTTP/1.1 401"),"pairing key required");
            check(post("state","{}",token,"Origin: https://evil.invalid\r\n").startsWith("HTTP/1.1 403"),"browser origins rejected");
            JSONObject state=data(post("state","{}",token,""));check(state.getString("document_id").equals(a.current.id),"state describes actual document");
            JSONObject q=new JSONObject().put("document_id",a.current.id).put("page_id",a.canvas.page.id).put("revision",state.getString("revision")).put("operation_id","network_test_123").put("strokes",new JSONArray("[{points:[[1,2],[3,4]],color:'#123456'}]"));
            Ink.Stroke user=new Ink.Stroke();a.canvas.page.strokes.add(user);
            JSONObject applied=data(post("ink",q.toString(),token,""));check(applied.getInt("added_strokes")==1&&a.canvas.page.strokes.size()==2,"network write reaches actual ink model");check(a.saves==1&&a.canvas.checkpoints==1,"one save and undo checkpoint");
            JSONObject retry=data(post("ink",q.toString(),token,""));check(retry.getBoolean("already_applied")&&a.canvas.page.strokes.size()==2,"exact retry is idempotent despite old revision");
            q.put("strokes",new JSONArray("[{points:[[9,9]]}]"));check(post("ink",q.toString(),token,"").startsWith("HTTP/1.1 409"),"operation ID reuse with different data rejected");
            q.put("operation_id","network_test_456");check(post("ink",q.toString(),token,"").startsWith("HTTP/1.1 409"),"stale revision rejected");
            q.put("revision",bridge.revision()).put("strokes",new JSONArray("[{points:[[1,2]]},{points:[[1e100,2]]}]"));check(post("ink",q.toString(),token,"").startsWith("HTTP/1.1 400"),"invalid final stroke rejected atomically");check(a.canvas.page.strokes.size()==2,"no partial batch");
            q.put("operation_id","network_test_123");JSONObject undone=data(post("undo",q.toString(),token,""));check(undone.getInt("removed_strokes")==1&&a.canvas.page.strokes.get(0)==user,"network undo keeps user strokes");
            a.canvas.penId=3;check(post("snapshot","{}",token,"").startsWith("HTTP/1.1 409"),"active pen gesture blocks snapshot/write");a.canvas.penId=-1;
            a.setupOpen=true;check(post("state","{}",token,"").startsWith("HTTP/1.1 409"),"setup screen cannot be mistaken for an open canvas");a.setupOpen=false;
            a.resumed=false;check(post("state","{}",token,"").startsWith("HTTP/1.1 409"),"background activity denies access");
        }finally{bridge.pause();}
        check(bridge.server==null,"listener closed on pause");System.out.println("PASS "+checks+" real LAN HTTP/auth/origin/foreground/version/idempotency/atomic ink/selective undo checks; Android UI and persistence substituted");
    }
}
