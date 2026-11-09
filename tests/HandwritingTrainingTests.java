package ru.folio.notes;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

public final class HandwritingTrainingTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt task)throws Exception{try{task.run();throw new AssertionError("invalid sample accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("folio-training-test");File file=dir.resolve("training.json").toFile();
        try{
            HashSet<String> labels=new HashSet<>();
            for(HandwritingTraining.Group group:HandwritingTraining.GROUPS)for(String value:group.prompts){check(value.length()<=32&&labels.add(value),"valid unique exercise label");}
            for(String expected:new String[]{"Привет","Я записываю свои мысли.","2 + 2 = 4","H₂O","F = ma","а","A","2","≤","∑","∫","₉","ⁿ","∠","a/b = c/d"})check(labels.contains(expected),"all exercise categories and math symbols exist");
            for(int i=0;i<HandwritingTraining.MATH_SYMBOLS.length();){int cp=HandwritingTraining.MATH_SYMBOLS.codePointAt(i);String symbol=new String(Character.toChars(cp));check(labels.contains(symbol),"each supported mathematical sign has its own training prompt: "+symbol);i+=Character.charCount(cp);}
            JSONArray strokes=new JSONArray("[{points:[10,20,0.02,0,0,20,40,0.6,0,0],tool:'fountain'}]");
            HandwritingTraining store=new HandwritingTraining(file);store.saveDraft("Привет",strokes);
            HandwritingTraining reopened=new HandwritingTraining(file);check(reopened.draft("Привет").toString().equals(strokes.toString()),"draft survives restart");
            String first=reopened.save("Привет",strokes,"");check(reopened.draft("Привет").length()==0,"saved sample clears only its draft");
            JSONObject pending=reopened.next("https://example.test|account-a");check(first.equals(pending.getString("id")),"unpaired samples bind to first account");
            HandwritingTraining again=new HandwritingTraining(file);check(again.next("https://example.test|account-b")==null,"queued sample cannot leak to another account");
            check(again.pendingOther("https://example.test|account-b")==1,"other-account sample is reported");
            check("pending_other".equals(again.sampleStatus("Привет","https://example.test|account-b")),"status does not claim that another account has received the sample");
            rejects(()->again.save("Привет",strokes,"https://example.test|account-b"));
            check(first.equals(again.next("https://example.test|account-a").getString("id")),"saving to another account does not overwrite an unsent sample");
            String second=again.save("Привет",strokes,"https://example.test|account-a");
            check(!again.acknowledge(first),"an old network acknowledgement cannot clear a replacement");
            check(second.equals(again.next("https://example.test|account-a").getString("id")),"replacement remains pending");
            check(again.acknowledge(second),"matching acknowledgement commits");
            check(again.next("https://example.test|account-a")==null,"acknowledged sample is not uploaded again");
            check("synced".equals(new HandwritingTraining(file).info("Привет").getString("status")),"synchronization status survives restart");
            JSONArray timed=new JSONArray("[{points:[10,20,0.4,0,0,20,40,0.7,0,0],pointTimesMs:[0,37],tool:'fountain'}]");HandwritingTraining.validate(timed,"∑");String timedId=again.save("∑",timed,"https://example.test|account-a");check(timedId.equals(new HandwritingTraining(file).info("∑").getString("id"))&&new HandwritingTraining(file).info("∑").getJSONArray("strokes").getJSONObject(0).getJSONArray("pointTimesMs").getLong(1)==37,"stylus event timing survives the training database round trip");
            rejects(()->HandwritingTraining.validate(new JSONArray("[{points:[0,0,1,0,0,1,1,1,0,0],pointTimesMs:[0]}]"),"x"));
            rejects(()->HandwritingTraining.validate(new JSONArray("[{points:[0,0,1,0,0,1,1,1,0,0],pointTimesMs:[20,10]}]"),"x"));
            rejects(()->HandwritingTraining.validate(new JSONArray("[{points:[0,0,1,0,0],pointTimesMs:[600001]}]"),"x"));
            String prior=again.save("H₂O",strokes,"https://example.test|account-a");check(again.movePending("https://example.test|account-b")==2,"user can explicitly transfer all waiting text and math samples");
            JSONObject moved=again.next("https://example.test|account-b");check(moved!=null&&!prior.equals(moved.getString("id")),"moving assigns a new upload identity");
            check(!again.acknowledge(prior),"an old account cannot acknowledge the moved sample");
            rejects(()->again.save("П",new JSONArray(),""));rejects(()->again.save("П",new JSONArray("[{points:[0,0,1,0,0,5,0,1,0,0]}]"),""));
            Files.write(file.toPath(),"{not valid".getBytes("UTF-8"));
            HandwritingTraining corrupt=new HandwritingTraining(file);try{corrupt.save("П",strokes,"");throw new AssertionError("corrupt store overwritten");}catch(Exception expected){check(new String(Files.readAllBytes(file.toPath()),"UTF-8").equals("{not valid"),"corrupt data is preserved");}
            System.out.println("PASS "+checks+" handwriting prompts, offline drafts, durable samples, account binding and retry checks");
        }finally{Files.deleteIfExists(file.toPath());Files.deleteIfExists(dir);}
    }
}
