package ru.folio.notes;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Device-only UI test. Uses an isolated sample store and does not modify notebook ink. */
public final class HandwritingSetupDeviceTests extends Instrumentation {
    MainActivity a;HandwritingTraining original;File testFile;String expectedLabel;boolean wasEnabled,hadSeen,seen,hadPending,pending;int assertions;
    void check(boolean ok,String what){if(!ok)throw new AssertionError(what);assertions++;}
    void main(Runnable r){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new RuntimeException(error.get());}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    MotionEvent event(int action,long down,long time,float x,float y,int tool,float pressure){
        MotionEvent.PointerProperties prop=new MotionEvent.PointerProperties();prop.id=0;prop.toolType=tool;MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();point.x=x;point.y=y;point.pressure=pressure;point.size=.04f;
        return MotionEvent.obtain(down,time,action,1,new MotionEvent.PointerProperties[]{prop},new MotionEvent.PointerCoords[]{point},0,0,1,1,0,0,tool==MotionEvent.TOOL_TYPE_FINGER?InputDevice.SOURCE_TOUCHSCREEN:InputDevice.SOURCE_STYLUS,0);
    }
    @Override public void onStart(){Bundle result=new Bundle();int outcome=Activity.RESULT_CANCELED;try{
        Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);a=(MainActivity)startActivitySync(intent);
        for(int i=0;i<100&&!a.ready;i++)SystemClock.sleep(100);check(a.ready,"app loaded");
        main(()->{if(a.launch!=null)a.launch.closeNow(false);if(a.intro!=null)a.intro.closeNow();if(a.handwritingSetup!=null)a.handwritingSetup.closeNow();wasEnabled=a.prefs.getBoolean("agent_enabled",false);hadSeen=a.prefs.contains(HandwritingSetup.SEEN);seen=a.prefs.getBoolean(HandwritingSetup.SEEN,false);hadPending=a.prefs.contains(HandwritingSetup.PENDING);pending=a.prefs.getBoolean(HandwritingSetup.PENDING,false);a.prefs.edit().putBoolean("agent_enabled",false).commit();a.agent.pause();original=a.handwritingTraining;testFile=new File(a.getCacheDir(),"folio-ui-test-"+System.nanoTime()+".json");a.handwritingTraining=new HandwritingTraining(testFile);HandwritingSetup.show(a,false);check(a.handwritingSetup!=null&&a.setupOpen,"connection page opens");check(a.handwritingSetup.page==0,"first step is connection");a.handwritingSetup.page=1;a.handwritingSetup.build();expectedLabel=a.handwritingSetup.label();});
        a.store.io.submit(()->{}).get();waitForIdleSync();
        main(()->{
            HandwritingSetup.PenPad pad=a.handwritingSetup.pad;check(pad!=null&&pad.isEnabled()&&pad.getWidth()>0&&pad.getHeight()>0,"writing pad is visible and ready");pad.geometry();float x=pad.left+pad.scale*150,y=pad.top+pad.scale*100;long now=SystemClock.uptimeMillis();
            MotionEvent finger=event(MotionEvent.ACTION_DOWN,now,now,x,y,MotionEvent.TOOL_TYPE_FINGER,.7f);check(!pad.onTouchEvent(finger),"finger does not draw");finger.recycle();
            MotionEvent down=event(MotionEvent.ACTION_DOWN,now,now,x,y,MotionEvent.TOOL_TYPE_STYLUS,.3f),move=event(MotionEvent.ACTION_MOVE,now,now+20,x+pad.scale*70,y+pad.scale*80,MotionEvent.TOOL_TYPE_STYLUS,.8f),up=event(MotionEvent.ACTION_UP,now,now+40,x+pad.scale*100,y+pad.scale*120,MotionEvent.TOOL_TYPE_STYLUS,.6f);
            check(pad.onTouchEvent(down)&&pad.onTouchEvent(move)&&pad.onTouchEvent(up),"stylus stroke accepted");down.recycle();move.recycle();up.recycle();
            check(pad.strokes.size()==1&&pad.strokes.get(0).points.size()>=3,"stroke is retained");check(Math.abs(pad.strokes.get(0).points.get(1).p-.8f)<.001f,"stylus pressure is retained");try{JSONArray timed=pad.json().getJSONObject(0).getJSONArray("pointTimesMs");check(timed.length()==pad.strokes.get(0).points.size()&&timed.getLong(0)<=timed.getLong(1)&&timed.getLong(1)<=timed.getLong(2),"historical stylus event times are captured in order");}catch(JSONException e){throw new AssertionError("pen event times are serialized",e);}
            a.handwritingSetup.persistDraft();a.onConfigurationChanged(new Configuration(a.getResources().getConfiguration()));check(a.handwritingSetup!=null&&a.handwritingSetup.page==1,"setup survives configuration change");check(a.handwritingSetup.pad.strokes.size()==1,"visible draft survives configuration change");
        });
        a.store.io.submit(()->{}).get();JSONArray savedDraft=new HandwritingTraining(testFile).draft(expectedLabel);check(savedDraft.length()==1,"draft survives reload");check(savedDraft.getJSONObject(0).getJSONArray("pointTimesMs").length()==savedDraft.getJSONObject(0).getJSONArray("points").length()/5,"pen timing survives draft reload");
        result.putString("stream","PASS "+assertions+" connection/setup, stylus pressure, palm rejection and draft lifecycle checks\n");outcome=Activity.RESULT_OK;
    }catch(Throwable failure){StringWriter s=new StringWriter();failure.printStackTrace(new PrintWriter(s));result.putString("stream","FAIL "+s);}
    finally{if(a!=null&&original!=null)try{main(()->{if(a.handwritingSetup!=null){a.handwritingSetup.persistDraft();a.handwritingSetup.closeNow();}});a.store.io.submit(()->{}).get();main(()->{a.handwritingTraining=original;android.content.SharedPreferences.Editor edit=a.prefs.edit().putBoolean("agent_enabled",wasEnabled);if(hadSeen)edit.putBoolean(HandwritingSetup.SEEN,seen);else edit.remove(HandwritingSetup.SEEN);if(hadPending)edit.putBoolean(HandwritingSetup.PENDING,pending);else edit.remove(HandwritingSetup.PENDING);edit.commit();if(wasEnabled&&a.resumed)a.agent.resume();});if(testFile!=null)testFile.delete();}catch(Exception ignored){}}
    finish(outcome,result);}
}
