package ru.folio.notes;

import java.util.*;

public final class LiveInkTests {
    static int assertions;static void check(boolean yes,String label){assertions++;if(!yes)throw new AssertionError(label);}
    static final class Outline implements StrokeCursor.Sink {
        final ArrayList<float[]> commands=new ArrayList<>();int resets;
        public void clear(){commands.clear();resets++;}public void moveTo(float x,float y){commands.add(new float[]{1,x,y});}public void lineTo(float x,float y){commands.add(new float[]{2,x,y});}
        public void ellipse(float x,float y,float rx,float ry,float a){commands.add(new float[]{3,x,y,rx,ry,a});}
        public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){commands.add(new float[]{4,ax,ay,bx,by,cx,cy,dx,dy});}
    }
    static Ink.Pt point(int i){return new Ink.Pt(100+i*2.5f,200+(float)Math.sin(i*.12)*40,.1f+(i%15)/16f,(i%20)/15f,i*.17f,(float)Math.sin(i*.08));}
    static void equal(Outline a,Outline b){check(a.commands.size()==b.commands.size(),"identical primitive count");for(int i=0;i<a.commands.size();i++){float[] x=a.commands.get(i),y=b.commands.get(i);check(x.length==y.length,"same primitive type");for(int j=0;j<x.length;j++)check(Math.abs(x[j]-y[j])<.0002f,"incremental geometry equals full sweep");}}
    static void parity(){for(String tool:new String[]{"brush","marker","fountain","pencil"}){
        Ink.Stroke s=new Ink.Stroke();s.tool=tool;s.width=20;StrokeCursor cursor=new StrokeCursor();Outline live=new Outline();
        for(int chunk=0;chunk<12;chunk++){
            for(int i=0;i<7;i++)s.points.add(point(s.points.size()));cursor.update(s,live,true);
            Outline full=new Outline();InkGeometry.sweep(s,full);equal(live,full);int primitives=live.commands.size();check(cursor.update(s,live,true)==0&&live.commands.size()==primitives,"unchanged frame builds no geometry");
            float[] a=cursor.bounds(),b=s.bounds();for(int i=0;i<4;i++)check(a[i]==b[i],"incremental marker/layer bounds match complete stroke");
        }
        int resets=live.resets;s.color=0xffe87298;check(cursor.update(s,live,true)==0&&live.resets==resets,"color changes do not rebuild geometry");
        s.width=24;cursor.update(s,live,true);Outline full=new Outline();InkGeometry.sweep(s,full);equal(live,full);
        s.points.set(s.points.size()-1,new Ink.Pt(40,40,.6f,.7f,-3));cursor.update(s,live,true);full=new Outline();InkGeometry.sweep(s,full);equal(live,full);
        s.points.subList(20,s.points.size()).clear();cursor.update(s,live,true);full=new Outline();InkGeometry.sweep(s,full);equal(live,full);
        s.points.clear();cursor.update(s,live,true);check(live.commands.isEmpty(),"empty draft clears cached path");cursor.clear();check(cursor.stroke==null,"finished draft releases reference");
    }}
    static void scaling(){Ink.Stroke s=new Ink.Stroke();s.tool="pen";StrokeCursor cursor=new StrokeCursor();final long[] geometry={0};StrokeCursor.Sink sink=new StrokeCursor.Sink(){public void clear(){}public void moveTo(float x,float y){geometry[0]++;}public void lineTo(float x,float y){geometry[0]++;}public void ellipse(float x,float y,float rx,float ry,float a){}public void quad(float a,float b,float c,float d,float e,float f,float g,float h){}};
        long oldVisits=0;int appended=0;for(int frame=0;frame<2000;frame++){for(int i=0;i<4;i++)s.points.add(point(s.points.size()));oldVisits+=s.points.size();appended+=cursor.update(s,sink,false);}
        check(appended==8000&&geometry[0]==8000,"8000 points built once across 2000 frames");check(oldVisits==8004000,"previous prefix rebuild required 8004000 point visits");check(cursor.moved,"polyline movement retained");
        System.out.println("PASS work-count regression: 8000 new point visits vs 8004000 prefix visits over 2000 synthetic frames. This is geometry work, not a device FPS claim.");
        for(String tool:new String[]{"triangle","line","rect","ellipse","arrow"})check(!StrokeCursor.supports(tool),"editable shape bypasses append-only cache");
        check(StrokeCursor.supports("compass"),"manual compass supports an append-only traveled path");
        Ink.Stroke arc=new Ink.Stroke();arc.tool="compass";StrokeCursor compass=new StrokeCursor();Outline live=new Outline();
        for(int frame=0;frame<30;frame++){arc.points.add(point(frame));compass.update(arc,live,false);Outline full=new Outline();full.moveTo(arc.points.get(0).x,arc.points.get(0).y);for(int i=1;i<arc.points.size();i++)full.lineTo(arc.points.get(i).x,arc.points.get(i).y);equal(live,full);check(compass.update(arc,live,false)==0,"stationary compass frame retains its prefix");}
    }
    static void rotation(){RotationHistory history=new RotationHistory();check(history.at(10,.5f)==.5f,"no sample fallback");check(history.add(350,100)&&history.add(10,200),"wrap samples accepted");check(Math.abs(InkGeometry.delta(history.at(150,0),0))<.00001,"short wrap interpolation");check(Math.abs(InkGeometry.delta(history.at(200,0),(float)Math.toRadians(10)))<.00001,"latest angle has no smoothing delay");check(Math.abs(InkGeometry.delta(history.at(100,0),(float)Math.toRadians(350)))<.00001,"historical pen point keeps historical angle");check(!history.add(20,150),"late packet cannot rotate backward in time");check(history.add(-90,200),"same-time signed sensor update");check(Math.abs(history.at(200,0)-(float)Math.toRadians(270))<.00001,"negative angle normalization");check(!history.add(Float.NaN,300)&&!history.add(1000,300),"invalid angle ignored");for(int i=0;i<10000;i++)history.add(i%360,1000+i);check(history.count==64,"bounded sensor history");check(Math.abs(history.at(11000,0)-(float)Math.toRadians(9999%360))<.00001,"newest sample survives ring wrap");history.clear();check(history.count==0,"hover exit releases angle history");}
    public static void main(String[] args){parity();scaling();rotation();System.out.println("PASS "+assertions+" live-ink and rotation-history assertions; geometry parity, invalidation, bounded storage and timestamped barrel rotation.");}
}
