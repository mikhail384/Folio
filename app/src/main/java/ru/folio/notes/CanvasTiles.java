package ru.folio.notes;

import android.graphics.*;
import java.util.*;

/** Bounded LRU cache for visible world tiles; all ink remains vector data in the document. */
final class CanvasTiles {
    static final int SIZE=512,GUTTER=2,CAPACITY=40;
    final LinkedHashMap<Long,Bitmap> tiles=new LinkedHashMap<>(48,.75f,true);
    final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);int level=Integer.MIN_VALUE;float unit;
    void clear(){for(Bitmap bitmap:tiles.values())bitmap.recycle();tiles.clear();}
    void invalidate(float[] b){if(unit<=0)return;float side=SIZE/unit;Iterator<Map.Entry<Long,Bitmap>> i=tiles.entrySet().iterator();while(i.hasNext()){Map.Entry<Long,Bitmap> e=i.next();int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();float l=x*side,t=y*side;if(CanvasViewport.overlaps(b,l-GUTTER/unit,t-GUTTER/unit,l+side+GUTTER/unit,t+side+GUTTER/unit)){e.getValue().recycle();i.remove();}}}
    void append(Ink.Stroke stroke){if(unit<=0)return;float side=SIZE/unit;float[] b=stroke.bounds();for(Map.Entry<Long,Bitmap> e:tiles.entrySet()){int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();float left=x*side,top=y*side,gutter=GUTTER/unit;if(!CanvasViewport.overlaps(b,left-gutter,top-gutter,left+side+gutter,top+side+gutter))continue;Canvas c=new Canvas(e.getValue());c.translate(GUTTER-left*unit,GUTTER-top*unit);c.scale(unit,unit);InkRenderer.stroke(c,stroke);}}
    void erase(Ink.Page page,Bitmap background,Ink.Cut cut,SpatialIndex index){EraserDamage damage=new EraserDamage();damage.add(cut);erase(page,background,damage,index);}
    void erase(Ink.Page page,Bitmap background,EraserDamage damage,SpatialIndex index){if(unit<=0||!damage.dirty)return;float side=SIZE/unit;float[] b=damage.padded(2/Math.max(1e-12f,unit));ArrayList<Ink.Stroke> nearby=index.query(b[0],b[1],b[2],b[3]);for(Map.Entry<Long,Bitmap> e:tiles.entrySet()){int x=(int)(e.getKey()>>32),y=(int)(long)e.getKey();float left=x*side,top=y*side,gutter=GUTTER/unit;if(!CanvasViewport.overlaps(b,left-gutter,top-gutter,left+side+gutter,top+side+gutter))continue;Canvas c=new Canvas(e.getValue());c.translate(GUTTER-left*unit,GUTTER-top*unit);c.scale(unit,unit);InkRenderer.eraseRegions(c,page,background,damage,unit,nearby);}}
    void trim(){while(tiles.size()>CAPACITY){Iterator<Bitmap> i=tiles.values().iterator();i.next().recycle();i.remove();}}
    void direct(Canvas c,Ink.Page page,Bitmap background,SpatialIndex index,Set<Ink.Stroke> hidden,float l,float t,float r,float b,float scale){InkRenderer.region(c,page,background,index.query(l,t,r,b),hidden,l,t,r,b,scale);}
    void draw(Canvas c,Ink.Page page,Bitmap background,SpatialIndex index,Set<Ink.Stroke> hidden,float l,float t,float r,float b,float scale){
        int next=CanvasViewport.level(scale,r-l,b-t);if(level!=next){clear();level=next;unit=CanvasViewport.levelScale(next);}if(!hidden.isEmpty()){direct(c,page,background,index,hidden,l,t,r,b,scale);return;}
        float side=SIZE/unit;int x1=(int)Math.floor(l/side),y1=(int)Math.floor(t/side),x2=(int)Math.floor(r/side),y2=(int)Math.floor(b/side);
        try{for(int y=y1;y<=y2;y++)for(int x=x1;x<=x2;x++){long key=CanvasViewport.key(x,y);float left=x*side,top=y*side,gutter=GUTTER/unit;Bitmap bitmap=tiles.get(key);if(bitmap==null){bitmap=Bitmap.createBitmap(SIZE+GUTTER*2,SIZE+GUTTER*2,Bitmap.Config.ARGB_8888);Canvas tile=new Canvas(bitmap);tile.translate(GUTTER-left*unit,GUTTER-top*unit);tile.scale(unit,unit);InkRenderer.region(tile,page,background,index.query(left-gutter,top-gutter,left+side+gutter,top+side+gutter),Collections.emptySet(),left-gutter,top-gutter,left+side+gutter,top+side+gutter,unit);tiles.put(key,bitmap);trim();}c.save();c.clipRect(left,top,left+side,top+side);c.drawBitmap(bitmap,null,new RectF(left-gutter,top-gutter,left+side+gutter,top+side+gutter),paint);c.restore();}}
        catch(OutOfMemoryError error){clear();direct(c,page,background,index,hidden,l,t,r,b,scale);}
    }
}
