package ru.folio.notes;

import java.util.*;

/** Straight-edge fitting and constraints for convex and concave polygons. */
final class PolygonFitter {
    static final class Match {String kind;ArrayList<Ink.Pt> points;float score;Match(String kind,ArrayList<Ink.Pt> points,float score){this.kind=kind;this.points=points;this.score=score;}}
    static float distance(Ink.Pt a,Ink.Pt b){return Ink.dist(a.x,a.y,b.x,b.y);}
    static void rdp(List<Ink.Pt> p,int start,int end,float tolerance,ArrayList<Ink.Pt> out){int at=-1;float max=tolerance;for(int i=start+1;i<end;i++){float d=Ink.segment(p.get(i).x,p.get(i).y,p.get(start).x,p.get(start).y,p.get(end).x,p.get(end).y);if(d>max){max=d;at=i;}}if(at<0){out.add(p.get(start));return;}rdp(p,start,at,tolerance,out);rdp(p,at,end,tolerance,out);}
    static ArrayList<Ink.Pt> vertices(List<Ink.Pt> samples,float diagonal){int a=0,b=0;float far=0;for(int i=0;i<samples.size();i++)for(int j=i+1;j<samples.size();j++){float d=distance(samples.get(i),samples.get(j));if(d>far){far=d;a=i;b=j;}}ArrayList<Ink.Pt> loop=new ArrayList<>();for(int i=0;i<samples.size();i++)loop.add(samples.get((a+i)%samples.size()));loop.add(loop.get(0));int split=(b-a+samples.size())%samples.size();ArrayList<Ink.Pt> result=new ArrayList<>();rdp(loop,0,split,diagonal*.022f,result);rdp(loop,split,loop.size()-1,diagonal*.022f,result);boolean changed=true;while(changed&&result.size()>3){changed=false;for(int i=0;i<result.size();i++){Ink.Pt prev=result.get((i+result.size()-1)%result.size()),p=result.get(i),next=result.get((i+1)%result.size());if(distance(prev,p)<diagonal*.055f||Ink.segment(p.x,p.y,prev.x,prev.y,next.x,next.y)<diagonal*.018f){result.remove(i);changed=true;break;}}}return result;}
    static float cornerFraction(List<Ink.Pt> samples,List<Ink.Pt> vertices){int strong=0,n=samples.size();for(int i=0;i<vertices.size();i++){Ink.Pt v=vertices.get(i);int at=0;float best=Float.MAX_VALUE;for(int j=0;j<n;j++){float d=distance(v,samples.get(j));if(d<best){best=d;at=j;}}int span=vertices.size()>=9?1:2;float corner=turn(samples,(at+n-span)%n,(at+span)%n,at);Ink.Pt next=vertices.get((i+1)%vertices.size());float mx=(v.x+next.x)/2,my=(v.y+next.y)/2;int mid=0;best=Float.MAX_VALUE;for(int j=0;j<n;j++){float d=Ink.dist(mx,my,samples.get(j).x,samples.get(j).y);if(d<best){best=d;mid=j;}}float edge=turn(samples,(mid+n-span)%n,(mid+span)%n,mid);if(corner>.22f&&corner>edge*1.7f+.08f)strong++;}return strong/(float)vertices.size();}
    static float turn(List<Ink.Pt> p,int a,int b,int mid){Ink.Pt x=p.get(a),m=p.get(mid),y=p.get(b);float ux=m.x-x.x,uy=m.y-x.y,vx=y.x-m.x,vy=y.y-m.y;return (float)Math.abs(Math.atan2(ux*vy-uy*vx,ux*vx+uy*vy));}
    static float parallel(Ink.Pt a,Ink.Pt b,Ink.Pt c,Ink.Pt d){float x=b.x-a.x,y=b.y-a.y,u=d.x-c.x,v=d.y-c.y;return Math.abs(x*v-y*u)/Math.max(.001f,(float)Math.hypot(x,y)*(float)Math.hypot(u,v));}
    static ArrayList<Ink.Pt> close(List<Ink.Pt> p){ArrayList<Ink.Pt> out=new ArrayList<>(p);out.add(out.get(0));return out;}
    // Constrain the drawn edges, never fit a quadrilateral to a stock outline.
    static Match quadrilateral(ArrayList<Ink.Pt> v,float diagonal){
        Match unchanged=new Match("polygon",new ArrayList<>(v),0);
        if(ShapeRecognizer.hull(v).size()!=4)return unchanged;
        Ink.Pt a=v.get(0),b=v.get(1),c=v.get(2),d=v.get(3);
        float p=parallel(a,b,d,c),q=parallel(b,c,a,d),near=(float)Math.sin(Math.toRadians(10));
        float cx=(a.x+b.x+c.x+d.x)/4,cy=(a.y+b.y+c.y+d.y)/4;
        float vx=(b.x-a.x+c.x-d.x)/2,vy=(b.y-a.y+c.y-d.y)/2;
        float wx=(d.x-a.x+c.x-b.x)/2,wy=(d.y-a.y+c.y-b.y)/2;
        boolean right=true;
        for(int i=0;i<4;i++){Ink.Pt x=v.get(i),y=v.get((i+1)%4),z=v.get((i+2)%4);float ux=x.x-y.x,uy=x.y-y.y,tx=z.x-y.x,ty=z.y-y.y;if(Math.abs(ux*tx+uy*ty)>near*distance(x,y)*distance(y,z))right=false;}
        if(right&&p<near&&q<near){
            // Least-squares orthogonal directions retain the user's rotation, even near an axis.
            double angle=.5*Math.atan2(2*(vx*vy-wx*wy),vx*vx-vy*vy-wx*wx+wy*wy);
            float ux=(float)Math.cos(angle),uy=(float)Math.sin(angle),nx=-uy,ny=ux;
            float width=vx*ux+vy*uy,height=wx*nx+wy*ny;
            boolean square=Math.abs(Math.abs(width)-Math.abs(height))/Math.max(Math.abs(width),Math.abs(height))<.065f;
            if(square){float mean=(Math.abs(width)+Math.abs(height))/2;width=Math.copySign(mean,width);height=Math.copySign(mean,height);}
            ArrayList<Ink.Pt> rect=corners(cx,cy,ux*width,uy*width,nx*height,ny*height);
            if(gentle(v,rect,diagonal))return new Match(square?"square":"rectangle",rect,0);
        }
        if(p<near&&q<near){
            ArrayList<Ink.Pt> parallelogram=corners(cx,cy,vx,vy,wx,wy);
            if(gentle(v,parallelogram,diagonal)){
                float x=(float)Math.hypot(vx,vy),y=(float)Math.hypot(wx,wy);
                // A label may describe equal sides; it must not change their lengths.
                return new Match(Math.abs(x-y)/Math.max(x,y)<.025f?"rhombus":"parallelogram",parallelogram,0);
            }
        }
        if(Math.min(p,q)<near){
            int shift=p<=q?0:1;Ink.Pt x=v.get(shift),y=v.get((shift+1)%4),z=v.get((shift+2)%4),w=v.get((shift+3)%4);
            // TLS direction of the two bases about their own midpoints; no fixed-angle snapping.
            float dx=y.x-x.x,dy=y.y-x.y,ex=z.x-w.x,ey=z.y-w.y;
            double angle=.5*Math.atan2(2*(dx*dy+ex*ey),dx*dx-dy*dy+ex*ex-ey*ey);
            float ux=(float)Math.cos(angle),uy=(float)Math.sin(angle),nx=-uy,ny=ux;
            float top=((x.x+y.x)*nx+(x.y+y.y)*ny)/2,bottom=((z.x+w.x)*nx+(z.y+w.y)*ny)/2;
            ArrayList<Ink.Pt> trap=new ArrayList<>(v);
            for(int i=0;i<4;i++){int index=(shift+i)%4;Ink.Pt point=v.get(index);float along=point.x*ux+point.y*uy,across=i<2?top:bottom;trap.set(index,ShapeRecognizer.pt(along*ux+across*nx,along*uy+across*ny));}
            if(gentle(v,trap,diagonal))return new Match("trapezoid",trap,0);
        }
        return unchanged;
    }
    static ArrayList<Ink.Pt> corners(float cx,float cy,float vx,float vy,float wx,float wy){ArrayList<Ink.Pt> out=new ArrayList<>();for(int[] sign:new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}})out.add(ShapeRecognizer.pt(cx+(sign[0]*vx+sign[1]*wx)/2,cy+(sign[0]*vy+sign[1]*wy)/2));return out;}
    static boolean gentle(List<Ink.Pt> source,List<Ink.Pt> fitted,float diagonal){
        if(Math.signum(ShapeRecognizer.area(source))!=Math.signum(ShapeRecognizer.area(fitted))||ShapeRecognizer.hull(fitted).size()!=4)return false;
        for(int i=0;i<4;i++){float local=Math.min(distance(source.get(i),source.get((i+1)%4)),distance(source.get(i),source.get((i+3)%4)));if(distance(source.get(i),fitted.get(i))>Math.min(diagonal*.035f,local*.12f))return false;}
        return true;
    }
    static ArrayList<Ink.Pt> triangle(ArrayList<Ink.Pt> v){float[] side={distance(v.get(0),v.get(1)),distance(v.get(1),v.get(2)),distance(v.get(2),v.get(0))};float min=Math.min(side[0],Math.min(side[1],side[2])),max=Math.max(side[0],Math.max(side[1],side[2]));if((max-min)/max<.1f){Ink.Pt a=v.get(0),b=v.get(1),c=v.get(2);float dx=b.x-a.x,dy=b.y-a.y,sign=ShapeRecognizer.cross(a,b,c)>=0?1:-1;ArrayList<Ink.Pt> out=new ArrayList<>(v);out.set(2,ShapeRecognizer.pt((a.x+b.x)/2-dy*.8660254f*sign,(a.y+b.y)/2+dx*.8660254f*sign));return out;}for(int i=0;i<3;i++){Ink.Pt a=v.get(i),b=v.get((i+1)%3),c=v.get((i+2)%3);float dx=b.x-a.x,dy=b.y-a.y,ex=c.x-a.x,ey=c.y-a.y,len=(float)Math.hypot(dx,dy),other=(float)Math.hypot(ex,ey);if(Math.abs(dx*ex+dy*ey)/(len*other)<.10f){float sign=dx*ey-dy*ex>=0?1:-1;ArrayList<Ink.Pt> out=new ArrayList<>(v);out.set((i+2)%3,ShapeRecognizer.pt(a.x-dy/len*other*sign,a.y+dx/len*other*sign));return out;}}for(int i=0;i<3;i++){Ink.Pt a=v.get(i),b=v.get((i+1)%3),c=v.get((i+2)%3);float ab=distance(a,b),ac=distance(a,c);if(Math.abs(ab-ac)/Math.max(ab,ac)<.10f){float dx=c.x-b.x,dy=c.y-b.y,len=(float)Math.hypot(dx,dy),mx=(b.x+c.x)/2,my=(b.y+c.y)/2,height=((a.x-mx)*-dy+(a.y-my)*dx)/len;ArrayList<Ink.Pt> out=new ArrayList<>(v);out.set(i,ShapeRecognizer.pt(mx-dy/len*height,my+dx/len*height));return out;}}return v;}
    static Match regularFit(List<Ink.Pt> samples,float diagonal){float cx=0,cy=0;for(Ink.Pt p:samples){cx+=p.x;cy+=p.y;}cx/=samples.size();cy/=samples.size();float radius=0;for(Ink.Pt p:samples)radius+=Ink.dist(cx,cy,p.x,p.y);radius/=samples.size();float[] amplitudes=new float[15],phases=new float[15];for(int n=3;n<15;n++){float co=0,si=0;for(Ink.Pt p:samples){double a=Math.atan2(p.y-cy,p.x-cx)*n;float delta=Ink.dist(cx,cy,p.x,p.y)-radius;co+=delta*Math.cos(a);si+=delta*Math.sin(a);}amplitudes[n]=2*(float)Math.hypot(co,si)/samples.size();phases[n]=(float)Math.atan2(si,co)/n;}float best=Float.MAX_VALUE;ArrayList<Ink.Pt> winner=null;
        for(int n=5;n<=12;n++){float neighbours=(amplitudes[n-1]+amplitudes[n+1]+amplitudes[n-2]+amplitudes[n+2])/4;if(amplitudes[n]<radius*.0065f||amplitudes[n]<neighbours*1.35f)continue;ArrayList<Ink.Pt> unit=new ArrayList<>();for(int i=0;i<=n;i++)unit.add(ShapeRecognizer.pt((float)Math.cos(i*Math.PI*2/n),(float)Math.sin(i*Math.PI*2/n)));float mean=0;for(Ink.Pt p:ShapeRecognizer.resample(unit,97))mean+=Math.hypot(p.x,p.y);mean/=97;float outer=radius/mean;for(int step=-4;step<=4;step++)for(float correction:new float[]{.985f,1,1.015f}){float phase=phases[n]+step*(float)Math.toRadians(.75);ArrayList<Ink.Pt> candidate=new ArrayList<>();for(int i=0;i<n;i++){double a=phase+i*Math.PI*2/n;candidate.add(ShapeRecognizer.pt(cx+outer*correction*(float)Math.cos(a),cy+outer*correction*(float)Math.sin(a)));}float error=ShapeRecognizer.boundaryError(samples,candidate)/diagonal;if(error<best){best=error;winner=candidate;}}}return winner==null||best>.018f?null:new Match("polygon",close(winner),best);
    }
    static ArrayList<Ink.Pt> refine(List<Ink.Pt> samples,ArrayList<Ink.Pt> vertices,float diagonal){int n=vertices.size();float[][] lines=new float[n][4];for(int i=0;i<n;i++){Ink.Pt a=vertices.get(i),b=vertices.get((i+1)%n);float dx=b.x-a.x,dy=b.y-a.y,len=dx*dx+dy*dy;ArrayList<Ink.Pt> points=new ArrayList<>();for(Ink.Pt p:samples){float t=((p.x-a.x)*dx+(p.y-a.y)*dy)/Math.max(.001f,len);if(t<.12f||t>.88f||Ink.segment(p.x,p.y,a.x,a.y,b.x,b.y)>diagonal*.04f)continue;int closest=i;float best=Ink.segment(p.x,p.y,a.x,a.y,b.x,b.y);for(int j=0;j<n;j++){Ink.Pt c=vertices.get(j),d=vertices.get((j+1)%n);float error=Ink.segment(p.x,p.y,c.x,c.y,d.x,d.y);if(error<best){best=error;closest=j;}}if(closest==i)points.add(p);}float mx=(a.x+b.x)/2,my=(a.y+b.y)/2,vx=dx,vy=dy;if(points.size()>=3){mx=my=0;for(Ink.Pt p:points){mx+=p.x;my+=p.y;}mx/=points.size();my/=points.size();double xx=0,yy=0,xy=0;for(Ink.Pt p:points){xx+=(p.x-mx)*(p.x-mx);yy+=(p.y-my)*(p.y-my);xy+=(p.x-mx)*(p.y-my);}double angle=.5*Math.atan2(2*xy,xx-yy);vx=(float)Math.cos(angle);vy=(float)Math.sin(angle);}lines[i]=new float[]{mx,my,vx,vy};}ArrayList<Ink.Pt> result=new ArrayList<>();for(int i=0;i<n;i++){float[] a=lines[(i+n-1)%n],b=lines[i];float determinant=a[2]*b[3]-a[3]*b[2];Ink.Pt original=vertices.get(i);if(Math.abs(determinant)<.001f){result.add(original);continue;}float t=((b[0]-a[0])*b[3]-(b[1]-a[1])*b[2])/determinant,x=a[0]+a[2]*t,y=a[1]+a[3]*t;result.add(Ink.dist(x,y,original.x,original.y)<diagonal*.08f?ShapeRecognizer.pt(x,y):original);}return result;}
    static Match fit(List<Ink.Pt> samples,float diagonal,float path){ArrayList<Ink.Pt> vertices=vertices(samples,diagonal),convex=ShapeRecognizer.hull(samples);float hullArea=Math.abs(ShapeRecognizer.area(convex)),deficit=hullArea>0?1-Math.abs(ShapeRecognizer.area(samples))/hullArea:1;ShapeRecognizer.simplify(convex,diagonal*.04f);if(deficit<.06f&&convex.size()<=vertices.size()&&convex.size()>=3&&convex.size()<=12&&ShapeRecognizer.boundaryError(samples,convex)/diagonal<.03f&&cornerFraction(samples,convex)>=.64f)vertices=convex;if(vertices.size()<3||vertices.size()>24)return null;vertices=refine(samples,vertices,diagonal);int previousSize=vertices.size();ShapeRecognizer.simplify(vertices,diagonal*(vertices.size()<=5?.03f:.012f));if(vertices.size()!=previousSize)vertices=refine(samples,vertices,diagonal);float perimeter=ShapeRecognizer.perimeter(vertices),error=ShapeRecognizer.boundaryError(samples,vertices)/diagonal;if(path/perimeter<.82f||path/perimeter>1.17f||error>.025f||cornerFraction(samples,vertices)<.64f)return null;String kind="polygon";ArrayList<Ink.Pt> clean=new ArrayList<>(vertices);
        if(vertices.size()==3){kind="triangle";clean=triangle(vertices);}
        if(vertices.size()==4){Match quad=quadrilateral(vertices,diagonal);clean=quad.points;kind=quad.kind;}
        if(vertices.size()>=5&&vertices.size()<=12){float cx=0,cy=0;for(Ink.Pt p:vertices){cx+=p.x;cy+=p.y;}cx/=vertices.size();cy/=vertices.size();float radius=0,cos=0,sin=0,direction=ShapeRecognizer.area(close(vertices))>=0?1:-1;for(int i=0;i<vertices.size();i++){Ink.Pt p=vertices.get(i);radius+=Ink.dist(cx,cy,p.x,p.y);double phase=Math.atan2(p.y-cy,p.x-cx)-direction*i*Math.PI*2/vertices.size();cos+=Math.cos(phase);sin+=Math.sin(phase);}radius/=vertices.size();float phase=(float)Math.atan2(sin,cos);ArrayList<Ink.Pt> regular=new ArrayList<>();for(int i=0;i<vertices.size();i++){double a=phase+direction*i*Math.PI*2/vertices.size();regular.add(ShapeRecognizer.pt(cx+radius*(float)Math.cos(a),cy+radius*(float)Math.sin(a)));}if(ShapeRecognizer.boundaryError(samples,regular)/diagonal<.025f){clean=regular;kind="polygon";}}
        float score=ShapeRecognizer.boundaryError(samples,clean)/diagonal;if(score>.038f)return null;if(kind.equals("polygon")&&cornerFraction(samples,vertices)<.88f)score+=Math.max(0,vertices.size()-5)*.0008f;return new Match(kind,close(clean),score);
    }
}
