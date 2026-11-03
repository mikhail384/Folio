package ru.folio.notes;
import java.nio.file.*;import java.util.*;import org.json.*;

public final class AiCorpusStoreTests {
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static String id(int n){return String.format(Locale.ROOT,"%032x",n);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("folio-corpus-tests");
        try{
            AiCorpusStore c=new AiCorpusStore(root.toFile());JSONArray strokes=new JSONArray("[{points:[0,0,.1,0,0,10,20,.4,0,0]}]");
            check(c.save("doc","page",id(1),"А",strokes,"ocr","text","",()->true),"first sample saved");
            check(c.save("doc","page",id(2),"а",strokes,"ocr","text","",()->true),"different case retained");
            check(!c.save("doc","page",id(2),"а",strokes,"ocr","text","",()->true),"retry is idempotent");
            check(!c.save("doc","page",id(3),"нет",strokes,"ocr","text","",()->false),"revoked consent prevents saving");
            JSONObject first=c.page("doc","",1,"account-a",()->true);check(first.getJSONArray("examples").length()==1&&first.getBoolean("has_more"),"bounded page");
            String next=first.getString("next_cursor");JSONObject second=c.page("doc",next,1,"account-a",()->true);check(second.getJSONArray("examples").length()==1&&!second.getBoolean("has_more"),"cursor returns next sample");
            Set<String> labels=new HashSet<>();labels.add(first.getJSONArray("examples").getJSONObject(0).getString("text"));labels.add(second.getJSONArray("examples").getJSONObject(0).getString("text"));check(labels.contains("А")&&labels.contains("а"),"uppercase and lowercase remain distinct");
            check(!first.getJSONArray("examples").getJSONObject(0).getBoolean("reviewed"),"OCR labels require review");
            check(c.page("doc","",8,"account-b",()->true).getJSONArray("examples").length()==0,"first account binding prevents cross-account export");
            try{c.page("doc","",8,"account-a",()->false);throw new AssertionError("revoked permission ignored");}catch(SecurityException expected){checks++;}
            check(c.save("calibration","calibration",id(4),"F = ma",strokes,"calibration","formula","account-a",()->true),"calibration saved");
            JSONObject f=c.page("calibration","",8,"account-a",()->true).getJSONArray("examples").getJSONObject(0);check(f.getBoolean("reviewed")&&f.getString("kind").equals("formula"),"formula metadata retained");
            check(new AiCorpusStore(root.toFile()).page("calibration","",8,"account-a",()->true).getJSONArray("examples").length()==1,"examples survive restart");
            check(c.transfer("account-b",()->true,d->false,false)==1,"transfer preview excludes revoked notebooks");
            check(c.transfer("account-b",()->true,d->false,true)==1,"explicit transfer moves calibration only");
            check(c.page("calibration","",8,"account-b",()->true).getJSONArray("examples").length()==1,"transferred calibration available to new account");
            check(c.page("doc","",8,"account-b",()->true).getJSONArray("examples").length()==0,"revoked notebook stayed on old account");
            check(c.transfer("account-b",()->true,d->d.equals("doc"),true)==2,"allowed notebook can be explicitly transferred");
            check(c.page("doc","",8,"account-b",()->true).getJSONArray("examples").length()==2,"both case-sensitive examples transferred");
            try{c.transfer("account-c",()->false,d->true,true);throw new AssertionError("changed account accepted");}catch(SecurityException expected){checks++;}
            try{c.page("../escape","",1,"account-a",()->true);throw new AssertionError("path traversal accepted");}catch(IllegalArgumentException expected){checks++;}
            System.out.println("PASS "+checks+" case, repeated examples, durable private corpus, consent, binding, formula and paging checks");
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(root)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){throw new RuntimeException(e);}});}}
    }
}
