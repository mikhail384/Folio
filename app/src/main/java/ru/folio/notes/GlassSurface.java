package ru.folio.notes;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import java.util.concurrent.*;

/** One small blurred snapshot per layout, never a live blur of the ink renderer. */
final class GlassSurface extends Drawable {
    static final ThreadPoolExecutor BLUR=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2),r->{Thread t=new Thread(r,"folio-card-glass");t.setDaemon(true);return t;});
    final Paint paint=new Paint(3);final Path clip=new Path();final RectF rect=new RectF(),border=new RectF();Shader sheen;Bitmap backdrop;float radius;int alpha=255;volatile int generation;
    GlassSurface(float radius){this.radius=radius;}
    void radius(float value){if(radius==value)return;radius=value;invalidateSelf();}
    protected void onBoundsChange(Rect bounds){rect.set(bounds);border.set(rect);border.inset(.5f,.5f);sheen=new LinearGradient(0,rect.top,0,Math.max(rect.top+1,rect.bottom),0x20ffffff,0x02ffffff,Shader.TileMode.CLAMP);}
    void release(){generation++;backdrop=null;}
    void capture(MainActivity a,int screenX,int screenY,int width,int height){
        final int epoch=++generation;if(width<1||height<1||a.root.getWidth()<1)return;
        Bitmap image=null;
        try{int w=Math.max(1,Math.min(512,width/7)),h=Math.max(1,Math.min(512,height/7));image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);final Bitmap snapshot=image;int[] origin=new int[2];a.getWindow().getDecorView().getLocationOnScreen(origin);Rect source=new Rect(screenX-origin[0],screenY-origin[1],screenX-origin[0]+width,screenY-origin[1]+height);
            PixelCopy.request(a.getWindow(),source,snapshot,status->{
                if(status!=PixelCopy.SUCCESS||epoch!=generation||a.isDestroyed()){snapshot.recycle();return;}
                try{BLUR.execute(()->{if(epoch!=generation){snapshot.recycle();return;}try{int[] pixels=new int[w*h],temporary=new int[w*h];snapshot.getPixels(pixels,0,w,0,0,w,h);for(int i=0;i<2;i++){blur(pixels,temporary,w,h,3,true);blur(temporary,pixels,w,h,3,false);}snapshot.setPixels(pixels,0,w,0,0,w,h);a.ui.post(()->{if(epoch==generation&&!a.isDestroyed()){backdrop=snapshot;invalidateSelf();}else snapshot.recycle();});}catch(RuntimeException|OutOfMemoryError ignored){snapshot.recycle();}});}catch(RejectedExecutionException ignored){snapshot.recycle();}
            },a.ui);
        }catch(RuntimeException|OutOfMemoryError ignored){if(image!=null)image.recycle();}
    }
    static void blur(int[] from,int[] to,int width,int height,int radius,boolean horizontal){int length=horizontal?width:height,rows=horizontal?height:width;for(int row=0;row<rows;row++){int red=0,green=0,blue=0;for(int k=-radius;k<=radius;k++){int j=Math.max(0,Math.min(length-1,k)),color=from[horizontal?row*width+j:j*width+row];red+=Color.red(color);green+=Color.green(color);blue+=Color.blue(color);}int count=radius*2+1;for(int x=0;x<length;x++){to[horizontal?row*width+x:x*width+row]=Color.rgb(red/count,green/count,blue/count);int before=Math.max(0,x-radius),after=Math.min(length-1,x+radius+1),a=from[horizontal?row*width+before:before*width+row],b=from[horizontal?row*width+after:after*width+row];red+=Color.red(b)-Color.red(a);green+=Color.green(b)-Color.green(a);blue+=Color.blue(b)-Color.blue(a);}}}
    public void draw(Canvas c){int save=c.save();clip.rewind();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);c.clipPath(clip);paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setAlpha(alpha);if(backdrop!=null){c.drawBitmap(backdrop,null,rect,paint);paint.setColor(0xcb292b30);}else paint.setColor(Studio.SURFACE);paint.setAlpha((Color.alpha(paint.getColor())*alpha)/255);c.drawRect(rect,paint);
        paint.setShader(sheen);paint.setAlpha(alpha);c.drawRect(rect,paint);paint.setShader(null);c.restoreToCount(save);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x35ffffff);paint.setAlpha(53*alpha/255);c.drawRoundRect(border,radius,radius,paint);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    }
    public void getOutline(Outline outline){outline.setRoundRect(getBounds(),radius);outline.setAlpha(1);}
    public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
    public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
