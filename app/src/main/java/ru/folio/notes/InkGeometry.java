package ru.folio.notes;

/** Shared, platform-free nib geometry. Swept footprints connect even widely spaced input points. */
public final class InkGeometry {
    private InkGeometry(){}
    public interface Sink {
        void ellipse(float x,float y,float rx,float ry,float angle);
        void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy);
    }
    public static final class Tip {
        public final float rx,ry,angle;
        Tip(float rx,float ry,float angle){this.rx=rx;this.ry=ry;this.angle=angle;}
    }
    public static float delta(float from,float to){float d=(to-from)%(float)(Math.PI*2);if(d>Math.PI)d-=Math.PI*2;if(d<-Math.PI)d+=Math.PI*2;return d;}
    public static float diameter(Ink.Stroke s,Ink.Pt p){
        switch(s.tool){
            case "pen":case "marker":case "tape":return s.width;
            case "fountain":return s.width*(.2f+.8f*(float)Math.sqrt(Ink.clamp(p.p,0,1.5f)));
            case "pencil":return s.width*(.35f+.65f*p.p);
            case "brush":return s.width*(.18f+.82f*(float)Math.sqrt(Ink.clamp(p.p,0,1.5f)));
            default:return s.width*(.38f+.62f*p.p);
        }
    }
    public static Tip tip(Ink.Stroke s,Ink.Pt p){float width=diameter(s,p);if(s.tool.equals("marker"))return new Tip(width/2,width/5,p.angle);if(!s.tool.equals("brush"))return new Tip(width/2,width/2,0);
        float lean=(float)Math.sin(p.tilt),angle=p.angle+.22f*lean*(float)Math.sin(p.azimuth-p.angle);
        return new Tip(width*(1+.45f*lean)/2,width*(.72f-.48f*lean)/2,angle);
    }
    static Ink.Pt between(Ink.Pt a,Ink.Pt b,float t){return new Ink.Pt(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t,a.tilt+(b.tilt-a.tilt)*t,a.angle+delta(a.angle,b.angle)*t,a.azimuth+delta(a.azimuth,b.azimuth)*t);}
    /** A support point of the oriented ellipse in direction (nx,ny), relative to its centre. */
    static float[] support(Tip tip,float nx,float ny){float cos=(float)Math.cos(tip.angle),sin=(float)Math.sin(tip.angle);float u=nx*cos+ny*sin,v=-nx*sin+ny*cos;float divisor=(float)Math.hypot(tip.rx*u,tip.ry*v);if(divisor<1e-6f)return new float[]{0,0};float x=tip.rx*tip.rx*u/divisor,y=tip.ry*tip.ry*v/divisor;return new float[]{cos*x-sin*y,sin*x+cos*y};}
    static void cap(Sink sink,Ink.Pt p,Tip t){sink.ellipse(p.x,p.y,Math.max(.01f,t.rx),Math.max(.01f,t.ry),t.angle);}
    static void bridge(Sink sink,Ink.Pt a,Tip ta,Ink.Pt b,Tip tb){float dx=b.x-a.x,dy=b.y-a.y,length=(float)Math.hypot(dx,dy);if(length<1e-5f)return;float nx=-dy/length,ny=dx/length;float[] u=support(ta,nx,ny),v=support(tb,nx,ny);
        // Consistent winding prevents overlapping subpaths from punching holes in the ribbon.
        sink.quad(a.x+u[0],a.y+u[1],a.x-u[0],a.y-u[1],b.x-v[0],b.y-v[1],b.x+v[0],b.y+v[1]);
    }
    public static void sweep(Ink.Stroke stroke,Sink sink){sweepRange(stroke,0,sink);}
    public static void sweepRange(Ink.Stroke stroke,int start,Sink sink){if(stroke.points.isEmpty())return;if(stroke.tool.equals("marker")){markerSweepRange(stroke,start,sink,null);return;}Ink.Pt previous=stroke.points.get(Math.max(0,start-1));Tip previousTip=tip(stroke,previous);if(start==0)cap(sink,previous,previousTip);
        for(int i=Math.max(1,start);i<stroke.points.size();i++){Ink.Pt from=stroke.points.get(i-1),to=stroke.points.get(i);int count=stroke.tool.equals("brush")?Math.max(1,Math.min(24,(int)Math.ceil(Math.max(Math.max(Math.abs(delta(from.angle,to.angle)),.22f*Math.abs(delta(from.azimuth,to.azimuth)))/.12f,Math.max(Math.abs(from.tilt-to.tilt)/.1f,Math.abs(from.p-to.p)/.15f))))):1;
            for(int j=1;j<=count;j++){Ink.Pt next=j==count?to:between(from,to,j/(float)count);Tip nextTip=tip(stroke,next);bridge(sink,previous,previousTip,next,nextTip);cap(sink,next,nextTip);previous=next;previousTip=nextTip;}
        }
    }
    static void rectangle(Ink.Pt p,Tip t,float[] corners,int offset){float c=(float)Math.cos(t.angle),s=(float)Math.sin(t.angle);for(int i=0;i<4;i++){float x=(i==0||i==3)?-t.rx:t.rx,y=i<2?-t.ry:t.ry;corners[offset+i*2]=p.x+x*c-y*s;corners[offset+i*2+1]=p.y+x*s+y*c;}}
    static float cross(float[] p,int a,int b,int c){return (p[b*2]-p[a*2])*(p[c*2+1]-p[a*2+1])-(p[b*2+1]-p[a*2+1])*(p[c*2]-p[a*2]);}
    /** The hull of successive rectangular footprints bridges sparse input without circular caps. */
    static void markerHull(Sink sink,float[] p,int[] order,int[] hull){for(int i=0;i<8;i++){order[i]=i;for(int j=i;j>0;j--){int a=order[j-1],b=order[j];if(p[a*2]<p[b*2]||p[a*2]==p[b*2]&&p[a*2+1]<=p[b*2+1])break;order[j-1]=b;order[j]=a;}}int n=0;for(int i:order){while(n>1&&cross(p,hull[n-2],hull[n-1],i)<=0)n--;hull[n++]=i;}int lower=n;for(int j=6;j>=0;j--){int i=order[j];while(n>lower&&cross(p,hull[n-2],hull[n-1],i)<=0)n--;hull[n++]=i;}n--;for(int i=1;i<n-1;i++){int a=hull[0]*2,b=hull[i]*2,c=hull[i+1]*2;sink.quad(p[a],p[a+1],p[b],p[b+1],p[c],p[c+1],p[c],p[c+1]);}}
    static void markerSweep(Ink.Stroke stroke,Sink sink,boolean[] hit){markerSweepRange(stroke,0,sink,hit);}
    static void markerSweepRange(Ink.Stroke stroke,int start,Sink sink,boolean[] hit){if(stroke.points.isEmpty())return;float[] corners=new float[16];int[] order=new int[8],hull=new int[16];Ink.Pt previous=stroke.points.get(Math.max(0,start-1));rectangle(previous,tip(stroke,previous),corners,0);if(start==0)sink.quad(corners[0],corners[1],corners[2],corners[3],corners[4],corners[5],corners[6],corners[7]);for(int i=Math.max(1,start);i<stroke.points.size();i++){if(hit!=null&&hit[0])return;Ink.Pt from=stroke.points.get(i-1),to=stroke.points.get(i);int count=Math.max(1,Math.min(24,(int)Math.ceil(Math.abs(delta(from.angle,to.angle))/.12f)));for(int j=1;j<=count;j++){if(hit!=null&&hit[0])return;Ink.Pt next=j==count?to:between(from,to,j/(float)count);rectangle(next,tip(stroke,next),corners,8);markerHull(sink,corners,order,hull);System.arraycopy(corners,8,corners,0,8);previous=next;}}}
    public static boolean markerHit(Ink.Stroke stroke,float x,float y,float radius){final boolean[] hit={false};markerSweep(stroke,new Sink(){public void ellipse(float cx,float cy,float rx,float ry,float a){}public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){if(hit[0])return;float[] xs={ax,bx,cx,dx},ys={ay,by,cy,dy};boolean positive=false,negative=false;float area=0;for(int i=0;i<4;i++){int j=(i+1)%4;float cross=(xs[j]-xs[i])*(y-ys[i])-(ys[j]-ys[i])*(x-xs[i]);positive|=cross>1e-5;negative|=cross<-1e-5;area+=(xs[i]-ax)*(ys[j]-ay)-(ys[i]-ay)*(xs[j]-ax);if(Ink.segment(x,y,xs[i],ys[i],xs[j],ys[j])<=radius){hit[0]=true;return;}}hit[0]=Math.abs(area)>1e-5&&!(positive&&negative);}},hit);return hit[0];}
    static boolean markerSweepHit(Ink.Stroke stroke,float x,float y,float z,float w,float radius){final boolean[] hit={false};markerSweep(stroke,new Sink(){public void ellipse(float cx,float cy,float rx,float ry,float a){}public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){if(hit[0])return;float[] xs={ax,bx,cx,dx},ys={ay,by,cy,dy};for(int i=0;i<4;i++){int j=(i+1)%4;if(Ink.segmentsDistance(x,y,z,w,xs[i],ys[i],xs[j],ys[j])<=radius){hit[0]=true;return;}}hit[0]=insideQuad(x,y,xs,ys)||insideQuad(z,w,xs,ys);}},hit);return hit[0];}
    static boolean insideQuad(float x,float y,float[] xs,float[] ys){boolean positive=false,negative=false;float area=0;for(int i=0;i<4;i++){int j=(i+1)%4;float cross=(xs[j]-xs[i])*(y-ys[i])-(ys[j]-ys[i])*(x-xs[i]);positive|=cross>1e-5;negative|=cross<-1e-5;area+=xs[i]*ys[j]-ys[i]*xs[j];}return Math.abs(area)>1e-5&&!(positive&&negative);}
}
