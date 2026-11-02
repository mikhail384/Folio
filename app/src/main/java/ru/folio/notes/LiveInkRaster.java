package ru.folio.notes;

import android.graphics.*;

/** Once a draft is long, retain its painted prefix and submit only new segments each frame. */
final class LiveInkRaster {
    static final int THRESHOLD=2048,MAX_SIDE=2048;
    Bitmap bitmap;Ink.Stroke stroke;int count;Ink.Pt tail;float tx,ty,scale,ratio;final Paint p=new Paint(3);final InkRenderer.Ribbon ribbon=new InkRenderer.Ribbon();
    boolean draw(Canvas world,NotesView view,Ink.Stroke next){
        if(next.points.size()<THRESHOLD||!StrokeCursor.supports(next.tool))return false;
        float desired=Math.min(1,MAX_SIDE/(float)Math.max(view.getWidth(),view.getHeight()));int w=Math.max(1,Math.round(view.getWidth()*desired)),h=Math.max(1,Math.round(view.getHeight()*desired));
        try{
            boolean reset=bitmap==null||bitmap.getWidth()!=w||bitmap.getHeight()!=h||stroke!=next||tx!=view.tx||ty!=view.ty||scale!=view.scale()||next.points.size()<count||count>0&&next.points.get(count-1)!=tail;
            if(reset){if(bitmap==null||bitmap.getWidth()!=w||bitmap.getHeight()!=h){if(bitmap!=null)bitmap.recycle();bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}else bitmap.eraseColor(Color.TRANSPARENT);count=0;stroke=next;tx=view.tx;ty=view.ty;scale=view.scale();ratio=desired;}
            if(count<next.points.size()){
                Canvas raster=new Canvas(bitmap);raster.scale(ratio,ratio);raster.translate(tx,ty);raster.scale(scale,scale);p.reset();p.setAntiAlias(true);p.setColor(next.color|0xff000000);p.setStrokeCap(next.tool.equals("tape")?Paint.Cap.BUTT:Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                StrokeTextures.apply(p,next);ribbon.clear();boolean fill=next.tool.equals("brush")||next.tool.equals("fountain")||next.tool.equals("pencil")||next.tool.equals("marker");
                if(fill){InkGeometry.sweepRange(next,count,ribbon);p.setStyle(Paint.Style.FILL);raster.drawPath(ribbon.path,p);}
                else if(next.tool.equals("rainbow")){for(int i=Math.max(1,count);i<next.points.size();i++){Ink.Pt a=next.points.get(i-1),b=next.points.get(i);p.setColor(Color.HSVToColor(new float[]{(i*2.7f+a.x*.08f)%360,.68f,.88f}));p.setStrokeWidth((InkGeometry.diameter(next,a)+InkGeometry.diameter(next,b))/2);raster.drawLine(a.x,a.y,b.x,b.y,p);}}
                else {p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(next.tool.equals("pen")?next.width:InkGeometry.diameter(next,next.points.get(0)));Ink.Pt first=next.points.get(Math.max(0,count-1));ribbon.moveTo(first.x,first.y);for(int i=Math.max(1,count);i<next.points.size();i++){Ink.Pt point=next.points.get(i);ribbon.lineTo(point.x,point.y);}raster.drawPath(ribbon.path,p);}
                count=next.points.size();tail=next.points.get(count-1);
            }
            p.reset();p.setFilterBitmap(true);p.setAlpha(next.tool.equals("marker")?70:next.tool.equals("pencil")?180:next.tool.equals("tape")?(next.revealed?48:245):255);
            world.drawBitmap(bitmap,null,new RectF(view.px(0),view.py(0),view.px(view.getWidth()),view.py(view.getHeight())),p);return true;
        }catch(OutOfMemoryError error){release();return false;}
    }
    void clear(){stroke=null;tail=null;count=0;}
    void release(){clear();if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
