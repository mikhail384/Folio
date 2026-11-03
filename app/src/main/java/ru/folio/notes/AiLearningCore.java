package ru.folio.notes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Conservative ink selection for automatic, opt-in handwriting analysis. */
final class AiLearningCore {
    static final float TILE=800f,MIN_CONFIDENCE=97f;
    private AiLearningCore(){}
    static boolean eligible(Ink.Page page,Ink.Stroke s){
        return page.visible(s)&&s.text.isEmpty()&&s.cuts.isEmpty()&&!s.action.startsWith("ai:")&&
            (s.tool.equals("pen")||s.tool.equals("fountain")||s.tool.equals("pencil")||s.tool.equals("brush"))&&!s.points.isEmpty();
    }
    static String label(String value,float confidence){
        if(!Float.isFinite(confidence)||confidence<MIN_CONFIDENCE||value==null)return null;
        String text=value.trim();if(text.length()<2||text.length()>32||!text.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}ʼ'’.-]*")||text.equals("__proto__")||text.equals("constructor")||text.equals("prototype"))return null;
        return text;
    }
    static ArrayList<Ink.Stroke> select(Ink.Page page,List<Ink.Stroke> strokes,float left,float top,float right,float bottom){
        ArrayList<Ink.Stroke> selected=new ArrayList<>();int points=0;
        for(Ink.Stroke s:strokes)if(eligible(page,s)){
            float[] b=s.bounds();if((b[0]+b[2])*.5f<left||(b[0]+b[2])*.5f>right||(b[1]+b[3])*.5f<top||(b[1]+b[3])*.5f>bottom)continue;
            int inside=0;for(Ink.Pt p:s.points)if(p.x>=left&&p.x<=right&&p.y>=top&&p.y<=bottom)inside++;
            if(inside*10<s.points.size()*9)continue;points+=s.points.size();if(points>16000||selected.size()>=128)return new ArrayList<>();selected.add(s);
        }
        return selected;
    }
    static JSONArray json(List<Ink.Stroke> strokes)throws Exception{JSONArray out=new JSONArray();for(Ink.Stroke s:strokes)out.put(s.json());return out;}
    static String hash(String value)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    static String pageFingerprint(Ink.Page page)throws Exception{JSONArray ink=new JSONArray();for(Ink.Stroke s:page.strokes)if(eligible(page,s))ink.put(s.json());return hash(ink.toString());}
    static String sampleId(String doc,String page,String text,JSONArray strokes)throws Exception{return hash(doc+"\n"+page+"\n"+text+"\n"+strokes.toString()).substring(0,32);}
    static final class Tile{
        final ArrayList<Ink.Stroke> strokes=new ArrayList<>();float l=Float.MAX_VALUE,t=Float.MAX_VALUE,r=-Float.MAX_VALUE,b=-Float.MAX_VALUE;
        void add(Ink.Stroke s){float[] x=s.bounds();l=Math.min(l,x[0]);t=Math.min(t,x[1]);r=Math.max(r,x[2]);b=Math.max(b,x[3]);strokes.add(s);}
    }
    static ArrayList<Tile> tiles(Ink.Page page){
        LinkedHashMap<String,Tile> result=new LinkedHashMap<>();for(Ink.Stroke s:page.strokes)if(eligible(page,s)){
            float[] b=s.bounds();if(b[2]-b[0]>TILE*1.5f||b[3]-b[1]>TILE*1.5f)continue;
            int x=(int)Math.floor(((double)b[0]+b[2])*.5/TILE),y=(int)Math.floor(((double)b[1]+b[3])*.5/TILE),part=0;String key=x+":"+y+":"+part;
            Tile tile=result.get(key);while(tile!=null&&tile.strokes.size()>=4000){key=x+":"+y+":"+(++part);tile=result.get(key);}if(tile==null){tile=new Tile();result.put(key,tile);}tile.add(s);
        }return new ArrayList<>(result.values());
    }
}
