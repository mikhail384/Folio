package ru.folio.notes;

import android.graphics.*;
import java.util.*;

/** Bounded prepared geometry; only visible blocks of a long stroke become Android paths. */
final class CommittedInk {
    static final int MAX_POINTS=2000000,MAX_COMMANDS=800000,MAX_PATHS=4096;
    final LinkedHashMap<Ink.Stroke,StrokeChunks> shapes=new LinkedHashMap<>(64,.75f,true);
    final LinkedHashMap<StrokeChunks.Chunk,Path> paths=new LinkedHashMap<>(128,.75f,true);
    final IdentityHashMap<StrokeChunks.Chunk,Integer> costs=new IdentityHashMap<>();
    int points,commands;
    StrokeChunks get(Ink.Stroke s){StrokeChunks shape=shapes.get(s);if(shape!=null&&shape.matches(s))return shape;if(shape!=null){points-=shape.count;shapes.remove(s);}shape=new StrokeChunks(s);shapes.put(s,shape);points+=shape.count;Iterator<Map.Entry<Ink.Stroke,StrokeChunks>> i=shapes.entrySet().iterator();while((points>MAX_POINTS||shapes.size()>8192)&&shapes.size()>1){Map.Entry<Ink.Stroke,StrokeChunks> e=i.next();points-=e.getValue().count;i.remove();}return shape;}
    void alias(Ink.Stroke old,Ink.Stroke next){StrokeChunks geometry=shapes.remove(old);if(geometry!=null){StrokeChunks displaced=shapes.put(next,geometry);if(displaced!=null)points-=displaced.count;}}
    Path path(Ink.Stroke s,StrokeChunks.Chunk chunk){Path found=paths.get(chunk);if(found!=null)return found;Ink.Stroke fragment=chunk.fragment(s);InkRenderer.Ribbon ribbon=new InkRenderer.Ribbon();
        if(s.tool.equals("brush")||s.tool.equals("fountain")||s.tool.equals("pencil")||s.tool.equals("marker"))InkGeometry.sweep(fragment,ribbon);
        else {Ink.Pt first=fragment.points.get(0);ribbon.moveTo(first.x,first.y);for(int i=1;i<fragment.points.size();i++){Ink.Pt point=fragment.points.get(i);ribbon.lineTo(point.x,point.y);}}
        int cost=Math.max(1,ribbon.commands);paths.put(chunk,ribbon.path);costs.put(chunk,cost);commands+=cost;Iterator<Map.Entry<StrokeChunks.Chunk,Path>> i=paths.entrySet().iterator();while((commands>MAX_COMMANDS||paths.size()>MAX_PATHS)&&paths.size()>1){StrokeChunks.Chunk key=i.next().getKey();commands-=costs.remove(key);i.remove();}return ribbon.path;
    }
    void draw(Canvas c,Ink.Stroke s,Paint p){
        StrokeChunks geometry=get(s);Rect clip=c.getClipBounds();float[] b=geometry.bounds;if(!CanvasViewport.overlaps(b,clip.left,clip.top,clip.right,clip.bottom))return;
        int opacity=s.tool.equals("marker")?70:s.tool.equals("pencil")?180:s.tool.equals("tape")?(s.revealed?48:245):255;
        int layer=-1;if(opacity!=255){layer=c.saveLayerAlpha(Math.max(b[0],clip.left),Math.max(b[1],clip.top),Math.min(b[2],clip.right),Math.min(b[3],clip.bottom),opacity);p.setColor(s.color|0xff000000);p.setAlpha(255);}
        StrokeTextures.apply(p,s);
        boolean fill=s.tool.equals("brush")||s.tool.equals("fountain")||s.tool.equals("pencil")||s.tool.equals("marker");p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(s.tool.equals("pen")?s.width:InkGeometry.diameter(s,s.points.get(0)));if(s.tool.equals("tape"))p.setStrokeCap(Paint.Cap.BUTT);
        if(s.points.size()==1||!fill&&b[2]-b[0]<=s.width*2+.001f&&b[3]-b[1]<=s.width*2+.001f){p.setStyle(Paint.Style.FILL);Ink.Pt point=s.points.get(0);if(fill)c.drawPath(path(s,geometry.chunks.get(0)),p);else c.drawCircle(point.x,point.y,InkGeometry.diameter(s,point)/2,p);}
        else for(StrokeChunks.Chunk chunk:geometry.chunks){if(!CanvasViewport.overlaps(chunk.bounds,clip.left,clip.top,clip.right,clip.bottom))continue;
            if(s.tool.equals("rainbow")){for(int i=Math.max(1,chunk.from+1);i<chunk.to;i++){Ink.Pt a=s.points.get(i-1),z=s.points.get(i);p.setColor(Color.HSVToColor(new float[]{(i*2.7f+a.x*.08f)%360,.68f,.88f}));p.setStrokeWidth((InkGeometry.diameter(s,a)+InkGeometry.diameter(s,z))/2);c.drawLine(a.x,a.y,z.x,z.y,p);}}
            else c.drawPath(path(s,chunk),p);
        }
        if(layer!=-1)c.restoreToCount(layer);
    }
    void clear(){shapes.clear();paths.clear();costs.clear();points=commands=0;StrokeChunks.MARKERS.get().clear();}
}
