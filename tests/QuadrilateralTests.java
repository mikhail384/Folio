package ru.folio.notes;

import java.util.*;

/** Independently drawn quadrilaterals: constraints must follow the user's four edges. */
public final class QuadrilateralTests {
    static int assertions,cases;static Random random=new Random(161);
    static void check(boolean yes,String label){assertions++;if(!yes)throw new AssertionError(label);}
    static final float[][][] FORMS={
        {{-125,-90},{10,-125},{145,95},{-110,55}},
        {{-125,-90},{125,-90},{0,0},{-90,110}},
        {{-100,-80},{45,-77},{125,80},{-100,83}},
        {{-125,-75},{65,-72},{128,74},{-67,79}},
        {{-120,-70},{68,-70},{128,100},{-60,100}},
        {{-125,-75},{125,-69},{122,80},{-129,76}},
        {{-101,-100},{99,-100},{101,100},{-100,100}},
        {{-125,-80},{85,-78},{125,80},{-85,78}}
    };
    static final String[] KINDS={"polygon","polygon","trapezoid","parallelogram","parallelogram","rectangle","square","parallelogram"};
    static Ink.Pt point(float x,float y){return new Ink.Pt(x,y,1,0,0);}
    static ArrayList<Ink.Pt> corners(ShapeRecognizer.Result result){ArrayList<Ink.Pt> v=new ArrayList<>();for(Ink.Pt p:result.stroke.points)if(v.isEmpty()||PolygonFitter.distance(p,v.get(v.size()-1))>.001)v.add(p);if(PolygonFitter.distance(v.get(0),v.get(v.size()-1))<.001)v.remove(v.size()-1);boolean changed=true;while(v.size()>4&&changed){changed=false;for(int i=0;i<v.size();i++){Ink.Pt a=v.get((i+v.size()-1)%v.size()),b=v.get(i),c=v.get((i+1)%v.size());if(Ink.segment(b.x,b.y,a.x,a.y,c.x,c.y)<.1f){v.remove(i);changed=true;break;}}}return v;}
    static float nearest(Ink.Pt point,List<Ink.Pt> points){float best=Float.MAX_VALUE;for(Ink.Pt p:points)best=Math.min(best,PolygonFitter.distance(point,p));return best;}
    static void matrix(){for(int form=0;form<FORMS.length;form++)for(int variant=0;variant<24;variant++){
        float angle=variant*.29f+.05236f,cs=(float)Math.cos(angle),sn=(float)Math.sin(angle),scale=variant%3==0?.5f:1;
        ArrayList<Ink.Pt> expected=new ArrayList<>();for(float[] p:FORMS[form])expected.add(point(300+scale*(p[0]*cs-p[1]*sn),300+scale*(p[0]*sn+p[1]*cs)));
        ArrayList<Ink.Pt> path=new ArrayList<>();for(int edge=0;edge<4;edge++){Ink.Pt a=expected.get(edge),b=expected.get((edge+1)%4);for(int i=0;i<40;i++){float t=i/40f,jx=(random.nextFloat()-.5f)*1.2f*scale,jy=(random.nextFloat()-.5f)*1.2f*scale;path.add(point(a.x+(b.x-a.x)*t+jx,a.y+(b.y-a.y)*t+jy));}}
        Ink.Stroke source=new Ink.Stroke();int offset=variant*11%path.size();for(int i=0;i<path.size();i++)source.points.add(path.get((offset+(variant%2==0?i:-i)+path.size()*2)%path.size()));source.points.add(source.points.get(0));
        ShapeRecognizer.Result result=ShapeRecognizer.recognize(source,1);String label="form "+form+" variant "+variant;
        check(result!=null,label+" recognized");check(result.kind.equals(KINDS[form]),label+" category: "+result.kind);cases++;
        ArrayList<Ink.Pt> fitted=corners(result);check(fitted.size()==4,label+" keeps four true corners");
        float diagonal=350*scale;
        for(Ink.Pt p:expected)check(nearest(p,fitted)<diagonal*(form<=1?.012f:.045f),label+" keeps the drawn corner position");
        check(Math.signum(ShapeRecognizer.area(source.points))==Math.signum(ShapeRecognizer.area(result.stroke.points)),label+" winding preserved");
        float area=Math.abs(ShapeRecognizer.area(expected)),got=Math.abs(ShapeRecognizer.area(fitted));check(Math.abs(area-got)/area<.08f,label+" area/proportions preserved");
        float p=PolygonFitter.parallel(fitted.get(0),fitted.get(1),fitted.get(3),fitted.get(2)),q=PolygonFitter.parallel(fitted.get(1),fitted.get(2),fitted.get(0),fitted.get(3));
        if(KINDS[form].equals("trapezoid"))check(Math.min(p,q)<.0001f&&Math.max(p,q)>.25f,label+" only the intended bases become parallel");
        if(KINDS[form].equals("parallelogram"))check(Math.max(p,q)<.0001f,label+" both opposite pairs become parallel");
        if(form==4){float x=PolygonFitter.distance(fitted.get(0),fitted.get(1)),y=PolygonFitter.distance(fitted.get(1),fitted.get(2));check(Math.abs(x-y)/Math.max(x,y)>.035f,label+" similar side lengths are not made equal");}
        if(form==0)check(p>.2f&&q>.2f,label+" arbitrary four-sided contour stays arbitrary");
    }}
    static void exact(){ArrayList<Ink.Pt> v=new ArrayList<>();double a=Math.toRadians(3);for(float[] p:new float[][]{{-125,-75},{125,-75},{125,75},{-125,75}})v.add(point((float)(p[0]*Math.cos(a)-p[1]*Math.sin(a)),(float)(p[0]*Math.sin(a)+p[1]*Math.cos(a))));PolygonFitter.Match r=PolygonFitter.quadrilateral(v,300);check(r.kind.equals("rectangle"),"near-axis rectangle classified");for(int i=0;i<4;i++)check(PolygonFitter.distance(v.get(i),r.points.get(i))<.0001,"no unwanted axis snapping");
        ArrayList<Ink.Pt> similar=new ArrayList<>();for(float[] p:new float[][]{{-120,-70},{62,-70},{122,100},{-60,100}})similar.add(point(p[0],p[1]));r=PolygonFitter.quadrilateral(similar,300);for(int i=0;i<4;i++)check(PolygonFitter.distance(similar.get(i),r.points.get(i))<.0001,"already straight unequal sides stay unchanged");
    }
    public static void main(String[] args){matrix();exact();System.out.println("PASS "+cases+" quadrilateral drawings; "+assertions+" assertions: own outline, one/two parallel pairs, right angles, winding, proportions, unequal sides and rotation.");}
}
