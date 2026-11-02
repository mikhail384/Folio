import ru.focusflow.pen.*;import org.json.*;
public class AndroidLogicTest {
    static int checks;static void ok(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    static void reject(String value,boolean path)throws Exception{boolean rejected=false;try{if(path)ScreenPath.validate(new JSONObject(value));else Config.validate(new JSONObject(value));}catch(JSONException e){rejected=true;}ok(rejected);}
    public static void main(String[] args)throws Exception{
        JSONObject legacy=Config.validate(new JSONObject("{actions:[{type:home,label:Home},{type:lock,label:Lock}],swipeWindowMs:450}"));ok(legacy.getJSONObject("mappings").getJSONArray("squeeze_double").length()==2);ok(legacy.getInt("swipeWindowMs")==1000);ok(legacy.getInt("schemaVersion")==2);
        JSONObject c=Config.defaults();c.put("customGestures",new JSONArray("[{id:custom_test,label:Mixed,steps:[pinch,up,down],gapMs:1500}]"));c.getJSONObject("mappings").put("custom_test",new JSONArray("[{type:back,label:Back}]"));JSONObject safe=Config.validate(c);ok(safe.getJSONObject("mappings").getJSONArray("custom_test").length()==1);ok(safe.getJSONArray("customGestures").getJSONObject(0).getInt("gapMs")==1500);
        reject("{customGestures:[{id:custom_x,label:X,steps:[up,down]}]}",false);reject("{customGestures:[{id:custom_x,label:X,steps:[left,right]}]}",false);
        JSONObject path=ScreenPath.validate(new JSONObject("{strokes:[{points:[[10,80],[90,20]],startMs:0,durationMs:650},{points:[[50,50]],startMs:900,durationMs:100}]}"));ok(path.getJSONArray("strokes").length()==2);
        reject("{strokes:[{points:[[101,10]],startMs:0,durationMs:500}]}",true);reject("{strokes:[{points:[[10,10]],startMs:0,durationMs:0}]}",true);reject("{strokes:[{points:[[10,10]],startMs:30000,durationMs:500}]}",true);reject("{strokes:[{points:[],startMs:0,durationMs:500}]}",true);reject("{strokes:[{points:[[10,10]],startMs:0,durationMs:500},{points:[[10,10]],startMs:100,durationMs:100}]}",true);
        JSONObject a=new JSONObject().put("type","screen_path").put("label","Path").put("value",path.toString());c.getJSONObject("mappings").put("swipe_up",new JSONArray().put(a));ok(Config.validate(c).getJSONObject("mappings").getJSONArray("swipe_up").length()==1);
        ok(Config.defaults().getBoolean("fastResponse"));ok(legacy.getBoolean("fastResponse"));
        safe.put("fastResponse",false);JSONObject saved=Config.validate(safe);ok(!saved.getBoolean("fastResponse"));ok(saved.getJSONArray("customGestures").getJSONObject(0).getInt("gapMs")==1500);
        System.out.println("PASS: "+checks+" Android config migration, custom sequence and screen-path validation assertions");
    }
}
