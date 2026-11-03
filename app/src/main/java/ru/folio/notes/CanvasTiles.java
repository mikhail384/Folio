package ru.folio.notes;

import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.*;
import java.util.concurrent.*;

/** Multiple recently used zoom levels, with background rendering while a cached level is visible. */
final class CanvasTiles {
    static final int SIZE=512,GUTTER=3,CAPACITY=192;
    static final class Key {
        final int level,x,y;
        Key(int level,int x,int y){this.level=level;this.x=x;this.y=y;}
        @Override public boolean equals(Object o){if(!(o instanceof Key))return false;Key k=(Key)o;return k.level==level&&k.x==x&&k.y==y;}
        @Override public int hashCode(){return (level*31+x)*31+y;}
    }
    final LinkedHashMap<Key,Bitmap> tiles=new LinkedHashMap<>(256,.75f,true);
    final HashMap<Key,Long> pending=new HashMap<>();
    final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    final Handler main=new Handler(Looper.getMainLooper());
    ThreadPoolExecutor workers;
    long revision;boolean asyncDisabled;
    int level=Integer.MIN_VALUE;float unit;

    void cancelPending(){revision++;pending.clear();if(workers!=null)workers.getQueue().clear();}
    // The hardware canvas may still reference a tile from the preceding frame.
    // Drop cache ownership and let Android release it after that frame completes.
    void clear(){cancelPending();asyncDisabled=false;tiles.clear();}
    void release(){clear();if(workers!=null){workers.shutdownNow();workers=null;}}
    float side(Key k){return SIZE/CanvasViewport.levelScale(k.level);}
    boolean intersects(Key k,float[] b){float u=CanvasViewport.levelScale(k.level),s=SIZE/u,g=GUTTER/u,l=k.x*s,t=k.y*s;return CanvasViewport.overlaps(b,l-g,t-g,l+s+g,t+s+g);}
    void invalidate(float[] b){cancelPending();Iterator<Map.Entry<Key,Bitmap>> i=tiles.entrySet().iterator();while(i.hasNext()){Map.Entry<Key,Bitmap> e=i.next();if(intersects(e.getKey(),b))i.remove();}}
    void append(Ink.Stroke stroke){cancelPending();float[] b=stroke.bounds();for(Map.Entry<Key,Bitmap> e:tiles.entrySet()){Key k=e.getKey();if(!intersects(k,b))continue;float u=CanvasViewport.levelScale(k.level),s=SIZE/u;Canvas c=new Canvas(e.getValue());c.translate(GUTTER-k.x*s*u,GUTTER-k.y*s*u);c.scale(u,u);InkRenderer.stroke(c,stroke);}}
    void erase(Ink.Page page,Bitmap background,Ink.Cut cut,SpatialIndex index){EraserDamage damage=new EraserDamage();damage.add(cut);erase(page,background,damage,index);}
    void erase(Ink.Page page,Bitmap background,EraserDamage damage,SpatialIndex index){
        if(!damage.dirty)return;cancelPending();
        float minimum=unit>0?unit:1;for(Key k:tiles.keySet())minimum=Math.min(minimum,CanvasViewport.levelScale(k.level));
        float[] bounds=damage.padded(2/Math.max(1e-12f,minimum));ArrayList<Ink.Stroke> nearby=index.query(bounds[0],bounds[1],bounds[2],bounds[3]);
        for(Map.Entry<Key,Bitmap> e:tiles.entrySet()){Key k=e.getKey();if(!intersects(k,bounds))continue;float u=CanvasViewport.levelScale(k.level),s=SIZE/u;Canvas c=new Canvas(e.getValue());c.translate(GUTTER-k.x*s*u,GUTTER-k.y*s*u);c.scale(u,u);InkRenderer.eraseRegions(c,page,background,damage,u,nearby);}
    }
    void trim(){while(tiles.size()>CAPACITY){Iterator<Bitmap> i=tiles.values().iterator();i.next();i.remove();}}
    void direct(Canvas c,Ink.Page page,Bitmap background,SpatialIndex index,Set<Ink.Stroke> hidden,float l,float t,float r,float b,float scale){InkRenderer.region(c,page,background,index.query(l,t,r,b),hidden,l,t,r,b,scale);}
    Bitmap render(Ink.Page page,Bitmap background,Collection<Ink.Stroke> strokes,Key key){
        float u=CanvasViewport.levelScale(key.level),s=SIZE/u,l=key.x*s,t=key.y*s,g=GUTTER/u;
        Bitmap bitmap=Bitmap.createBitmap(SIZE+GUTTER*2,SIZE+GUTTER*2,Bitmap.Config.ARGB_8888);
        try{Canvas c=new Canvas(bitmap);c.translate(GUTTER-l*u,GUTTER-t*u);c.scale(u,u);InkRenderer.region(c,page,background,strokes,Collections.emptySet(),l-g,t-g,l+s+g,t+s+g,u);return bitmap;}
        catch(RuntimeException|OutOfMemoryError e){bitmap.recycle();throw e;}
    }
    void paintTile(Canvas c,Key k,Bitmap bitmap){float u=CanvasViewport.levelScale(k.level),s=SIZE/u,g=GUTTER/u,l=k.x*s,t=k.y*s;c.save();c.clipRect(l,t,l+s,t+s);c.drawBitmap(bitmap,null,new RectF(l-g,t-g,l+s+g,t+s+g),paint);c.restore();}
    // Only use an older zoom level when every part of the requested tile is covered.
    ArrayList<Key> fallback(Key key,float viewLeft,float viewTop,float viewRight,float viewBottom){
        TreeSet<Integer> levels=new TreeSet<>((a,b)->{int d=Integer.compare(Math.abs(a-key.level),Math.abs(b-key.level));return d!=0?d:Integer.compare(a,b);});
        for(Key k:tiles.keySet())if(k.level!=key.level&&Math.abs(k.level-key.level)<=4)levels.add(k.level);
        float s=side(key),l=Math.max(key.x*s,viewLeft),t=Math.max(key.y*s,viewTop),right=Math.min((key.x+1)*s,viewRight),bottom=Math.min((key.y+1)*s,viewBottom);
        for(int other:levels){float os=SIZE/CanvasViewport.levelScale(other);int x1=(int)Math.floor(l/os),y1=(int)Math.floor(t/os),x2=(int)Math.floor(Math.nextDown((double)right)/os),y2=(int)Math.floor(Math.nextDown((double)bottom)/os);ArrayList<Key> found=new ArrayList<>();boolean complete=true;
            for(long y=y1;y<=y2&&complete;y++)for(long x=x1;x<=x2;x++){Key k=new Key(other,(int)x,(int)y);if(!tiles.containsKey(k)){complete=false;break;}found.add(k);}if(complete&&!found.isEmpty())return found;
        }return null;
    }
    Ink.Page snapshot(Ink.Page page){Ink.Page copy=new Ink.Page();copy.layers.clear();for(Ink.Layer layer:page.layers)copy.layers.add(layer.copy());copy.pattern=page.pattern;copy.background=page.background;copy.paper=page.paper;copy.lines=page.lines;copy.width=page.width;copy.height=page.height;copy.spacing=page.spacing;copy.infinite=page.infinite;return copy;}
    void schedule(View view,Ink.Page page,SpatialIndex index,Set<Ink.Stroke> mutable,Key key){
        if(pending.containsKey(key))return;
        if(workers==null||workers.isShutdown()){workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48),r->{Thread t=new Thread(r,"Folio tile renderer");t.setDaemon(true);return t;});workers.allowCoreThreadTimeOut(true);}
        if(workers.getQueue().remainingCapacity()==0)return;
        float u=CanvasViewport.levelScale(key.level),s=SIZE/u,g=GUTTER/u,l=key.x*s,t=key.y*s;
        ArrayList<Ink.Stroke> strokes=index.query(l-g,t-g,l+s+g,t+s+g);for(int i=0;i<strokes.size();i++)if(mutable.contains(strokes.get(i)))strokes.set(i,strokes.get(i).copy());
        Ink.Page paper=snapshot(page);long ticket=revision;pending.put(key,ticket);
        try{workers.execute(()->{Bitmap made=null;try{made=render(paper,null,strokes,key);}catch(RuntimeException|OutOfMemoryError ignored){}final Bitmap result=made;main.post(()->{Long current=pending.get(key);if(current==null||current.longValue()!=ticket||ticket!=revision){if(result!=null)result.recycle();return;}pending.remove(key);if(result!=null){tiles.put(key,result);trim();}else asyncDisabled=true;view.postInvalidateOnAnimation();});});}
        catch(RejectedExecutionException ignored){pending.remove(key);}
    }
    void draw(View view,Canvas c,Ink.Page page,Bitmap background,SpatialIndex index,Set<Ink.Stroke> hidden,Set<Ink.Stroke> mutable,float l,float t,float r,float b,float scale){
        int next=CanvasViewport.level(scale,r-l,b-t);if(level!=next){level=next;unit=CanvasViewport.levelScale(next);}if(!hidden.isEmpty()){direct(c,page,background,index,hidden,l,t,r,b,scale);return;}
        float side=SIZE/unit;int x1=(int)Math.floor(l/side),y1=(int)Math.floor(t/side),x2=(int)Math.floor(r/side),y2=(int)Math.floor(b/side);
        try{for(long y=y1;y<=y2;y++)for(long x=x1;x<=x2;x++){Key key=new Key(level,(int)x,(int)y);Bitmap bitmap=tiles.get(key);if(bitmap==null){ArrayList<Key> old=background==null&&!asyncDisabled?fallback(key,l,t,r,b):null;if(old!=null){float left=key.x*side,top=key.y*side;c.save();c.clipRect(left,top,left+side,top+side);for(Key previous:old)paintTile(c,previous,tiles.get(previous));c.restore();schedule(view,page,index,mutable,key);continue;}float left=key.x*side,top=key.y*side,gutter=GUTTER/unit;bitmap=render(page,background,index.query(left-gutter,top-gutter,left+side+gutter,top+side+gutter),key);tiles.put(key,bitmap);trim();}paintTile(c,key,bitmap);}}
        catch(OutOfMemoryError error){clear();direct(c,page,background,index,hidden,l,t,r,b,scale);}
    }
}
