package ru.folio.notes;

import java.util.*;

/** Two sparse grids keep both close-up edits and large overviews selective. */
final class SpatialIndex {
    static final int CELL=256,COARSE=8192,MAX_CELLS=256,MAX_QUERY_CELLS=4096;
    static final class Entry {
        final Ink.Stroke stroke;final float[] bounds;int order;final boolean large,coarseLarge;final StrokeChunks geometry;
        Entry(Ink.Stroke s,int order,boolean large,boolean coarseLarge,StrokeChunks geometry){stroke=s;bounds=geometry.bounds;this.order=order;this.large=large;this.coarseLarge=coarseLarge;this.geometry=geometry;}
    }
    final HashMap<Long,HashSet<Ink.Stroke>> cells=new HashMap<>(),coarseCells=new HashMap<>();
    final IdentityHashMap<Ink.Stroke,Entry> entries=new IdentityHashMap<>();
    final HashMap<String,HashSet<Ink.Stroke>> actions=new HashMap<>();
    final HashSet<Ink.Stroke> large=new HashSet<>(),coarseLarge=new HashSet<>();
    final ArrayList<Ink.Stroke> ordered=new ArrayList<>();
    int lastCandidates;
    int cell(float value){return cell(value,CELL);}
    private int cell(float value,int size){return (int)Math.floor(value/size);}
    private long count(float[] b,int size){long w=(long)cell(b[2],size)-cell(b[0],size)+1,h=(long)cell(b[3],size)-cell(b[1],size)+1;if(w<=0||h<=0||w>MAX_CELLS||h>MAX_CELLS)return MAX_CELLS+1;return w*h;}
    private void place(HashMap<Long,HashSet<Ink.Stroke>> grid,Ink.Stroke stroke,float[] b,int size,boolean add){
        int l=cell(b[0],size),t=cell(b[1],size),r=cell(b[2],size),bottom=cell(b[3],size);
        for(long y=t;y<=bottom;y++)for(long x=l;x<=r;x++){
            long key=CanvasViewport.key((int)x,(int)y);
            if(add)grid.computeIfAbsent(key,k->new HashSet<>()).add(stroke);
            else {HashSet<Ink.Stroke> set=grid.get(key);if(set!=null){set.remove(stroke);if(set.isEmpty())grid.remove(key);}}
        }
    }
    void clear(){cells.clear();coarseCells.clear();entries.clear();large.clear();coarseLarge.clear();actions.clear();ordered.clear();lastCandidates=0;}
    private void setOrder(Ink.Stroke stroke,int index){if(index<0)return;while(ordered.size()<=index)ordered.add(null);ordered.set(index,stroke);}
    void add(Ink.Stroke s,int order){remove(s);if(s.points.isEmpty())return;StrokeChunks geometry=new StrokeChunks(s);float[] b=geometry.bounds;boolean big=count(b,CELL)>MAX_CELLS,coarseBig=count(b,COARSE)>MAX_CELLS;Entry e=new Entry(s,order,big,coarseBig,geometry);entries.put(s,e);setOrder(s,order);actions.computeIfAbsent(s.action,k->new HashSet<>()).add(s);if(big)large.add(s);else place(cells,s,b,CELL,true);if(coarseBig)coarseLarge.add(s);else place(coarseCells,s,b,COARSE,true);}
    void remove(Ink.Stroke s){Entry e=entries.remove(s);if(e==null)return;HashSet<Ink.Stroke> group=actions.get(s.action);if(group!=null){group.remove(s);if(group.isEmpty())actions.remove(s.action);}if(e.order>=0&&e.order<ordered.size()&&ordered.get(e.order)==s)ordered.set(e.order,null);if(e.large)large.remove(s);else place(cells,s,e.bounds,CELL,false);if(e.coarseLarge)coarseLarge.remove(s);else place(coarseCells,s,e.bounds,COARSE,false);}
    void replaceMask(Ink.Stroke old,Ink.Stroke next){Entry e=entries.get(old);if(e==null)return;remove(old);entries.put(next,new Entry(next,e.order,e.large,e.coarseLarge,e.geometry));setOrder(next,e.order);actions.computeIfAbsent(next.action,k->new HashSet<>()).add(next);if(e.large)large.add(next);else place(cells,next,e.bounds,CELL,true);if(e.coarseLarge)coarseLarge.add(next);else place(coarseCells,next,e.bounds,COARSE,true);}
    void reorder(java.util.List<Ink.Stroke> strokes){ordered.clear();ordered.addAll(strokes);for(int i=0;i<strokes.size();i++){Entry e=entries.get(strokes.get(i));if(e!=null)e.order=i;}}
    ArrayList<Ink.Stroke> query(float l,float t,float r,float b){
        HashSet<Ink.Stroke> found=new HashSet<>();int x1=cell(l),y1=cell(t),x2=cell(r),y2=cell(b);
        long width=(long)x2-x1+1,height=(long)y2-y1+1;
        if(width>0&&height>0&&width<=MAX_QUERY_CELLS&&height<=MAX_QUERY_CELLS&&width*height<=MAX_QUERY_CELLS){
            found.addAll(large);for(long y=y1;y<=y2;y++)for(long x=x1;x<=x2;x++){Set<Ink.Stroke> set=cells.get(CanvasViewport.key((int)x,(int)y));if(set!=null)found.addAll(set);}
        }else{
            int cx1=cell(l,COARSE),cy1=cell(t,COARSE),cx2=cell(r,COARSE),cy2=cell(b,COARSE);long cw=(long)cx2-cx1+1,ch=(long)cy2-cy1+1;
            found.addAll(coarseLarge);
            if(cw>0&&ch>0&&cw<=MAX_QUERY_CELLS&&ch<=MAX_QUERY_CELLS&&cw*ch<=Math.min((long)MAX_QUERY_CELLS,Math.max(64L,coarseCells.size()*2L))){
                for(long y=cy1;y<=cy2;y++)for(long x=cx1;x<=cx2;x++){Set<Ink.Stroke> set=coarseCells.get(CanvasViewport.key((int)x,(int)y));if(set!=null)found.addAll(set);}
            }else for(Map.Entry<Long,HashSet<Ink.Stroke>> item:coarseCells.entrySet()){
                int x=(int)(item.getKey()>>32),y=(int)(long)item.getKey();if(x>=cx1&&x<=cx2&&y>=cy1&&y<=cy2)found.addAll(item.getValue());
            }
        }
        lastCandidates=found.size();ArrayList<Ink.Stroke> result=new ArrayList<>(found.size());if(found.size()>=1024&&(long)found.size()*4>=ordered.size()){for(Ink.Stroke s:ordered)if(s!=null&&found.contains(s)){Entry e=entries.get(s);if(e!=null&&CanvasViewport.overlaps(e.bounds,l,t,r,b))result.add(s);}return result;}for(Ink.Stroke s:found){Entry e=entries.get(s);if(e!=null&&CanvasViewport.overlaps(e.bounds,l,t,r,b))result.add(s);}result.sort((a,z)->Integer.compare(entries.get(a).order,entries.get(z).order));return result;
    }
}
