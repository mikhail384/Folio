package ru.folio.notes;

/** Retains the append-only live draft prefix, so each frame builds only new geometry. */
final class StrokeCursor {
    interface Sink extends InkGeometry.Sink {void clear();void moveTo(float x,float y);void lineTo(float x,float y);}
    Ink.Stroke stroke;Ink.Pt first,tail;String tool;float width;int count;boolean moved;float left,top,right,bottom;
    static boolean supports(String tool){switch(tool){case "compass":case "pen":case "fountain":case "pencil":case "brush":case "marker":case "rainbow":case "tape":return true;default:return false;}}
    void clear(){stroke=null;first=tail=null;tool=null;count=0;moved=false;}
    int update(Ink.Stroke next,Sink sink,boolean ribbon){
        int size=next.points.size();
        boolean reset=stroke!=next||tool==null||!tool.equals(next.tool)||width!=next.width||size<count||count>0&&(next.points.get(0)!=first||next.points.get(count-1)!=tail);
        if(reset){sink.clear();count=0;moved=false;left=top=Float.MAX_VALUE;right=bottom=-Float.MAX_VALUE;}
        int start=count;
        if(size>start){
            if(ribbon)InkGeometry.sweepRange(next,start,sink);
            else {if(start==0){Ink.Pt p=next.points.get(0);sink.moveTo(p.x,p.y);}Ink.Pt origin=next.points.get(0);for(int i=Math.max(1,start);i<size;i++){Ink.Pt p=next.points.get(i);sink.lineTo(p.x,p.y);float dx=p.x-origin.x,dy=p.y-origin.y;moved|=dx*dx+dy*dy>1e-6f;}}
            for(int i=start;i<size;i++){Ink.Pt p=next.points.get(i);left=Math.min(left,p.x);top=Math.min(top,p.y);right=Math.max(right,p.x);bottom=Math.max(bottom,p.y);}
        }
        stroke=next;tool=next.tool;width=next.width;count=size;first=size==0?null:next.points.get(0);tail=size==0?null:next.points.get(size-1);
        return size-start;
    }
    float[] bounds(){return new float[]{left-width,top-width,right+width,bottom+width};}
}
