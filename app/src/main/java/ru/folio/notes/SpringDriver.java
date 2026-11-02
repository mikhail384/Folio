package ru.folio.notes;

import android.view.*;

/** One native frame callback for related values; no restarting tween when the user changes direction. */
final class SpringDriver implements Choreographer.FrameCallback {
    final View owner;final SmoothSpring[] springs;final Runnable update;final Choreographer choreographer=Choreographer.getInstance();boolean running;long previous;int generation;
    Runnable settled;
    SpringDriver(View owner,Runnable update,SmoothSpring... springs){this.owner=owner;this.update=update;this.springs=springs;owner.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){stop();}});}
    void to(boolean animated,float... targets){boolean needed=false;for(int i=0;i<springs.length;i++){springs[i].target(targets[i]);needed|=Math.abs(springs[i].value-springs[i].target)>.0005f||Math.abs(springs[i].velocity)>.008f;}if(!animated||!owner.isAttachedToWindow()||!owner.isShown()){stop();for(SmoothSpring spring:springs)spring.snap(spring.target);update.run();if(settled!=null)settled.run();return;}if(!running){if(needed){running=true;previous=System.nanoTime();choreographer.postFrameCallback(this);}else {update.run();if(settled!=null)settled.run();}}}
    public void doFrame(long now){if(!running)return;int epoch=generation;if(owner.getWindowVisibility()!=View.VISIBLE){finish();if(settled!=null)settled.run();return;}float dt=Math.max(0,(now-previous)/1000000000f);previous=now;boolean moving=false;for(SmoothSpring spring:springs)moving|=spring.step(dt);update.run();if(!running||epoch!=generation)return;if(moving){choreographer.postFrameCallback(this);}else {running=false;previous=0;if(settled!=null)settled.run();}}
    void stop(){generation++;running=false;previous=0;choreographer.removeFrameCallback(this);}
    void finish(){stop();for(SmoothSpring spring:springs)spring.snap(spring.target);update.run();}
}
