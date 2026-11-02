package ru.folio.notes;

import java.util.*;

/** Immutable segment blocks shared by hit testing and visible geometry preparation. */
final class StrokeChunks {
    static final int BLOCK=96;
    static final ThreadLocal<MarkerMeshes> MARKERS=ThreadLocal.withInitial(MarkerMeshes::new);
    /** Cached exact polygons, shared across mask replacements, with an 8 MiB ceiling. */
    static final class MarkerMeshes {
        static final int MAX_FLOATS=2*1024*1024,MAX_MESHES=2048;
        final LinkedHashMap<Chunk,MarkerMesh> meshes=new LinkedHashMap<>(64,.75f,true);
        int floats,builds;
        MarkerMesh get(Ink.Stroke stroke,Chunk chunk){
            MarkerMesh mesh=meshes.get(chunk);if(mesh!=null)return mesh;
            mesh=new MarkerMesh(chunk.fragment(stroke));builds++;meshes.put(chunk,mesh);floats+=mesh.data.length;
            Iterator<Map.Entry<Chunk,MarkerMesh>> it=meshes.entrySet().iterator();
            while((floats>MAX_FLOATS||meshes.size()>MAX_MESHES)&&meshes.size()>1){floats-=it.next().getValue().data.length;it.remove();}
            return mesh;
        }
        void clear(){meshes.clear();floats=builds=0;}
    }
    static final class MarkerMesh {
        final float[] data;
        MarkerMesh(Ink.Stroke stroke){
            class Builder implements InkGeometry.Sink {
                float[] values=new float[512];int count;
                public void ellipse(float x,float y,float rx,float ry,float angle){}
                public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
                    if(count+12>values.length)values=Arrays.copyOf(values,values.length*2);
                    values[count++]=Math.min(Math.min(ax,bx),Math.min(cx,dx));values[count++]=Math.min(Math.min(ay,by),Math.min(cy,dy));
                    values[count++]=Math.max(Math.max(ax,bx),Math.max(cx,dx));values[count++]=Math.max(Math.max(ay,by),Math.max(cy,dy));
                    values[count++]=ax;values[count++]=ay;values[count++]=bx;values[count++]=by;
                    values[count++]=cx;values[count++]=cy;values[count++]=dx;values[count++]=dy;
                }
            }
            Builder builder=new Builder();InkGeometry.sweep(stroke,builder);data=Arrays.copyOf(builder.values,builder.count);
        }
        boolean inside(int offset,float x,float y){
            boolean positive=false,negative=false;float area=0,ax=data[offset],ay=data[offset+1];
            for(int i=0;i<4;i++){int a=offset+i*2,b=offset+((i+1)&3)*2;
                float cross=(data[b]-data[a])*(y-data[a+1])-(data[b+1]-data[a+1])*(x-data[a]);
                positive|=cross>1e-5;negative|=cross<-1e-5;
                area+=(data[a]-ax)*(data[b+1]-ay)-(data[a+1]-ay)*(data[b]-ax);
            }return Math.abs(area)>1e-5&&!(positive&&negative);
        }
        boolean sweep(float x,float y,float z,float w,float radius){
            float l=Math.min(x,z)-radius,t=Math.min(y,w)-radius,r=Math.max(x,z)+radius,bottom=Math.max(y,w)+radius;
            for(int offset=0;offset<data.length;offset+=12){
                if(data[offset]>r||data[offset+2]<l||data[offset+1]>bottom||data[offset+3]<t)continue;
                int first=offset+4;
                for(int i=0;i<4;i++){int a=first+i*2,b=first+((i+1)&3)*2;
                    if(Ink.segmentsDistance(x,y,z,w,data[a],data[a+1],data[b],data[b+1])<=radius)return true;}
                if(inside(first,x,y)||inside(first,z,w))return true;
            }return false;
        }
    }
    static final class Chunk {
        final int from,to;final float[] bounds;Ink.Stroke fragment;
        Chunk(Ink.Stroke s,int from,int to){this.from=from;this.to=to;float l=Float.MAX_VALUE,t=l,r=-l,b=-l;for(int i=from;i<to;i++){Ink.Pt p=s.points.get(i);l=Math.min(l,p.x);t=Math.min(t,p.y);r=Math.max(r,p.x);b=Math.max(b,p.y);}bounds=new float[]{l-s.width,t-s.width,r+s.width,b+s.width};}
        Ink.Stroke fragment(Ink.Stroke s){if(fragment==null){fragment=new Ink.Stroke();fragment.tool=s.tool;fragment.width=s.width;fragment.color=s.color;fragment.points.addAll(s.points.subList(s.tool.equals("tape")?Math.max(0,from-1):from,to));}return fragment;}
    }
    final float[] bounds;final ArrayList<Chunk> chunks=new ArrayList<>();final int count;final Ink.Pt first,last;final String tool;final float width;
    StrokeChunks(Ink.Stroke s){count=s.points.size();first=count==0?null:s.points.get(0);last=count==0?null:s.points.get(count-1);tool=s.tool;width=s.width;for(int i=0;i<count;i+=BLOCK)chunks.add(new Chunk(s,Math.max(0,i-1),Math.min(count,i+BLOCK)));if(s.tool.equals("text")||count==0)bounds=s.bounds();else {float l=Float.MAX_VALUE,t=l,r=-l,b=-l;for(Chunk c:chunks){l=Math.min(l,c.bounds[0]);t=Math.min(t,c.bounds[1]);r=Math.max(r,c.bounds[2]);b=Math.max(b,c.bounds[3]);}bounds=new float[]{l,t,r,b};}}
    boolean matches(Ink.Stroke s){int n=s.points.size();return n==count&&width==s.width&&tool.equals(s.tool)&&(n==0||s.points.get(0)==first&&s.points.get(n-1)==last);}
    boolean hit(Ink.Stroke s,float x,float y,float radius){return !s.covered(x,y,radius)&&sweep(s,x,y,x,y,radius);}
    boolean sweep(Ink.Stroke s,float x,float y,float z,float w,float radius){
        float l=Math.min(x,z)-radius,t=Math.min(y,w)-radius,r=Math.max(x,z)+radius,b=Math.max(y,w)+radius;
        if(!CanvasViewport.overlaps(bounds,l,t,r,b))return false;
        if(s.tool.equals("text"))return true;
        if(s.tool.equals("rect")||s.tool.equals("ellipse")){
            int n=Math.max(1,(int)Math.ceil(Ink.dist(x,y,z,w)/Math.max(2,radius/2)));
            for(int i=0;i<=n;i++)if(s.hit(x+(z-x)*i/n,y+(w-y)*i/n,radius))return true;return false;
        }
        float distance=radius+width*(tool.equals("brush")?.9f:.6f);
        for(Chunk chunk:chunks){if(!CanvasViewport.overlaps(chunk.bounds,l,t,r,b))continue;
            if(tool.equals("marker")){if(MARKERS.get().get(s,chunk).sweep(x,y,z,w,radius))return true;continue;}
            if(chunk.to-chunk.from==1){Ink.Pt p=s.points.get(chunk.from);if(Ink.segment(p.x,p.y,x,y,z,w)<distance)return true;}
            for(int i=chunk.from+1;i<chunk.to;i++){Ink.Pt a=s.points.get(i-1),end=s.points.get(i);if(Ink.segmentsDistance(x,y,z,w,a.x,a.y,end.x,end.y)<distance)return true;}
        }return false;
    }

}
