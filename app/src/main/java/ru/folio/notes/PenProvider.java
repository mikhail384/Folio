package ru.folio.notes;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.lang.ref.WeakReference;

/** No exported mutation API: only this app and the ADB shell can talk to the helper. */
public final class PenProvider extends ContentProvider {
    static volatile WeakReference<MainActivity> activity=new WeakReference<>(null);
    static volatile long heartbeat;static volatile String owner="none";static volatile boolean shared;
    static volatile String state="Мост не запущен",device="",lastEvent="—";
    static volatile float rotationDegrees=Float.NaN;
    static volatile long rotationAt,rotationEvents;
    static volatile long nativeRotationAt;
    static volatile String rotationSource="—";
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        int uid=Binder.getCallingUid();if("priority".equals(method)){if(uid!=2000&&uid!=android.os.Process.myUid()){String[] packages=getContext().getPackageManager().getPackagesForUid(uid);if(packages==null||!java.util.Arrays.asList(packages).contains("ru.focusflow.pen"))throw new SecurityException("Priority reader denied");}MainActivity current=activity.get();Bundle status=new Bundle();status.putBoolean("active",current!=null&&current.penPriority());status.putInt("protocol",2);status.putBoolean("connected",connected());status.putBoolean("shared",shared);return status;}
        if(uid!=2000&&uid!=android.os.Process.myUid())throw new SecurityException("ADB shell only");
        MainActivity a=activity.get();Bundle reply=new Bundle();boolean active=a!=null&&a.penActive();
        if(method.equals("control")){heartbeat=SystemClock.uptimeMillis();state=arg==null?"observe":arg;shared=extras!=null&&extras.getInt("sharedProtocol",0)==2;owner=shared?extras.getString("owner","none"):"folio";reply.putBoolean("priority",a!=null&&a.penPriority());reply.putBoolean("active",active&&a!=null&&!a.nativePenOnly());reply.putBoolean("grab",active&&a.suppress());reply.putBoolean("needDevice",device.isEmpty());reply.putInt("tap",a==null?96:a.tapCode());reply.putInt("squeeze",a==null?189:a.squeezeCode());}
        else if(method.equals("device")){device=arg;}
        else if(method.equals("diagnostics")){reply.putString("sdk",a==null||a.penSdk==null?"not initialized":a.penSdk.summary());reply.putBoolean("nativePenOnly",a!=null&&a.nativePenOnly());reply.putString("owner",owner);reply.putBoolean("shared",shared);reply.putBoolean("priority",a!=null&&a.penPriority());reply.putString("lastEvent",lastEvent);reply.putString("state",state);reply.putBoolean("connected",connected());reply.putBoolean("active",active);reply.putString("device",device);reply.putFloat("rotation",rotationDegrees);reply.putLong("rotationAt",rotationAt);reply.putLong("rotationEvents",rotationEvents);reply.putString("rotationSource",rotationSource);reply.putString("rotationService",a==null||a.rotation==null?"—":a.rotation.state);reply.putLong("nativeRotationEvents",a==null||a.rotation==null?0:a.rotation.events);}
        else if(method.equals("events")&&arg!=null){if(a!=null&&a.nativePenOnly())return reply;for(String record:arg.split(";")){String[] v=record.split(",");if(v.length!=3)continue;try{long t=Long.parseLong(v[0]);int code=Integer.parseInt(v[1]),value=Integer.parseInt(v[2]);if(code==-10){if(value>=-360&&value<=360&&SystemClock.uptimeMillis()-nativeRotationAt>5000){rotationSource="ADB / ABS_BRAKE";rotationDegrees=(value%360+360)%360;rotationAt=SystemClock.uptimeMillis();rotationEvents++;if(active)a.runOnUiThread(()->{if(a.penActive()&&a.canvas!=null)a.canvas.rotation(value,t);});}}else {lastEvent="Linux "+code+" · "+(value==1?"DOWN":"UP");android.util.Log.i("FolioPen",lastEvent);if(active)a.runOnUiThread(()->{if(a.penActive())a.hardware(t,code,value);});}}catch(NumberFormatException ignored){}}}
        return reply;
    }
    static boolean connected(){return SystemClock.uptimeMillis()-heartbeat<2000&&heartbeat!=0;}
    @Override public String getType(Uri u){return null;}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}

