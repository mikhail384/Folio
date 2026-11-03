package ru.folio.notes;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class AgentTrainingDispatchTests {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static String repeat(String s,int count){return String.join("",Collections.nCopies(count,s));}
    public static void main(String[] args)throws Exception{
        ExecutorService ui=Executors.newSingleThreadExecutor(r->new Thread(r,"simulated-ui"));
        Path root=Files.createTempDirectory("folio-dispatch-test");
        try{
            MainActivity a=new MainActivity(new MainActivity.Ui(){void post(Runnable r){ui.execute(r);}});a.filesDir=root.toFile();a.docs.add(a.current);
            List<String> reads=Collections.synchronizedList(new ArrayList<>());boolean[] permission={true};
            String config=new JSONObject().put("url",AgentEndpoints.SITE).put("transport","relay").put("room",repeat("a",32)).put("token",repeat("A",43)).put("service_token","test-service-token").toString();
            a.prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(o,m,x)->{
                if(m.getName().equals("getString")){if(x[0].equals("agent_cloud")){reads.add(Thread.currentThread().getName());return config;}return x[1];}
                if(m.getName().equals("getBoolean")){String key=(String)x[0];return key.equals("agent_enabled")||key.startsWith("ai_learning_doc_")&&permission[0];}
                return null;
            });
            AgentBridge bridge=new AgentBridge(a);bridge.epoch=7;String doc=a.current.id;String target=AgentEndpoints.SITE+"|"+repeat("a",32);
            JSONArray strokes=new JSONArray("[{points:[0,0,.1,0,0,10,20,.4,0,0]}]");
            new AiCorpusStore(root.resolve("ai-training-corpus").toFile()).save(doc,"page",repeat("1",32),"Привет",strokes,"calibration","text",target,()->true);
            JSONObject out=bridge.dispatch("/v1/training_examples",new JSONObject().put("document_id",doc),7);
            check(out.getJSONArray("examples").length()==1,"reviewed example returned");
            check(reads.contains("simulated-ui")&&reads.contains(Thread.currentThread().getName()),"authorization on UI, corpus checks on connection worker");
            permission[0]=false;
            try{bridge.dispatch("/v1/training_examples",new JSONObject().put("document_id",doc),7);throw new AssertionError("revoked permission ignored");}catch(AgentBridge.Fault expected){check(expected.code==403,"revoked access returns 403");}
            permission[0]=true;bridge.epoch++;
            try{bridge.dispatch("/v1/training_examples",new JSONObject().put("document_id",doc),7);throw new AssertionError("old connection accepted");}catch(AgentBridge.Fault expected){check(expected.code==409,"stale connection rejected");}
            System.out.println("PASS reviewed corpus dispatch off UI thread, consent revocation and stale connection checks");
        }finally{ui.shutdownNow();try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
}
