package ru.folio.notes;
import java.nio.file.*;import org.json.*;
public final class AiLearningQueueTests {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("folio-auto-learning");Path path=dir.resolve("queue.json");
        try{
            AiLearningQueue q=new AiLearningQueue(path.toFile());String id="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";JSONArray strokes=new JSONArray("[{points:[0,0,1,0,0,10,20,1,0,0]}]");
            JSONArray batch=new JSONArray().put(new JSONObject().put("id",id).put("text","Привет").put("strokes",strokes));
            check(q.record("doc","page","v1",batch,""),"page batch saved");check(q.analyzed("doc","page","v1"),"checkpoint saved");
            AiLearningQueue restored=new AiLearningQueue(path.toFile());check(restored.size()==1,"queue survives restart");
            check(restored.pendingFor("account-a").length()==1,"unbound sample binds to first account");check(restored.pendingFor("account-b").length()==0,"another account cannot receive it");
            restored.acknowledge(id);check(restored.size()==0,"acknowledgement removes only queue item");
            check(restored.record("doc","page","v2",batch,"account-a")&&restored.size()==0,"same strokes are not uploaded twice after reanalysis");
            check(!restored.analyzed("doc","page","v1")&&restored.analyzed("doc","page","v2"),"new checkpoint replaces old");
            String second="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";restored.record("private","p","v1",new JSONArray().put(new JSONObject().put("id",second).put("text","текст").put("strokes",strokes)),"account-a");
            restored.purgeRevoked(document->!document.equals("private"));check(restored.size()==0&&!restored.analyzed("private","p","v1"),"revocation deletes unsent samples and checkpoints");
            JSONArray large=new JSONArray();for(int i=0;i<AiLearningQueue.MAX_PENDING+1;i++)large.put(new JSONObject().put("id",String.format("%032x",i+1)).put("text","слово").put("strokes",strokes));
            check(!restored.record("doc","page","large",large,"account-a"),"a full queue leaves the page unfinished");check(restored.size()==AiLearningQueue.MAX_PENDING&&!restored.analyzed("doc","page","large"),"partial progress is persisted without a false checkpoint");
            restored.acknowledge(String.format("%032x",1));check(restored.record("doc","page","large",large,"account-a")&&restored.analyzed("doc","page","large"),"remaining sample resumes after capacity is available");
            Files.write(path,"broken".getBytes("UTF-8"));try{new AiLearningQueue(path.toFile()).record("doc","page","v3",batch,"");throw new AssertionError("corrupt queue overwritten");}catch(Exception expected){check(new String(Files.readAllBytes(path),"UTF-8").equals("broken"),"corrupt data preserved");}
            System.out.println("PASS "+checks+" durable checkpoints, account binding and idempotent queue checks");
        }finally{Files.deleteIfExists(path);Files.deleteIfExists(dir);}
    }
}
