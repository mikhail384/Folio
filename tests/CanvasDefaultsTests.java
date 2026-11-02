package ru.folio.notes;

import android.content.SharedPreferences;
import java.util.*;

/** Production default loading/saving against a memory preferences transport; not an Android runtime. */
public final class CanvasDefaultsTests {
    static int assertions;static final Random random=new Random(1921);
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
    static void style(Ink.Page a,Ink.Page b){check(a.infinite==b.infinite&&a.width==b.width&&a.height==b.height,"size mode and dimensions round trip");check(a.paper==b.paper&&a.lines==b.lines&&a.pattern.equals(b.pattern)&&a.spacing==b.spacing,"paper and markup round trip");}
    static final class Prefs implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        public Map<String,?> getAll(){return new HashMap<>(values);}public boolean contains(String k){return values.containsKey(k);}
        public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}@SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return values.containsKey(k)?(Set<String>)values.get(k):d;}
        public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public long getLong(String k,long d){return values.containsKey(k)?(Long)values.get(k):d;}public float getFloat(String k,float d){return values.containsKey(k)?(Float)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public Editor edit(){return new Editor(){final Map<String,Object> pending=new HashMap<>();boolean clear;
            public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}public Editor putInt(String k,int v){pending.put(k,v);return this;}public Editor putLong(String k,long v){pending.put(k,v);return this;}public Editor putFloat(String k,float v){pending.put(k,v);return this;}public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}public Editor remove(String k){pending.put(k,null);return this;}public Editor clear(){clear=true;return this;}
            public void apply(){commit();}public boolean commit(){if(clear)values.clear();for(Map.Entry<String,Object> e:pending.entrySet())if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());return true;}
        };}
    }
    public static void main(String[] args){
        Prefs prefs=new Prefs();style(CanvasDefaults.read(prefs),new Ink.Page());prefs.edit().putString("gesture_squeeze","undo").putInt("ink_pen",0xff123456).apply();
        for(int n=0;n<1200;n++){
            Ink.Page source=new Ink.Page();source.infinite=n%2==0;source.width=200+random.nextInt(9801);source.height=200+random.nextInt(9801);source.paper=0xff000000|random.nextInt(0xffffff);source.lines=random.nextInt();source.pattern=new String[]{"blank","grid","lined","dots"}[n%4];source.spacing=8+random.nextInt(153);source.background="example.png";source.hasViewport=true;source.viewX=-50000;source.viewZoom=3;source.strokes.add(new Ink.Stroke());
            Map<String,Object> encoded=CanvasDefaults.values(source);check(encoded.size()==7,"only seven blank paper settings are persisted");CanvasDefaults.save(prefs,source);Ink.Page a=CanvasDefaults.read(prefs),b=CanvasDefaults.read(prefs);style(a,source);style(b,source);check(!a.id.equals(b.id)&&!a.id.equals(source.id),"each new canvas has its own identity");check(a.background.isEmpty()&&a.strokes.isEmpty()&&!a.hasViewport&&a.viewZoom==1,"ink, images and camera never copied to new canvases");
            a.paper=0xff000000;a.strokes.add(new Ink.Stroke());check(b.strokes.isEmpty()&&source.strokes.size()==1,"new pages do not share mutable ink");style(CanvasDefaults.read(prefs),source);check(prefs.getString("gesture_squeeze","").equals("undo")&&prefs.getInt("ink_pen",0)==0xff123456,"saving defaults preserves gestures and tool colors");
        }
        Ink.Page original=new Ink.Page();Map<String,Object> bad=new HashMap<>();bad.put(CanvasDefaults.PREFIX+"width",Float.NaN);bad.put(CanvasDefaults.PREFIX+"height",20000);bad.put(CanvasDefaults.PREFIX+"pattern","broken");bad.put(CanvasDefaults.PREFIX+"spacing",Float.POSITIVE_INFINITY);bad.put(CanvasDefaults.PREFIX+"paper","wrong type");bad.put(CanvasDefaults.PREFIX+"infinite",123);style(CanvasDefaults.read(bad),original);check(bad.size()==6,"reading preferences is non-mutating");
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-4,199,10001}){bad.put(CanvasDefaults.PREFIX+"width",invalid);Ink.Page page=CanvasDefaults.read(bad);check(page.width==1200&&page.height==1640,"invalid dimensions fall back safely");}
        bad.clear();bad.put(CanvasDefaults.PREFIX+"spacing",2);check(CanvasDefaults.read(bad).spacing==8,"minimum spacing bounded");bad.put(CanvasDefaults.PREFIX+"spacing",999);check(CanvasDefaults.read(bad).spacing==160,"maximum spacing bounded");
        CanvasDefaults.reset(prefs);style(CanvasDefaults.read(prefs),original);check(prefs.getAll().size()==2&&prefs.getString("gesture_squeeze","").equals("undo")&&prefs.getInt("ink_pen",0)==0xff123456,"reset removes exactly defaults and preserves unrelated settings");
        System.out.println("PASS "+assertions+" canvas-default assertions: persistence, isolated new pages, original style retention, malformed preferences and scoped reset; desktop memory transport only");
    }
}
