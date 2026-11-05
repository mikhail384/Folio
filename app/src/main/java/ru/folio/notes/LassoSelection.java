package ru.folio.notes;

import java.util.*;

/** Spatially bounded lasso selection; catches strokes crossing the outline between samples. */
final class LassoSelection {
    private LassoSelection(){}
    static HashSet<Ink.Stroke> select(SpatialIndex index,Ink.Page page,List<Ink.Pt> polygon){
        HashSet<Ink.Stroke> selected=new HashSet<>();if(polygon.size()<3)return selected;
        float l=Float.MAX_VALUE,t=l,r=-l,b=-l;for(Ink.Pt p:polygon){l=Math.min(l,p.x);t=Math.min(t,p.y);r=Math.max(r,p.x);b=Math.max(b,p.y);}
        for(Ink.Stroke stroke:index.query(l,t,r,b)){
            if(!page.editable(stroke))continue;SpatialIndex.Entry entry=index.entries.get(stroke);if(entry==null)continue;
            if(stroke.tool.equals("text")){float[] bounds=entry.geometry.bounds;if(Ink.inside(bounds[0],bounds[1],polygon)||Ink.inside(bounds[2],bounds[1],polygon)||Ink.inside(bounds[2],bounds[3],polygon)||Ink.inside(bounds[0],bounds[3],polygon)||insideBounds(polygon,bounds)){selected.add(stroke);continue;}}
            boolean hit=false;for(Ink.Pt point:stroke.points)if(Ink.inside(point.x,point.y,polygon)){hit=true;break;}
            if(!hit&&stroke.points.size()>1){float tolerance=Math.max(.1f,stroke.width*.5f);for(int i=1;i<stroke.points.size()&&!hit;i++){Ink.Pt a=stroke.points.get(i-1),z=stroke.points.get(i);if(Math.max(a.x,z.x)<l-tolerance||Math.min(a.x,z.x)>r+tolerance||Math.max(a.y,z.y)<t-tolerance||Math.min(a.y,z.y)>b+tolerance)continue;for(int j=0;j<polygon.size();j++){Ink.Pt c=polygon.get(j),d=polygon.get((j+1)%polygon.size());if(Ink.segmentsDistance(a.x,a.y,z.x,z.y,c.x,c.y,d.x,d.y)<=tolerance){hit=true;break;}}}}
            if(hit)selected.add(stroke);
        }return selected;
    }
    private static boolean insideBounds(List<Ink.Pt> polygon,float[] b){for(Ink.Pt p:polygon)if(p.x>=b[0]&&p.x<=b[2]&&p.y>=b[1]&&p.y<=b[3])return true;return false;}
}
