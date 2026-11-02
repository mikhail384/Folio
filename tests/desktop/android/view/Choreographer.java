package android.view;

import java.util.ArrayList;

/** Test-only scheduler. This source never enters either Android APK. */
public final class Choreographer {
    public interface FrameCallback {void doFrame(long timestamp);}
    private static final Choreographer INSTANCE=new Choreographer();
    private final ArrayList<FrameCallback> pending=new ArrayList<>();
    public static Choreographer getInstance(){return INSTANCE;}
    public void postFrameCallback(FrameCallback callback){pending.add(callback);}
    public void removeFrameCallback(FrameCallback callback){while(pending.remove(callback)){};}
    public void advance(long timestamp){ArrayList<FrameCallback> callbacks=new ArrayList<>(pending);pending.clear();for(FrameCallback callback:callbacks)callback.doFrame(timestamp);}
    public int pending(){return pending.size();}
}
