package ru.folio.notes;

import android.content.SharedPreferences;
import java.util.Map;

/** Consent is local to this installation and never inherited by an imported or copied notebook. */
final class AiLearningPermissions {
    private static final String PREFIX="ai_learning_doc_";
    private AiLearningPermissions(){}
    static String key(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid notebook ID");return PREFIX+id;}
    static boolean enabled(SharedPreferences prefs,String id){return prefs.getBoolean(key(id),false);}
    static boolean allowed(SharedPreferences prefs,String id){return enabled(prefs,id)&&!prefs.getBoolean("trash_"+id,false);}
    static void set(SharedPreferences prefs,String id,boolean enabled){prefs.edit().putBoolean(key(id),enabled).apply();}
    static boolean anyAllowed(SharedPreferences prefs){for(Map.Entry<String,?> entry:prefs.getAll().entrySet())if(entry.getKey().startsWith(PREFIX)&&Boolean.TRUE.equals(entry.getValue())){String id=entry.getKey().substring(PREFIX.length());if(id.matches("[A-Za-z0-9_-]{1,80}")&&allowed(prefs,id))return true;}return false;}
}
