package ru.folio.notes;

import android.view.Choreographer;
import java.util.function.LongSupplier;

/** UI-only frame clock. Card motion has its own preference, independent of the global animator scale. */
final class FrameClock implements Choreographer.FrameCallback {
    interface Update {void frame(float fraction);}
    final Choreographer choreographer=Choreographer.getInstance();
    final LongSupplier time;boolean running;long firstFrame=-1,duration;int generation;Update update;Runnable complete;
    FrameClock(){this(System::nanoTime);}FrameClock(LongSupplier time){this.time=time;}
    void start(int milliseconds,Update update,Runnable complete){cancel();int epoch=generation;duration=Math.max(1,milliseconds)*1000000L;this.update=update;this.complete=complete;firstFrame=time.getAsLong();running=true;update.frame(0);if(running&&epoch==generation)choreographer.postFrameCallback(this);}
    public void doFrame(long now){if(!running)return;int epoch=generation;float t=Math.max(0,Math.min(1,(now-firstFrame)/(float)duration));update.frame(t);if(!running||epoch!=generation)return;if(t>=1){running=false;Runnable done=complete;update=null;complete=null;if(done!=null)done.run();}else choreographer.postFrameCallback(this);}
    void cancel(){generation++;running=false;choreographer.removeFrameCallback(this);update=null;complete=null;firstFrame=-1;}
}
