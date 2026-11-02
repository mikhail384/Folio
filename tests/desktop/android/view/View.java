package android.view;

import java.util.ArrayList;

/** Minimal test-only lifecycle surface for the real SpringDriver; never compiled into Android. */
public final class View {
    public static final int VISIBLE=0;
    public boolean attached=true,shown=true;public int windowVisibility;
    public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View view);void onViewDetachedFromWindow(View view);}
    final ArrayList<OnAttachStateChangeListener> listeners=new ArrayList<>();
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener listener){listeners.add(listener);}
    public boolean isAttachedToWindow(){return attached;}
    public boolean isShown(){return shown;}
    public int getWindowVisibility(){return windowVisibility;}
    public void detach(){attached=false;for(OnAttachStateChangeListener listener:listeners)listener.onViewDetachedFromWindow(this);}
}
