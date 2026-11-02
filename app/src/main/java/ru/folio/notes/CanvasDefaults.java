package ru.folio.notes;

import android.content.SharedPreferences;
import java.util.*;

/** Only blank-paper settings are copied. Document IDs, ink, assets and camera stay independent. */
final class CanvasDefaults {
    static final String PREFIX="canvas_default_";
    static final String[] FIELDS={"infinite","width","height","paper","lines","pattern","spacing"};
    static Ink.Page read(SharedPreferences prefs){return read(prefs.getAll());}
    static Ink.Page read(Map<String,?> values){
        Ink.Page page=new Ink.Page();Object infinite=values.get(PREFIX+"infinite");if(infinite instanceof Boolean)page.infinite=(Boolean)infinite;
        float w=number(values,"width",page.width),h=number(values,"height",page.height);if(PaperSizes.valid(w,h)){page.width=w;page.height=h;}
        Object paper=values.get(PREFIX+"paper"),lines=values.get(PREFIX+"lines"),pattern=values.get(PREFIX+"pattern");
        if(paper instanceof Integer)page.paper=(Integer)paper|0xff000000;if(lines instanceof Integer)page.lines=(Integer)lines;
        if(pattern instanceof String&&Arrays.asList("blank","grid","lined","dots").contains(pattern))page.pattern=(String)pattern;
        float spacing=number(values,"spacing",page.spacing);if(Float.isFinite(spacing))page.spacing=Ink.clamp(spacing,8,160);
        return page;
    }
    static float number(Map<String,?> values,String field,float fallback){Object n=values.get(PREFIX+field);return n instanceof Number?((Number)n).floatValue():fallback;}
    static Map<String,Object> values(Ink.Page page){
        Map<String,Object> values=new HashMap<>();values.put(PREFIX+"infinite",page.infinite);values.put(PREFIX+"width",page.width);values.put(PREFIX+"height",page.height);values.put(PREFIX+"paper",page.paper);values.put(PREFIX+"lines",page.lines);values.put(PREFIX+"pattern",page.pattern);values.put(PREFIX+"spacing",page.spacing);return values;
    }
    static void save(SharedPreferences prefs,Ink.Page page){
        Ink.Page clean=read(values(page));SharedPreferences.Editor edit=prefs.edit();
        edit.putBoolean(PREFIX+"infinite",clean.infinite).putFloat(PREFIX+"width",clean.width).putFloat(PREFIX+"height",clean.height).putInt(PREFIX+"paper",clean.paper).putInt(PREFIX+"lines",clean.lines).putString(PREFIX+"pattern",clean.pattern).putFloat(PREFIX+"spacing",clean.spacing).apply();
    }
    static void reset(SharedPreferences prefs){SharedPreferences.Editor edit=prefs.edit();for(String field:FIELDS)edit.remove(PREFIX+field);edit.apply();}
}
