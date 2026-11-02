package ru.folio.notes;

import android.os.Handler;
import android.view.Choreographer;

/** Ink state changes immediately; chrome is drawn once per frame, preferences after a burst. */
final class ToolUpdates {
    final Handler ui;final Runnable write,refresh;boolean dirty,framePending;
    final Runnable persist;
    final Choreographer.FrameCallback frame;
    ToolUpdates(Handler ui,Runnable write,Runnable refresh){this.ui=ui;this.write=write;this.refresh=refresh;persist=()->{if(dirty){dirty=false;write.run();}};frame=now->{framePending=false;refresh.run();};}
    void save(){dirty=true;ui.removeCallbacks(persist);ui.postDelayed(persist,120);}
    void changed(){save();if(!framePending){framePending=true;Choreographer.getInstance().postFrameCallback(frame);}}
    void flush(){ui.removeCallbacks(persist);if(framePending){Choreographer.getInstance().removeFrameCallback(frame);framePending=false;refresh.run();}persist.run();}
}
