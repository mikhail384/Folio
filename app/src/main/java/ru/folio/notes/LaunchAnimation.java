package ru.folio.notes;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;

/** A finite cold-start sequence: ink first, then the pencil emblem and the workspace. */
final class LaunchAnimation extends View {
    static final long DURATION=1450;
    final MainActivity a;final Paint p=new Paint(3);final Path curve=new Path(),segment=new Path();
    final PathMeasure measure=new PathMeasure();final float[] point=new float[2],tangent=new float[2];
    final Bitmap emblem;final long start=SystemClock.uptimeMillis();boolean data,ending,closed;
    LaunchAnimation(MainActivity a){super(a);this.a=a;setBackgroundColor(0xff08090b);setClickable(true);setFocusable(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);emblem=BitmapFactory.decodeResource(getResources(),R.drawable.freenotes_icon);}
    void dataReady(){data=true;if(!Studio.motion()||SystemClock.uptimeMillis()-start>=DURATION)finishSoon();else invalidate();}
    void finishSoon(){if(ending||closed||!data)return;ending=true;post(()->{if(closed)return;if(!Studio.motion()){closeNow(true);return;}animate().alpha(0).setDuration(170).withEndAction(()->closeNow(true)).start();});}
    void closeNow(boolean tour){if(closed)return;closed=true;animate().cancel();if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);if(a.launch==this)a.launch=null;if(tour&&!a.isDestroyed()&&!a.isFinishing())Onboarding.show(a,false);}
    protected void onDraw(Canvas c){super.onDraw(c);if(closed)return;float elapsed=Studio.motion()?SystemClock.uptimeMillis()-start:DURATION;render(c,elapsed);if(elapsed<DURATION&&getWindowVisibility()==VISIBLE)postInvalidateOnAnimation();else if(data)finishSoon();}
    void render(Canvas c,float elapsed){
        float draw=Ink.clamp((elapsed-100)/610,0,1),reveal=Ink.clamp((elapsed-700)/510,0,1),cx=getWidth()/2f,cy=getHeight()*.46f;
        float scale=Math.min(a.dp(300),getWidth()*.7f)/300f;
        c.save();c.translate(cx,cy);c.scale(scale,scale);
        if(reveal<1){c.save();float fold=1-.34f*reveal;c.scale(fold,fold);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(0xfff6f5f0);p.setAlpha((int)(255*(1-reveal)));
            curve.rewind();curve.moveTo(-117,22);curve.cubicTo(-72,-58,-25,79,18,13);curve.cubicTo(50,-36,75,-20,114,-37);measure.setPath(curve,false);segment.rewind();measure.getSegment(0,Math.max(.01f,measure.getLength()*draw),segment,true);c.drawPath(segment,p);
            if(draw>0){measure.getPosTan(measure.getLength()*draw,point,tangent);c.save();c.translate(point[0],point[1]);c.rotate(-32);c.saveLayerAlpha(-45,-10,45,165,(int)(255*(1-reveal)));ToolArt.draw(c,"pen",0,0,1.25f,0xffe7e5f7);c.restore();c.restore();}c.restore();
        }
        if(reveal>0){c.save();float s=.72f+.28f*PopoverLayout.spring(reveal);c.scale(s,s);p.setStyle(Paint.Style.FILL);p.setAlpha((int)(255*Ink.clamp(reveal*2.4f,0,1)));c.drawBitmap(emblem,null,new RectF(-52,-52,52,52),p);c.restore();
            p.setColor(0xfff6f5f0);p.setAlpha((int)(255*Ink.clamp((reveal-.25f)*1.7f,0,1)));p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(27);p.setTextAlign(Paint.Align.CENTER);c.drawText("folio",0,95+8*(1-reveal),p);p.setTextAlign(Paint.Align.LEFT);
        }
        p.setAlpha(255);c.restore();
    }
    protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
}
