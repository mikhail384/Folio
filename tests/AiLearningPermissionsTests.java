package ru.folio.notes;
import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;
public final class AiLearningPermissionsTests {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        Map<String,Boolean> values=new HashMap<>();
        SharedPreferences.Editor editor=(SharedPreferences.Editor)Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class[]{SharedPreferences.Editor.class},(proxy,method,v)->{
            if(method.getName().equals("putBoolean")){values.put((String)v[0],(Boolean)v[1]);return proxy;}if(method.getName().equals("apply"))return null;if(method.getName().equals("commit"))return true;return proxy;
        });
        SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,v)->{
            if(method.getName().equals("getBoolean"))return values.getOrDefault(v[0],(Boolean)v[1]);if(method.getName().equals("edit"))return editor;if(method.getName().equals("getAll"))return values;return null;
        });
        String a="notebook-1",b="notebook-2";check(!AiLearningPermissions.allowed(prefs,a),"old notebooks default to no consent");
        AiLearningPermissions.set(prefs,a,true);check(AiLearningPermissions.allowed(prefs,a),"explicit consent works");check(AiLearningPermissions.anyAllowed(prefs),"one approved notebook enables analysis");check(!AiLearningPermissions.allowed(prefs,b),"consent does not leak to another notebook");
        values.put("trash_"+a,true);check(AiLearningPermissions.enabled(prefs,a)&&!AiLearningPermissions.allowed(prefs,a),"trash is excluded without losing reversible consent");check(!AiLearningPermissions.anyAllowed(prefs),"trashed notebooks do not start analysis");
        values.put("trash_"+a,false);check(AiLearningPermissions.allowed(prefs,a),"restored notebook is eligible again");AiLearningPermissions.set(prefs,a,false);check(!AiLearningPermissions.allowed(prefs,a),"revocation takes effect immediately");
        boolean failed=false;try{AiLearningPermissions.key("../other");}catch(IllegalArgumentException expected){failed=true;}check(failed,"invalid ID rejected");
        System.out.println("PASS "+checks+" per-notebook default, isolation, trash and revocation checks");
    }
}
