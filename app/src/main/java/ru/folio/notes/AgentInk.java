package ru.folio.notes;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Validates a complete agent transaction before touching the page. No Android dependencies. */
public final class AgentInk {
    private AgentInk(){}
    public static String action(String operation,JSONArray raw)throws Exception {
        if(!operation.matches("[A-Za-z0-9_-]{8,80}"))throw new IllegalArgumentException("Invalid operation_id");
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder tag=new StringBuilder("ai:"+operation+":");for(int i=0;i<8;i++)tag.append(String.format(Locale.ROOT,"%02x",hash[i]&255));return tag.toString();
    }
    static float number(JSONArray a,int i){double n=a.optDouble(i,Double.NaN);if(!Double.isFinite(n)||Math.abs(n)>10000000)throw new IllegalArgumentException("Invalid point coordinate");return (float)n;}
    public static ArrayList<Ink.Stroke> prepare(JSONArray raw,String action,String layer)throws Exception {
        if(raw.length()==0||raw.length()>2000)throw new IllegalArgumentException("Expected 1..2000 strokes");
        ArrayList<Ink.Stroke> result=new ArrayList<>();int points=0;
        for(int i=0;i<raw.length();i++){
            JSONObject item=raw.getJSONObject(i);Ink.Stroke s=new Ink.Stroke();s.action=action;s.layer=layer;
            s.tool=item.optString("tool","pen");if(!Arrays.asList("pen","fountain","pencil","brush","marker").contains(s.tool))throw new IllegalArgumentException("Unsupported drawing tool");
            String color=item.optString("color","#28344b");if(!color.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Expected #RRGGBB color");s.color=(int)(0xff000000L|Long.parseLong(color.substring(1),16));
            double width=item.optDouble("width",2.2);if(!Double.isFinite(width)||width<.3||width>80)throw new IllegalArgumentException("Width must be 0.3..80");s.width=(float)width;
            JSONArray path=item.getJSONArray("points");points+=path.length();if(path.length()<1||points>100000)throw new IllegalArgumentException("Expected 1..100000 points in a batch");
            for(int n=0;n<path.length();n++){JSONArray p=path.getJSONArray(n);if(p.length()<2||p.length()>4)throw new IllegalArgumentException("A point is [x,y,pressure?,roll?]");float x=number(p,0),y=number(p,1),pressure=p.length()>2?number(p,2):1,roll=p.length()>3?number(p,3):0;if(pressure<.02f||pressure>2f||Math.abs(roll)>(float)(Math.PI*2))throw new IllegalArgumentException("Invalid pressure or roll");s.points.add(new Ink.Pt(x,y,pressure,0,roll,0));}
            result.add(s);
        }return result;
    }
    public static String existing(Ink.Page page,String operation){String prefix="ai:"+operation+":";for(Ink.Stroke s:page.strokes)if(s.action.startsWith(prefix))return s.action;return null;}
    public static int remove(Ink.Page page,String action){int before=page.strokes.size();page.strokes.removeIf(s->s.action.equals(action));return before-page.strokes.size();}
}
