package ru.focusflow.pen;

import android.accessibilityservice.*;
import android.content.*;
import android.graphics.Path;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

public final class PenService extends AccessibilityService {
    private PenRuntime runtime;
    private ru.folio.penbridge.XiaomiPenSdk penSdk;
    private ScreenRecorder recorder;
    public void recordScreen(String mode,String target,int edit,int duration){if(recorder==null)recorder=new ScreenRecorder(this);recorder.open(mode,target,edit,duration);}
    public void stopScreenRecording(){if(recorder!=null)recorder.close();}
    public interface Result {void done(boolean ok);}
    @Override protected void onServiceConnected() {
        super.onServiceConnected(); runtime=PenRuntime.get(this);runtime.service=this;
        penSdk=new ru.folio.penbridge.XiaomiPenSdk(this,false,new ru.folio.penbridge.XiaomiPenSdk.Sink(){
            public void rotation(int degree,long time){}
            public void gesture(int key,boolean down,long time){if(runtime==null||!runtime.actionsAllowed())return;if(key==194)runtime.input(189,down,time,"Xiaomi SDK");else if(down&&key>=196)runtime.input(key==196?PenRuntime.SWIPE_UP_CODE:PenRuntime.SWIPE_DOWN_CODE,true,time,"Xiaomi SDK");else if(down&&key==195)runtime.addLog("SDK: двойной тап корпуса получен");}
        });penSdk.resume();
        AccessibilityServiceInfo info=getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;setServiceInfo(info);
        runtime.addLog("Служба управления включена");FocusFlowTileService.refresh(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        // Package/window transitions are enough; screen contents and text are never read.
        if(runtime!=null&&e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)runtime.refreshFolioPriority();
    }
    @Override public void onInterrupt(){if(runtime!=null)runtime.cancel();}
    @Override public void onDestroy(){
        if(penSdk!=null)penSdk.pause();if(recorder!=null)recorder.close();
        if(runtime!=null){runtime.cancel();runtime.service=null;runtime.bridgeConnected=false;runtime.grabbed=false;FocusFlowTileService.refresh(this);}
        super.onDestroy();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(runtime==null||runtime.priorityFence.blocked())return false;
        if((runtime.config.optBoolean("nativePenOnly",true)||!runtime.bridgeConnected)&&penSdk!=null){penSdk.update();if(penSdk.key(event))return runtime.actionsAllowed()&&runtime.config.optBoolean("enabled")&&runtime.config.optBoolean("intercept");}
        InputDevice device=event.getDevice();
        if(device==null||!device.getName().toLowerCase(java.util.Locale.ROOT).contains("focus pen pro"))return false;
        if(!runtime.actionsAllowed())return false;
        if(event.getKeyCode()==196||event.getKeyCode()==197){
            if(event.getRepeatCount()==0&&event.getAction()==KeyEvent.ACTION_DOWN){
                runtime.addLog("Свайп Android: key="+event.getKeyCode()+", scan="+event.getScanCode()+", device="+(device==null?"virtual":device.getName()));
                runtime.input(event.getKeyCode()==196?PenRuntime.SWIPE_UP_CODE:PenRuntime.SWIPE_DOWN_CODE,true,event.getEventTime(),"Android");
            }
            return runtime.config.optBoolean("enabled")&&runtime.config.optBoolean("intercept");
        }
        if(event.getScanCode()!=189) return false;
        if(event.getRepeatCount()==0&&(event.getAction()==KeyEvent.ACTION_DOWN||event.getAction()==KeyEvent.ACTION_UP))
            runtime.event(event.getAction()==KeyEvent.ACTION_DOWN,event.getEventTime(),"Android");
        return !runtime.bridgeConnected&&runtime.config.optBoolean("enabled")&&runtime.config.optBoolean("intercept");
    }
    public void perform(JSONObject action,Result result) {
        if(runtime!=null&&!runtime.actionsAllowed()){result.done(false);return;}
        String type=action.optString("type"),value=action.optString("value");int global=-1;
        if(type.equals("widget")){startActivity(new Intent(this,WidgetActivity.class).putExtra("widgetId",Integer.parseInt(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));result.done(true);return;}
        if(type.equals("app_activity")){Intent intent=new Intent().setComponent(ComponentName.unflattenFromString(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);result.done(true);return;}
        if(type.equals("shortcut")){try{org.json.JSONArray list=new org.json.JSONArray(value);Intent[] intents=new Intent[list.length()];for(int i=0;i<list.length();i++)intents[i]=Intent.parseUri(list.getString(i),Intent.URI_INTENT_SCHEME);intents[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivities(intents);result.done(true);}catch(Exception e){result.done(false);}return;}
        if(type.equals("swipe_custom")||type.equals("screen_path")){
            try{JSONObject data=ScreenPath.validate(new JSONObject(value));org.json.JSONArray strokes=data.getJSONArray("strokes");DisplayMetrics m=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);GestureDescription.Builder b=new GestureDescription.Builder();
                for(int i=0;i<strokes.length();i++){JSONObject s=strokes.getJSONObject(i);org.json.JSONArray points=s.getJSONArray("points");Path p=new Path();for(int j=0;j<points.length();j++){org.json.JSONArray xy=points.getJSONArray(j);float x=(float)((m.widthPixels-1)*xy.getDouble(0)/100),y=(float)((m.heightPixels-1)*xy.getDouble(1)/100);if(j==0)p.moveTo(x,y);else p.lineTo(x,y);}b.addStroke(new GestureDescription.StrokeDescription(p,s.getInt("startMs"),s.getInt("durationMs")));}
                boolean accepted=dispatchGesture(b.build(),new GestureResultCallback(){@Override public void onCompleted(GestureDescription d){result.done(true);}@Override public void onCancelled(GestureDescription d){result.done(false);}},runtime.handler);if(!accepted)result.done(false);
            }catch(Exception e){result.done(false);}return;
        }
        switch(type){
            case "home":global=GLOBAL_ACTION_HOME;break;
            case "back":global=GLOBAL_ACTION_BACK;break;
            case "recents":global=GLOBAL_ACTION_RECENTS;break;
            case "notifications":global=GLOBAL_ACTION_NOTIFICATIONS;break;
            case "quick_settings":global=GLOBAL_ACTION_QUICK_SETTINGS;break;
            case "screenshot":if(Build.VERSION.SDK_INT>=28)global=GLOBAL_ACTION_TAKE_SCREENSHOT;break;
            case "lock":if(Build.VERSION.SDK_INT>=28)global=GLOBAL_ACTION_LOCK_SCREEN;break;
            case "power":global=GLOBAL_ACTION_POWER_DIALOG;break;
        }
        if(global!=-1){result.done(performGlobalAction(global));return;}
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(type.equals("volume_up")||type.equals("volume_down")){
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,type.equals("volume_up")?1:-1,AudioManager.FLAG_SHOW_UI);result.done(true);return;
        }
        int media=type.equals("play_pause")?KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:type.equals("next_track")?KeyEvent.KEYCODE_MEDIA_NEXT:type.equals("previous_track")?KeyEvent.KEYCODE_MEDIA_PREVIOUS:0;
        if(media!=0){audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,media));audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,media));result.done(true);return;}
        if(type.equals("launch_app")||type.equals("open_url")){
            Intent intent=type.equals("launch_app")?getPackageManager().getLaunchIntentForPackage(value):new Intent(Intent.ACTION_VIEW,Uri.parse(value));
            if(intent==null){result.done(false);return;}
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);result.done(true);return;
        }
        if(type.equals("tap")||type.startsWith("swipe_")){
            DisplayMetrics m=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
            Path path=new Path();long duration;
            if(type.equals("tap")){
                String[] xy=value.split(",");path.moveTo((m.widthPixels-1)*Float.parseFloat(xy[0])/100f,(m.heightPixels-1)*Float.parseFloat(xy[1])/100f);duration=60;
            }else{
                boolean up=type.equals("swipe_up");path.moveTo(m.widthPixels*.5f,m.heightPixels*(up?.75f:.25f));path.lineTo(m.widthPixels*.5f,m.heightPixels*(up?.25f:.75f));duration=350;
            }
            GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
            boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription d){result.done(true);}
                @Override public void onCancelled(GestureDescription d){result.done(false);}
            },runtime.handler);
            if(!accepted)result.done(false);return;
        }
        result.done(false);
    }
}
