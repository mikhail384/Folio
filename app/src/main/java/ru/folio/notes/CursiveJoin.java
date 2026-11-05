package ru.folio.notes;

import java.util.ArrayList;

/** Joins adjacent personal glyphs without changing the visible nib width. */
final class CursiveJoin {
    private static final float MIN_PRESSURE=.021f;
    private static final float MAX_PRESSURE=1.5f;

    private CursiveJoin(){}

    static boolean merge(Ink.Stroke from,Ink.Stroke to,float maxGap){
        if(from==null||to==null||from==to||!"fountain".equals(from.tool)||!from.tool.equals(to.tool)
                ||from.color!=to.color||!from.layer.equals(to.layer)||!from.cuts.isEmpty()||!to.cuts.isEmpty()
                ||from.revealed||to.revealed||from.points.size()<2||to.points.size()<2
                ||!Float.isFinite(maxGap)||maxGap<=0||!Float.isFinite(from.width)||!Float.isFinite(to.width)
                ||from.width<=0||to.width<=0)return false;

        int fromCount=from.points.size(),toCount=to.points.size();
        Ink.Pt a0=from.points.get(fromCount-2),a=from.points.get(fromCount-1);
        Ink.Pt b=to.points.get(0),b1=to.points.get(1);
        float distance=Ink.dist(a.x,a.y,b.x,b.y);
        if(!Float.isFinite(distance)||distance>maxGap)return false;

        float ax=a.x-a0.x,ay=a.y-a0.y,bx=b1.x-b.x,by=b1.y-b.y;
        float al=(float)Math.hypot(ax,ay),bl=(float)Math.hypot(bx,by);
        if(al<.01f||bl<.01f)return false;
        ax/=al;ay/=al;bx/=bl;by/=bl;
        float gx=distance<.001f?ax:(b.x-a.x)/distance,gy=distance<.001f?ay:(b.y-a.y)/distance;
        // A connector that reverses either terminal tangent tends to make a loop over the letter.
        if(ax*gx+ay*gy<-.25f||bx*gx+by*gy<-.25f)return false;

        float minimum=Float.POSITIVE_INFINITY,maximum=0;
        for(Ink.Pt point:from.points){float w=visibleWidth(from.width,point.p);minimum=Math.min(minimum,w);maximum=Math.max(maximum,w);}
        for(Ink.Pt point:to.points){float w=visibleWidth(to.width,point.p);minimum=Math.min(minimum,w);maximum=Math.max(maximum,w);}
        float lowBase=maximum/response(MAX_PRESSURE),highBase=minimum/response(MIN_PRESSURE);
        if(!Float.isFinite(lowBase)||!Float.isFinite(highBase)||lowBase>highBase)return false;
        float base=Ink.clamp((float)Math.sqrt(from.width*to.width),lowBase,highBase);
        if(!Float.isFinite(base)||base<=0)return false;

        ArrayList<Ink.Pt> oldPoints=repressure(from.points,from.width,base);
        ArrayList<Ink.Pt> newPoints=repressure(to.points,to.width,base);
        if(oldPoints==null||newPoints==null)return false;

        ArrayList<Ink.Pt> bridge=new ArrayList<>();
        if(distance>.12f){
            float control=Math.min(distance*.34f,Math.max(.6f,maxGap*.18f));
            float c1x=a.x+ax*control,c1y=a.y+ay*control;
            float c2x=b.x-bx*control,c2y=b.y-by*control;
            int count=Math.max(2,Math.min(12,(int)Math.ceil(distance/Math.max(1,maxGap*.12f))));
            float previousProjection=0;
            float startWidth=visibleWidth(from.width,a.p),endWidth=visibleWidth(to.width,b.p);
            for(int i=1;i<count;i++){
                float t=i/(float)count,u=1-t;
                float px=u*u*u*a.x+3*u*u*t*c1x+3*u*t*t*c2x+t*t*t*b.x;
                float py=u*u*u*a.y+3*u*u*t*c1y+3*u*t*t*c2y+t*t*t*b.y;
                float projection=((px-a.x)*gx+(py-a.y)*gy)/Math.max(.0001f,distance);
                float perpendicular=Math.abs((px-a.x)*gy-(py-a.y)*gx);
                if(projection<previousProjection-.015f||projection<-.015f||projection>1.015f
                        ||perpendicular>Math.max(.75f,maxGap*.22f))return false;
                previousProjection=projection;
                float smooth=t*t*(3-2*t),effective=startWidth+(endWidth-startWidth)*smooth;
                float pressure=pressureFor(effective,base);if(!Float.isFinite(pressure))return false;
                bridge.add(new Ink.Pt(px,py,pressure,lerp(a.tilt,b.tilt,t),angleLerp(a.angle,b.angle,t),angleLerp(a.azimuth,b.azimuth,t)));
            }
        }

        from.width=base;
        from.points.clear();from.points.addAll(oldPoints);from.points.addAll(bridge);
        // The first point is already represented by the connector endpoint.
        for(int i=1;i<newPoints.size();i++)from.points.add(newPoints.get(i));
        return true;
    }

    private static float visibleWidth(float base,float pressure){return base*response(Ink.clamp(pressure,0,MAX_PRESSURE));}
    private static float response(float pressure){return .2f+.8f*(float)Math.sqrt(Ink.clamp(pressure,0,MAX_PRESSURE));}
    private static float pressureFor(float visible,float base){
        float value=visible/base,root=(value-.2f)/.8f;
        if(!Float.isFinite(root)||root<Math.sqrt(MIN_PRESSURE)-.0005f||root>Math.sqrt(MAX_PRESSURE)+.0005f)return Float.NaN;
        return Ink.clamp(root*root,MIN_PRESSURE,MAX_PRESSURE);
    }
    private static ArrayList<Ink.Pt> repressure(ArrayList<Ink.Pt> points,float oldBase,float newBase){
        ArrayList<Ink.Pt> result=new ArrayList<>(points.size());
        for(Ink.Pt p:points){float pressure=pressureFor(visibleWidth(oldBase,p.p),newBase);if(!Float.isFinite(pressure))return null;result.add(new Ink.Pt(p.x,p.y,pressure,p.tilt,p.angle,p.azimuth));}
        return result;
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float angleLerp(float a,float b,float t){return a+InkGeometry.delta(a,b)*t;}
}
