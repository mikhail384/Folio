package ru.folio.notes;

import android.graphics.*;
import java.util.*;

public final class InkRenderer {
    private static final ThreadLocal<Paint> PAINT=ThreadLocal.withInitial(()->new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
    static final ThreadLocal<CommittedInk> COMMITTED=ThreadLocal.withInitial(CommittedInk::new);
    static void erased(Ink.Stroke old,Ink.Stroke next){COMMITTED.get().alias(old,next);}
    static Paint paint(){Paint p=PAINT.get();p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);return p;}
    public static void paper(Canvas c,Ink.Page page,Bitmap background){
        c.drawColor(page.paper);Paint p=paint();
        if(background!=null){c.drawBitmap(background,null,new RectF(0,0,page.width,page.height),p);return;}
        p.setColor(page.lines);p.setStrokeWidth(.8f);float step=page.spacing;
        if(page.pattern.equals("dots")){for(float y=step;y<page.height;y+=step)for(float x=step;x<page.width;x+=step)c.drawCircle(x,y,1.15f,p);}
        else {if(page.pattern.equals("grid"))for(float x=step;x<page.width;x+=step)c.drawLine(x,0,x,page.height,p);
            if(page.pattern.equals("grid")||page.pattern.equals("lined"))for(float y=step;y<page.height;y+=step)c.drawLine(0,y,page.width,y,p);
            if(page.pattern.equals("lined")){p.setColor(0x55da8b98);c.drawLine(step*2,0,step*2,page.height,p);}}
    }
    public static void page(Canvas c,Ink.Page p,Bitmap bg,Set<Ink.Stroke> hidden){page(c,p,bg,hidden,null);}
    static void page(Canvas c,Ink.Page p,Bitmap bg,Set<Ink.Stroke> hidden,Prepared prepared){c.save();c.clipRect(0,0,p.width,p.height);paper(c,p,bg);for(Ink.Stroke s:p.strokes)if(p.visible(s)&&!hidden.contains(s))stroke(c,s,prepared);c.restore();}
    public static void region(Canvas c,Ink.Page page,Bitmap background,Collection<Ink.Stroke> strokes,Set<Ink.Stroke> hidden,float l,float t,float r,float b,float scale){region(c,page,background,strokes,hidden,l,t,r,b,scale,null);}
    static void region(Canvas c,Ink.Page page,Bitmap background,Collection<Ink.Stroke> strokes,Set<Ink.Stroke> hidden,float l,float t,float r,float b,float scale,Prepared prepared){c.save();c.clipRect(l,t,r,b);paperRegion(c,page,background,l,t,r,b,scale);for(Ink.Stroke stroke:strokes)if(page.visible(stroke)&&!hidden.contains(stroke))stroke(c,stroke,prepared);c.restore();}
    public static void paperRegion(Canvas c,Ink.Page page,Bitmap background,float l,float t,float r,float b,float scale){if(!page.infinite)scale=1;Paint p=paint();p.setColor(page.paper);c.drawRect(l,t,r,b,p);if(!page.infinite&&background!=null){c.drawBitmap(background,null,new RectF(0,0,page.width,page.height),p);return;}p.setColor(page.lines);p.setStrokeWidth(Math.max(.8f,.6f/Math.max(1e-12f,scale)));float step=CanvasViewport.gridStep(page.spacing,Math.max(1e-12f,scale),page.pattern.equals("dots")),startX=(float)Math.floor(l/step)*step,startY=(float)Math.floor(t/step)*step;
        if(page.pattern.equals("dots")){for(float y=startY;y<=b;y+=step)for(float x=startX;x<=r;x+=step)if(page.infinite||x>0&&y>0&&x<page.width&&y<page.height)c.drawCircle(x,y,Math.max(1.15f,.7f/Math.max(1e-12f,scale)),p);}else {if(page.pattern.equals("grid"))for(float x=startX;x<=r;x+=step)if(page.infinite||x>0&&x<page.width)c.drawLine(x,t,x,b,p);if(page.pattern.equals("grid")||page.pattern.equals("lined"))for(float y=startY;y<=b;y+=step)if(page.infinite||y>0&&y<page.height)c.drawLine(l,y,r,y,p);}if(!page.infinite&&page.pattern.equals("lined")){p.setColor(0x55da8b98);c.drawLine(page.spacing*2,t,page.spacing*2,b,p);}if(background!=null){p.setAlpha(255);c.drawBitmap(background,null,new RectF(0,0,page.width,page.height),p);}
    }
    static float size(Ink.Stroke s,Ink.Pt p){return InkGeometry.diameter(s,p);}
    static final class Ribbon implements StrokeCursor.Sink {
        int commands;final Path path=new Path(),oval=new Path();final Matrix matrix=new Matrix();final RectF bounds=new RectF();
        public void clear(){path.rewind();commands=0;}public void moveTo(float x,float y){path.moveTo(x,y);commands++;}public void lineTo(float x,float y){path.lineTo(x,y);commands++;}
        public void ellipse(float x,float y,float rx,float ry,float angle){commands+=5;oval.rewind();bounds.set(x-rx,y-ry,x+rx,y+ry);oval.addOval(bounds,Path.Direction.CW);matrix.setRotate((float)Math.toDegrees(angle),x,y);oval.transform(matrix);path.addPath(oval);}
        public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){commands+=5;path.moveTo(ax,ay);path.lineTo(bx,by);path.lineTo(cx,cy);path.lineTo(dx,dy);path.close();}
    }
    static final class Prepared {final StrokeCursor cursor=new StrokeCursor();final Ribbon ink=new Ribbon();void clear(){cursor.clear();ink.clear();}}
    public static void stroke(Canvas c,Ink.Stroke s){stroke(c,s,null);}
    static void stroke(Canvas c,Ink.Stroke s,Prepared prepared){
        if(prepared!=null&&!StrokeCursor.supports(s.tool))prepared=null;
        if(s.tool.equals("laser"))return;
        c.save();if(!s.cuts.isEmpty()){Rect clip=c.getClipBounds();Path holes=new Path();for(Ink.Cut cut:s.cuts)if(CanvasViewport.overlaps(cut.bounds(),clip.left,clip.top,clip.right,clip.bottom))cutPath(holes,cut);c.clipOutPath(holes);}rawStroke(c,s,prepared);c.restore();
    }
    static void cutPath(Path path,Ink.Cut cut){
        path.addCircle(cut.x,cut.y,cut.r,Path.Direction.CW);if(cut.x==cut.z&&cut.y==cut.w)return;
        path.addCircle(cut.z,cut.w,cut.r,Path.Direction.CW);float dx=cut.z-cut.x,dy=cut.w-cut.y,length=(float)Math.hypot(dx,dy),nx=-dy/length*cut.r,ny=dx/length*cut.r;
        path.moveTo(cut.x+nx,cut.y+ny);path.lineTo(cut.x-nx,cut.y-ny);path.lineTo(cut.z-nx,cut.w-ny);path.lineTo(cut.z+nx,cut.w+ny);path.close();
    }
    static void eraseRegion(Canvas c,Ink.Page page,Bitmap background,Ink.Cut cut,float scale){
        eraseRegion(c,page,background,cut,scale,page.strokes);}
    static void eraseRegion(Canvas c,Ink.Page page,Bitmap background,Ink.Cut cut,float scale,Collection<Ink.Stroke> nearby){
        EraserDamage damage=new EraserDamage();damage.add(cut);eraseRegions(c,page,background,damage,scale,nearby);
    }
    static void eraseRegions(Canvas c,Ink.Page page,Bitmap background,EraserDamage damage,float scale,Collection<Ink.Stroke> nearby){
        if(!damage.dirty)return;float fringe=2/Math.max(1e-12f,scale);float[] b=damage.padded(fringe);c.save();
        // Repaint the antialiased fringe as well; only the original vector cuts erase ink.
        if(damage.rectangular)c.clipRect(b[0],b[1],b[2],b[3]);else {Path holes=new Path();for(Ink.Cut cut:damage.cuts)cutPath(holes,new Ink.Cut(cut.x,cut.y,cut.r+fringe,cut.z,cut.w));c.clipPath(holes);}
        if(!page.infinite)c.clipRect(0,0,page.width,page.height);
        paperRegion(c,page,background,b[0],b[1],b[2],b[3],scale);
        // The ordered strokes are composited once, preserving separate translucent marker layers.
        for(Ink.Stroke stroke:nearby)if(page.visible(stroke)&&CanvasViewport.overlaps(COMMITTED.get().get(stroke).bounds,b[0],b[1],b[2],b[3]))stroke(c,stroke);
        c.restore();
    }
    static void rawStroke(Canvas c,Ink.Stroke s,Prepared prepared){
        if(s.points.isEmpty())return;Paint p=paint();p.setColor(s.color);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Ink.Pt first=s.points.get(0),last=s.points.get(s.points.size()-1);
        if(s.tool.equals("text")){p.setTextSize(s.width);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));int row=0;for(String line:s.text.split("\n",-1))c.drawText(line,first.x,first.y+s.width+(row++)*s.width*1.25f,p);return;}
        p.setStrokeWidth(s.width);p.setStyle(Paint.Style.STROKE);
        if(s.tool.equals("line")){c.drawLine(first.x,first.y,last.x,last.y,p);return;}
        if(s.tool.equals("rect")||s.tool.equals("ellipse")){RectF r=new RectF(Math.min(first.x,last.x),Math.min(first.y,last.y),Math.max(first.x,last.x),Math.max(first.y,last.y));if(s.tool.equals("rect"))c.drawRect(r,p);else c.drawOval(r,p);return;}
        if(prepared==null){COMMITTED.get().draw(c,s,p);return;}
        boolean ribbon=s.tool.equals("brush")||s.tool.equals("fountain")||s.tool.equals("pencil")||s.tool.equals("marker");
        if(prepared!=null&&!s.tool.equals("rainbow"))prepared.cursor.update(s,prepared.ink,ribbon);
        int layer=-1;
        if(s.tool.equals("marker")||s.tool.equals("pencil")){float[] b=prepared==null?s.bounds():prepared.cursor.bounds();layer=c.saveLayerAlpha(new RectF(b[0],b[1],b[2],b[3]),s.tool.equals("marker")?70:180);p.setColor(s.color|0xff000000);}
        StrokeTextures.apply(p,s);
        if(s.tool.equals("tape")){p.setAlpha(s.revealed?48:245);p.setStrokeCap(Paint.Cap.BUTT);}
        if(ribbon){Ribbon ink;if(prepared!=null)ink=prepared.ink;else {ink=new Ribbon();InkGeometry.sweep(s,ink);}p.setStyle(Paint.Style.FILL);c.drawPath(ink.path,p);}
        else if(s.points.size()==1){p.setStyle(Paint.Style.FILL);c.drawCircle(first.x,first.y,size(s,first)/2,p);}
        else if(!s.tool.equals("rainbow")){Path continuous;if(prepared!=null){continuous=prepared.ink.path;}else {continuous=new Path();continuous.moveTo(first.x,first.y);for(int i=1;i<s.points.size();i++){Ink.Pt point=s.points.get(i);continuous.lineTo(point.x,point.y);}}p.setStrokeWidth(s.tool.equals("pen")?s.width:size(s,first));boolean moved=prepared!=null&&prepared.cursor.moved;if(prepared==null)for(Ink.Pt point:s.points)if(Ink.dist(first.x,first.y,point.x,point.y)>.001f){moved=true;break;}if(moved)c.drawPath(continuous,p);else {p.setStyle(Paint.Style.FILL);c.drawCircle(first.x,first.y,size(s,first)/2,p);}}
        else for(int i=1;i<s.points.size();i++){
            Ink.Pt a=s.points.get(i-1),b=s.points.get(i);float width=(size(s,a)+size(s,b))/2;
            if(s.tool.equals("rainbow"))p.setColor(Color.HSVToColor(new float[]{(i*2.7f+a.x*.08f)%360,.68f,.88f}));
            p.setStrokeWidth(width);c.drawLine(a.x,a.y,b.x,b.y,p);
        }
        if(layer!=-1)c.restoreToCount(layer);
    }
}
