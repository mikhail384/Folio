package ru.folio.notes;

import java.util.*;

/** Sparse world index, including negative coordinates and huge strokes that span many cells. */
final class SpatialIndex {
    static final int CELL=256,MAX_CELLS=256;
    static final class Entry {final Ink.Stroke stroke;final float[] bounds;int order;final boolean large;final StrokeChunks geometry;Entry(Ink.Stroke s,int order,boolean large,StrokeChunks geometry){stroke=s;bounds=geometry.bounds;this.order=order;this.large=large;this.geometry=geometry;}}
    final HashMap<Long,HashSet<Ink.Stroke>> cells=new HashMap<>();
    final IdentityHashMap<Ink.Stroke,Entry> entries=new IdentityHashMap<>();
    final HashMap<String,HashSet<Ink.Stroke>> actions=new HashMap<>();
    final HashSet<Ink.Stroke> large=new HashSet<>();
    int cell(float value){return (int)Math.floor(value/CELL);}
    void clear(){cells.clear();entries.clear();large.clear();actions.clear();}
    void add(Ink.Stroke s,int order){remove(s);if(s.points.isEmpty())return;StrokeChunks geometry=new StrokeChunks(s);float[] b=geometry.bounds;int l=cell(b[0]),t=cell(b[1]),r=cell(b[2]),bottom=cell(b[3]);boolean big=(long)(r-l+1)*(bottom-t+1)>MAX_CELLS;Entry e=new Entry(s,order,big,geometry);entries.put(s,e);actions.computeIfAbsent(s.action,k->new HashSet<>()).add(s);if(big){large.add(s);return;}for(int y=t;y<=bottom;y++)for(int x=l;x<=r;x++)cells.computeIfAbsent(CanvasViewport.key(x,y),k->new HashSet<>()).add(s);}
    void remove(Ink.Stroke s){Entry e=entries.remove(s);if(e==null)return;HashSet<Ink.Stroke> group=actions.get(s.action);if(group!=null){group.remove(s);if(group.isEmpty())actions.remove(s.action);}if(e.large){large.remove(s);return;}for(int y=cell(e.bounds[1]);y<=cell(e.bounds[3]);y++)for(int x=cell(e.bounds[0]);x<=cell(e.bounds[2]);x++){long key=CanvasViewport.key(x,y);HashSet<Ink.Stroke> set=cells.get(key);if(set!=null){set.remove(s);if(set.isEmpty())cells.remove(key);}}}
    void replaceMask(Ink.Stroke old,Ink.Stroke next){Entry e=entries.get(old);if(e==null)return;remove(old);entries.put(next,new Entry(next,e.order,e.large,e.geometry));actions.computeIfAbsent(next.action,k->new HashSet<>()).add(next);if(e.large){large.add(next);return;}for(int y=cell(e.bounds[1]);y<=cell(e.bounds[3]);y++)for(int x=cell(e.bounds[0]);x<=cell(e.bounds[2]);x++)cells.computeIfAbsent(CanvasViewport.key(x,y),k->new HashSet<>()).add(next);}
    void reorder(java.util.List<Ink.Stroke> strokes){for(int i=0;i<strokes.size();i++){Entry e=entries.get(strokes.get(i));if(e!=null)e.order=i;}}
    ArrayList<Ink.Stroke> query(float l,float t,float r,float b){HashSet<Ink.Stroke> found=new HashSet<>();int x1=cell(l),y1=cell(t),x2=cell(r),y2=cell(b);if((long)(x2-x1+1)*(y2-y1+1)>4096)found.addAll(entries.keySet());else {found.addAll(large);for(int y=y1;y<=y2;y++)for(int x=x1;x<=x2;x++){Set<Ink.Stroke> set=cells.get(CanvasViewport.key(x,y));if(set!=null)found.addAll(set);}}ArrayList<Ink.Stroke> result=new ArrayList<>();for(Ink.Stroke s:found){Entry e=entries.get(s);if(e!=null&&CanvasViewport.overlaps(e.bounds,l,t,r,b))result.add(s);}result.sort((a,z)->Integer.compare(entries.get(a).order,entries.get(z).order));return result;}
}
