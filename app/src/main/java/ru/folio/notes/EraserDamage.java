package ru.folio.notes;

import java.util.*;

/** Raster damage accumulated until the next frame; vector cuts are never discarded. */
final class EraserDamage {
    static final int MAX_CUTS=64;
    final ArrayList<Ink.Cut> cuts=new ArrayList<>();
    final float[] bounds=new float[4];
    boolean rectangular,dirty;
    void add(Ink.Cut cut){
        float l=Math.min(cut.x,cut.z)-cut.r,t=Math.min(cut.y,cut.w)-cut.r;
        float r=Math.max(cut.x,cut.z)+cut.r,b=Math.max(cut.y,cut.w)+cut.r;
        if(!dirty){bounds[0]=l;bounds[1]=t;bounds[2]=r;bounds[3]=b;dirty=true;}
        else {bounds[0]=Math.min(bounds[0],l);bounds[1]=Math.min(bounds[1],t);bounds[2]=Math.max(bounds[2],r);bounds[3]=Math.max(bounds[3],b);}
        if(rectangular)return;
        append(cuts,cut);
        if(cuts.size()>MAX_CUTS){cuts.clear();rectangular=true;}
    }
    void clear(){cuts.clear();dirty=rectangular=false;}
    float[] padded(float padding){return new float[]{bounds[0]-padding,bounds[1]-padding,bounds[2]+padding,bounds[3]+padding};}
    static void append(ArrayList<Ink.Cut> cuts,Ink.Cut cut){
        int n=cuts.size();if(n>0){Ink.Cut tail=cuts.get(n-1);if(tail.covers(cut))return;
            if(tail.r==cut.r&&tail.z==cut.x&&tail.w==cut.y){float ax=tail.z-tail.x,ay=tail.w-tail.y,bx=cut.z-cut.x,by=cut.w-cut.y;
                if(ax*bx+ay*by>=0&&Math.abs(ax*by-ay*bx)<=.00001f){cuts.set(n-1,new Ink.Cut(tail.x,tail.y,cut.r,cut.z,cut.w));return;}}
        }cuts.add(cut);
    }
}
