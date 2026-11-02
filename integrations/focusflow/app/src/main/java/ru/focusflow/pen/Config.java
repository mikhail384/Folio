package ru.focusflow.pen;

import android.content.Context;
import org.json.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public final class Config {
    public static final Set<String> TYPES = new HashSet<>(Arrays.asList("home","back","recents","notifications","quick_settings","screenshot","lock","power","volume_up","volume_down","play_pause","next_track","previous_track","launch_app","open_url","wait","tap","swipe_up","swipe_down","swipe_custom","screen_path","widget","shortcut","app_activity"));
    public static JSONObject defaults() {
        try {
            JSONObject c=new JSONObject("{\"schemaVersion\":2,\"enabled\":true,\"intercept\":false,\"haptic\":true,\"fastResponse\":true,\"windowMs\":1000,\"swipeWindowMs\":1000,\"customGestures\":[],\"actions\":[{\"type\":\"home\",\"label\":\"На главный экран\"}]}");
            JSONObject mappings=new JSONObject();for(String id:Gestures.ALL.keySet())mappings.put(id,new JSONArray());
            mappings.put("squeeze_double",c.getJSONArray("actions"));return c.put("mappings",mappings);
        }
        catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public static JSONObject load(Context c) {
        try { return validate(new JSONObject(c.getSharedPreferences("focusflow",0).getString("config",defaults().toString()))); }
        catch (Exception e) { return defaults(); }
    }
    public static JSONObject validate(JSONObject input) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("nativePenOnly",input.optBoolean("nativePenOnly",true));
        boolean migrate=input.optInt("schemaVersion",0)<2;out.put("schemaVersion",2);
        out.put("enabled", input.optBoolean("enabled", true));
        out.put("intercept", input.optBoolean("intercept", false));
        out.put("haptic", input.optBoolean("haptic",true));
        out.put("fastResponse", input.optBoolean("fastResponse",true));
        out.put("windowMs", Math.max(migrate?1000:200, Math.min(3000, input.optInt("windowMs",1000))));
        out.put("swipeWindowMs",Math.max(migrate?1000:200,Math.min(3000,input.optInt("swipeWindowMs",1000))));
        JSONArray custom=input.optJSONArray("customGestures"),safeCustom=new JSONArray();Set<String> ids=new HashSet<>();Set<String> patterns=new HashSet<>();
        if(custom!=null){if(custom.length()>30)throw new JSONException("Не более 30 своих жестов");
            for(int i=0;i<custom.length();i++){
                JSONObject g=custom.getJSONObject(i);String id=g.getString("id"),label=g.optString("label","Мой жест").trim();JSONArray steps=g.getJSONArray("steps");
                if(!id.matches("custom_[A-Za-z0-9_-]{1,60}")||!ids.add(id))throw new JSONException("Некорректный ID жеста");
                if(label.isEmpty()||label.length()>80||steps.length()<1||steps.length()>12)throw new JSONException("В жесте нужно от 1 до 12 движений");
                for(int j=0;j<steps.length();j++)if(!Arrays.asList("pinch","up","down").contains(steps.getString(j)))throw new JSONException("Неизвестное движение");
                List<String> sequence=new java.util.ArrayList<>();for(int j=0;j<steps.length();j++)sequence.add(steps.getString(j));for(String builtin:Gestures.ALL.keySet())if(Gestures.steps(builtin).equals(sequence))throw new JSONException("Эта комбинация уже есть среди готовых жестов");
                if(!patterns.add(steps.toString()))throw new JSONException("Такая последовательность уже есть");
                safeCustom.put(new JSONObject().put("id",id).put("label",label).put("steps",steps).put("gapMs",Math.max(200,Math.min(3000,g.optInt("gapMs",1200)))));
            }
        }out.put("customGestures",safeCustom);
        JSONObject source=input.optJSONObject("mappings"),mappings=new JSONObject();
        for(String id:Gestures.ALL.keySet()){
            JSONArray list=source==null?null:source.optJSONArray(id);
            if(list==null&&id.equals("squeeze_double"))list=input.optJSONArray("actions");
            mappings.put(id,sanitize(list==null?new JSONArray():list));
        }
        for(String id:ids){JSONArray list=source==null?null:source.optJSONArray(id);mappings.put(id,sanitize(list==null?new JSONArray():list));}
        return out.put("mappings",mappings).put("actions",mappings.getJSONArray("squeeze_double"));
    }
    private static JSONArray sanitize(JSONArray list)throws JSONException{
        JSONArray safe = new JSONArray();
        if (list.length() > 20) throw new JSONException("Не более 20 действий");
        for(int i=0;i<list.length();i++) {
            JSONObject a = list.getJSONObject(i);
            String type = a.getString("type");
            if(!TYPES.contains(type)) throw new JSONException("Неизвестное действие");
            JSONObject s = new JSONObject().put("type",type).put("label",a.optString("label",type).substring(0,Math.min(100,a.optString("label",type).length())));
            String value = a.optString("value","");
            if(value.length()>(Arrays.asList("screen_path","swipe_custom","shortcut").contains(type)?65536:2048)) throw new JSONException("Слишком длинное значение");
            if(type.equals("widget")&&!value.matches("[1-9][0-9]{0,8}"))throw new JSONException("Выберите виджет");
            if(type.equals("app_activity")&&!value.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))throw new JSONException("Выберите действие приложения");
            if(type.equals("shortcut")){JSONArray intents=new JSONArray(value);if(intents.length()<1||intents.length()>8)throw new JSONException("Некорректный ярлык");for(int j=0;j<intents.length();j++)if(!intents.getString(j).contains("#Intent;"))throw new JSONException("Некорректный ярлык");}
            if(type.equals("screen_path")||type.equals("swipe_custom"))value=ScreenPath.validate(new JSONObject(value)).toString();
            if(type.equals("open_url") && !value.matches("(?i)^https?://[^\\s]+$")) throw new JSONException("Нужен адрес https:// или http://");
            if(type.equals("launch_app") && !value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) throw new JSONException("Выберите приложение");
            if(type.equals("wait")) value = String.valueOf(Math.max(100,Math.min(10000,parse(value,500))));
            if(type.equals("tap")) {
                String[] xy=value.split(",");
                if(xy.length!=2) throw new JSONException("Укажите X,Y в процентах");
                try { for(String p:xy) if(Float.parseFloat(p)<0 || Float.parseFloat(p)>100 || !Float.isFinite(Float.parseFloat(p))) throw new Exception(); }
                catch(Exception e) { throw new JSONException("Координаты должны быть от 0 до 100"); }
            }
            s.put("value",value); safe.put(s);
        }
        return safe;
    }
    private static int parse(String s,int fallback) { try{return Integer.parseInt(s);}catch(Exception e){return fallback;} }
    public static void save(Context c, JSONObject config) throws JSONException {
        c.getSharedPreferences("focusflow",0).edit().putString("config",validate(config).toString()).apply();
    }
}
