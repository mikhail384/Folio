package ru.focusflow.pen;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Binder IPC protected by signature-only DUMP and an explicit shell UID check. */
public final class PenProvider extends ContentProvider {
    private PenRuntime runtime;
    @Override public boolean onCreate(){runtime=PenRuntime.get(getContext());return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        int uid=Binder.getCallingUid();
        if(uid!=2000&&uid!=0)throw new SecurityException("Only the ADB shell may send pen events");
        Bundle result=new Bundle();
        if("inputMode".equals(method)){
            if(!"sdk".equals(arg)&&!"global".equals(arg))throw new IllegalArgumentException("Expected sdk or global");
            final boolean onlySdk="sdk".equals(arg);
            runtime.handler.post(()->{try{org.json.JSONObject next=new org.json.JSONObject(runtime.config.toString());next.put("nativePenOnly",onlySdk);runtime.update(next);runtime.resetDetector();runtime.addLog(onlySdk?"Ввод: только API/SDK":"Ввод: SDK с глобальным помощником");}catch(org.json.JSONException error){runtime.addLog("Не удалось изменить режим ввода");}});
            result.putBoolean("requestedNativePenOnly",onlySdk);
        }else if("control".equals(method)){
            final String mode=arg==null?"observe":arg;
            final boolean folio=extras!=null&&extras.getInt("sharedProtocol",0)==2&&extras.getBoolean("folioPriority",false);
            final int protocol=extras==null?0:extras.getInt("sharedProtocol",0);
            runtime.handler.post(()->{
                runtime.bridgeProtocol=protocol;
                runtime.setFolioPriority(folio||FolioPriority.active(runtime.context));
                boolean tileChanged=!runtime.bridgeConnected||runtime.grabbed!=mode.equals("grabbed");
                runtime.lastHeartbeat=SystemClock.elapsedRealtime();
                if(!runtime.bridgeConnected){runtime.bridgeConnected=true;runtime.resetDetector();runtime.addLog("ADB-помощник подключён");}
                boolean grabbed=mode.equals("grabbed");
                String error=mode.startsWith("error:")?mode.substring(6):"";
                if(runtime.grabbed!=grabbed||!runtime.bridgeError.equals(error)){
                    runtime.addLog(grabbed?"Перехват включён: штатные жесты отключены":error.isEmpty()?"Наблюдение за жестами":error);
                }
                runtime.grabbed=grabbed;runtime.bridgeError=error;if(tileChanged)FocusFlowTileService.refresh(getContext());
            });
            boolean ready=runtime.service!=null&&!runtime.stopRequested&&!folio&&!runtime.config.optBoolean("nativePenOnly",true);
            result.putBoolean("ready",ready);result.putInt("sharedProtocol",2);
            result.putBoolean("grab",ready&&runtime.config.optBoolean("enabled")&&runtime.config.optBoolean("intercept"));
            result.putBoolean("stop",runtime.stopRequested);
        }else if("diagnostics".equals(method)){
            result.putBoolean("nativePenOnly",runtime.config.optBoolean("nativePenOnly",true));
            result.putInt("swipes",runtime.swipes);result.putInt("squeezes",runtime.squeezes);result.putInt("gestures",runtime.gestureCount);result.putString("lastSignal",runtime.lastSignal);
            result.putBoolean("connected",runtime.bridgeConnected&&SystemClock.elapsedRealtime()-runtime.lastHeartbeat<2000);
            result.putBoolean("folioPriority",runtime.priorityFence.blocked());result.putBoolean("stopped",runtime.stopRequested);
            result.putBoolean("service",runtime.service!=null);result.putBoolean("grabbed",runtime.grabbed);
            result.putInt("sharedProtocol",runtime.bridgeProtocol);result.putInt("providerProtocol",2);result.putString("device",runtime.device);
        }else if("events".equals(method)){
            if(arg==null||arg.length()>4096)throw new IllegalArgumentException("Event batch size");
            final String batch=arg;
            runtime.handler.post(()->{
                runtime.lastHeartbeat=SystemClock.elapsedRealtime();
                for(String event:batch.split(";"))try{
                    String[] values=event.split(",");
                    if(values.length!=3)continue;
                    long time=Long.parseLong(values[0]);int code=Integer.parseInt(values[1]),value=Integer.parseInt(values[2]);
                    if((code==189||code==73||code==81)&&(value==0||value==1))
                        runtime.input(code==73?PenRuntime.SWIPE_UP_CODE:code==81?PenRuntime.SWIPE_DOWN_CODE:189,value==1,time,"ADB");
                }catch(NumberFormatException ignored){}
            });
        }else if("device".equals(method)){
            if(arg!=null&&arg.matches("/dev/input/event[0-9]+"))runtime.handler.post(()->{
                runtime.device="Xiaomi Focus Pen Pro · "+arg;runtime.addLog("Перо: "+arg);
            });
        }else throw new IllegalArgumentException("Unknown method");
        return result;
    }
    @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
}
