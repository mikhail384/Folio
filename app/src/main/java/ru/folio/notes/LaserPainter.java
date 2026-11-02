package ru.folio.notes;

import android.graphics.*;

/** Native light renderer: batched fading traces, radial bloom and a white hot centre. */
final class LaserPainter implements LaserEffect.Sink {
    static final float[] SCALES={6,2.7f,1,.32f},OPACITY={.055f,.22f,.92f,.87f};
    final Paint paint=new Paint(3);final Path path=new Path();final Matrix shift=new Matrix();Canvas canvas;
    float width,alpha,lastX,lastY;int color;boolean segments;RadialGradient halo;int haloColor;
    void draw(Canvas canvas,LaserEffect effect,long now,float unit){this.canvas=canvas;effect.render(now,unit,this);this.canvas=null;}
    void color(int color,float opacity){paint.setShader(null);paint.setColor(color);paint.setAlpha(Math.round(255*Math.max(0,Math.min(1,opacity))));}
    public void beginTrail(float width,int color,float alpha){this.width=width;this.color=color;this.alpha=alpha;segments=false;path.rewind();}
    public void segment(float ax,float ay,float bx,float by){if(!segments||Math.abs(ax-lastX)+Math.abs(ay-lastY)>.002f)path.moveTo(ax,ay);path.lineTo(bx,by);lastX=bx;lastY=by;segments=true;}
    public void endTrail(){if(!segments)return;paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        for(int i=0;i<4;i++){color(i==3?0xffffefef:color,alpha*OPACITY[i]);paint.setStrokeCap(i<2?Paint.Cap.ROUND:Paint.Cap.BUTT);paint.setStrokeWidth(width*SCALES[i]);canvas.drawPath(path,paint);}paint.setStyle(Paint.Style.FILL);
    }
    public void spot(float x,float y,float radius,int color,float alpha){if(alpha<=0)return;paint.setStyle(Paint.Style.FILL);
        if(halo==null||haloColor!=color){haloColor=color;int rgb=color&0xffffff;halo=new RadialGradient(0,0,1,new int[]{0xd9000000|rgb,0x65000000|rgb,0x18000000|rgb,rgb},new float[]{0,.25f,.60f,1},Shader.TileMode.CLAMP);}
        shift.setScale(radius*5,radius*5);shift.postTranslate(x,y);halo.setLocalMatrix(shift);paint.setShader(halo);paint.setAlpha(Math.round(255*alpha));canvas.drawCircle(x,y,radius*5,paint);color(color,alpha);canvas.drawCircle(x,y,radius,paint);color(0xfffff7f7,alpha);canvas.drawCircle(x,y,radius*.46f,paint);paint.setShader(null);
    }
    public void ring(float x,float y,float radius,float width,int color,float alpha){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);color(color,alpha);canvas.drawCircle(x,y,radius,paint);paint.setStyle(Paint.Style.FILL);}
}
