package ru.folio.penbridge;

import android.os.*;
import java.io.*;
import java.lang.reflect.*;

/** Separate Binder connection per app. Neither app gets shell access to its peer. */
final class ShellClient {
    final String authority;Object manager,provider;IBinder token;String reported="";
    ShellClient(String authority){this.authority=authority;}
    synchronized Bundle call(String method,String arg,Bundle extras)throws Exception{
        try{Class<?> am=Class.forName("android.app.IActivityManager");if(manager==null)manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            if(provider==null){token=new Binder();Object holder=am.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(manager,authority,0,token,"Folio shared pen");if(holder!=null)provider=holder.getClass().getField("provider").get(holder);}
            if(provider==null)return null;
            Object result;if(Build.VERSION.SDK_INT>=31){android.content.AttributionSource caller=new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build();result=Class.forName("android.content.IContentProvider").getMethod("call",android.content.AttributionSource.class,String.class,String.class,String.class,Bundle.class).invoke(provider,caller,authority,method,arg,extras);}
            else if(Build.VERSION.SDK_INT>=29)result=Class.forName("android.content.IContentProvider").getMethod("call",String.class,String.class,String.class,String.class,Bundle.class).invoke(provider,"com.android.shell",authority,method,arg,extras);
            else result=Class.forName("android.content.IContentProvider").getMethod("call",String.class,String.class,String.class,Bundle.class).invoke(provider,"com.android.shell",method,arg,extras);
            Bundle bundle=(Bundle)result;if(bundle!=null)bundle.size();return bundle;
        }catch(Exception error){provider=null;reported="";try{if(manager!=null&&token!=null)Class.forName("android.app.IActivityManager").getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(manager,authority,token,0);}catch(Exception ignored){}throw error;}
    }
    synchronized void device(String path)throws Exception{if(!path.isEmpty()&&!reported.equals(path)){call("device",path,null);reported=path;}}
}
