package ru.folio.penbridge;

import android.content.Context;
import android.os.*;
import android.util.Log;
import android.view.*;
import com.miui.penengine.touchfilm.MiuiTouchFilmUtils;
import com.miui.penengine.posture.MiuiStylusPosture;

/** Official Xiaomi PenEngine 0.3.2. No shell privileges or private Binder calls. */
public final class XiaomiPenSdk {
    public interface Sink { void gesture(int key, boolean down, long time); void rotation(int degree, long time); }
    private final Context context;
    private final Sink sink;
    private final boolean useRotation;
    private boolean touch, posture, squeeze, closed;
    private int currentKey;
    private long currentTime, lastTry, lastMotionLog;
    public long keys, callbacks, rotations;
    public String status="SDK ожидает запуска";
    public XiaomiPenSdk(Context context, boolean rotation, Sink sink){this.context=context;this.useRotation=rotation;this.sink=sink;}
    public void resume(){closed=false;lastTry=0;update();}
    public void update(){
        if(closed || (!android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT).contains("xiaomi")&&!android.os.Build.BRAND.toLowerCase(java.util.Locale.ROOT).contains("redmi")))return;
        long now=SystemClock.uptimeMillis();if(now-lastTry<2000)return;lastTry=now;
        try {
            if(!touch)touch=MiuiTouchFilmUtils.init(context,new MiuiTouchFilmUtils.TouchFilmListener(){
                public void onTouchFilmTriggered(int function){
                    callbacks++;Log.i("FolioNativePen","SDK callback function="+function+" key="+currentKey);
                    if(function==0||closed)return;
                    if(currentKey==194){squeeze=true;sink.gesture(194,true,currentTime);}
                    else if(currentKey>=195&&currentKey<=197)sink.gesture(currentKey,true,currentTime);
                }
                public void onBrushPreviewChanged(boolean enabled){Log.i("FolioNativePen","SDK preview="+enabled);}
            });
            if(useRotation&&!posture)posture=MiuiStylusPosture.init(context);
            String next="PenEngine 0.3.2 · жесты="+touch+" · вращение="+posture;
            if(!next.equals(status)){status=next;Log.i("FolioNativePen",status);}
        } catch(RuntimeException|LinkageError e){status="SDK: "+e.getClass().getSimpleName();Log.w("FolioNativePen",status,e);}
    }
    public boolean key(KeyEvent e){
        int key=e.getKeyCode();InputDevice device=e.getDevice();
        boolean pen=device!=null&&device.getName().toLowerCase(java.util.Locale.ROOT).contains("focus pen");
        if(!pen||closed)return false;
        keys++;Log.i("FolioNativePen","Android key="+key+" scan="+e.getScanCode()+" action="+e.getAction()+" repeat="+e.getRepeatCount());
        if(!touch||key<194||key>197)return false;
        if(e.getRepeatCount()!=0||e.isCanceled()){if(squeeze&&key==194){squeeze=false;sink.gesture(194,false,e.getEventTime());}return true;}
        currentKey=key;currentTime=e.getEventTime();
        try{boolean handled=MiuiTouchFilmUtils.onDispatchKeyEvent(e);
            if(key==194&&e.getAction()==KeyEvent.ACTION_UP&&squeeze){squeeze=false;sink.gesture(194,false,e.getEventTime());}
            return handled;
        }catch(RuntimeException|LinkageError ex){Log.w("FolioNativePen","SDK key failed",ex);return false;}
        finally{currentKey=0;}
    }
    public boolean motion(MotionEvent e){
        if(!posture||closed)return false;
        try{boolean handled=MiuiStylusPosture.genericMotionEvent(e);if(handled){int degree=MiuiStylusPosture.getDegree();rotations++;sink.rotation(degree,e.getEventTime());long now=SystemClock.uptimeMillis();if(now-lastMotionLog>500){lastMotionLog=now;Log.i("FolioNativePen","SDK rotation="+degree+" samples="+rotations);}}return handled;
        }catch(RuntimeException|LinkageError ex){Log.w("FolioNativePen","SDK motion failed",ex);return false;}
    }
    public void pause(){closed=true;squeeze=false;try{if(touch)MiuiTouchFilmUtils.onDestroy();if(posture)MiuiStylusPosture.destroy();}catch(RuntimeException|LinkageError e){Log.w("FolioNativePen","SDK release",e);}touch=posture=false;}
    public String summary(){return status+" · keys="+keys+" · callbacks="+callbacks+" · rotation="+rotations;}
}
