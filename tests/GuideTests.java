package ru.folio.notes;

import java.util.*;

/** Production geometry, independent vector references. Desktop only; no Android touch/render claims. */
public final class GuideTests {
    static int assertions;static final Random random=new Random(91921);
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);assertions++;}
    static void near(float actual,float expected,float tolerance,String label){check(Math.abs(actual-expected)<=tolerance,label+": "+actual+" vs "+expected);}
    static void attributes(Ink.Pt point,Ink.Pt source){check(point.p==source.p&&point.tilt==source.tilt&&point.angle==source.angle&&point.azimuth==source.azimuth,"projection retains pressure, tilt, barrel roll and azimuth");}
    static void infinite(){
        RulerGeometry r=new RulerGeometry();r.x=230;r.y=370;r.placed=true;
        for(int i=0;i<4000;i++){
            r.degrees=random.nextFloat()*360-180;double angle=Math.toRadians(r.degrees),dx=Math.cos(angle),dy=Math.sin(angle);
            float u=(random.nextFloat()-.5f)*40000,v=(random.nextFloat()-.5f)*80;
            Ink.Pt source=new Ink.Pt(r.x+(float)(u*dx-v*dy),r.y+(float)(u*dy+v*dx),random.nextFloat(),.7f,2.8f,-1.2f),point=r.project(source);
            near(point.x,r.x+(float)(u*dx),.006f,"unlimited longitudinal x");near(point.y,r.y+(float)(u*dy),.006f,"unlimited longitudinal y");attributes(point,source);
            float density=.8f+random.nextFloat()*3.2f,scale=.15f+random.nextFloat()*6,tx=(random.nextFloat()-.5f)*20000,ty=(random.nextFloat()-.5f)*20000;
            Ink.Pt world=new Ink.Pt((source.x*density-tx)/scale,(source.y*density-ty)/scale,source.p,source.tilt,source.angle,source.azimuth);
            Ink.Pt constrained=GuideTools.rulerPoint(r,world,density,scale,tx,ty);
            near((constrained.x*scale+tx)/density,point.x,.02f,"camera-independent screen edge x");near((constrained.y*scale+ty)/density,point.y,.02f,"camera-independent screen edge y");attributes(constrained,source);
            check(r.x==230&&r.y==370&&r.placed,"camera operations leave ruler screen anchor unchanged");
        }
        r.degrees=0;near(r.project(new Ink.Pt(-100000,390,1,0,0)).x,-100000,.01f,"no left endpoint");near(r.project(new Ink.Pt(100000,390,1,0,0)).x,100000,.01f,"no right endpoint");
        System.out.println("PASS infinite projection in both directions, rotated edge, independent camera transforms and full nib attributes");
    }
    static void fingers(){
        RulerGeometry r=new RulerGeometry();RulerManipulation g=new RulerManipulation();r.x=400;r.y=300;r.degrees=17;
        g.begin(r,7,230,320);g.moveOne(r,280,350);near(r.x,450,.001f,"one finger translates x");near(r.y,330,.001f,"one finger translates y");check(r.degrees==17,"one finger cannot rotate");
        g.pair(r,42,350,330,550,330);g.movePair(r,480,250,480,450);near(r.x,480,.001f,"pair translates its midpoint x");near(r.y,350,.001f,"pair translates its midpoint y");near(r.degrees,107,.001f,"two-finger quarter turn");
        g.movePair(r,480,150,480,550);near(r.x,480,.001f,"pinch keeps anchor x");near(r.y,350,.001f,"pinch keeps anchor y");near(r.degrees,107,.001f,"pinch cannot change angle or resize ruler");
        g.one(r,42,480,550);g.moveOne(r,500,570);near(r.x,500,.001f,"remaining pointer rebases without jump");near(r.y,370,.001f,"remaining pointer translates");g.cancel(r);near(r.x,400,.001f,"cancel restores original x");near(r.y,300,.001f,"cancel restores original y");near(r.degrees,17,.001f,"cancel restores original angle");check(!g.active&&!g.paired,"cancel releases ownership");
        // The ruler anchor is offset from the fingers. It rotates about their midpoint, not about itself.
        r.x=300;r.y=280;r.degrees=0;g.begin(r,4,200,300);g.pair(r,9,200,300,400,300);g.movePair(r,350,250,350,450);near(r.x,370,.001f,"moving pivot rotates offset x");near(r.y,350,.001f,"moving pivot rotates offset y");near(r.degrees,90,.001f,"quarter-turn magnet");g.end();
        for(float target:RulerGeometry.ANGLES){
            r.x=400;r.y=300;r.degrees=0;r.snapTarget=Float.NaN;g.begin(r,1,300,300);g.pair(r,2,300,300,500,300);
            float prior=0;for(int n=1;n<=200;n++){float theta=target*n/200;double a=Math.toRadians(theta);g.movePair(r,400-100*(float)Math.cos(a),300-100*(float)Math.sin(a),400+100*(float)Math.cos(a),300+100*(float)Math.sin(a));check(Math.abs(RulerGeometry.angle(r.degrees-prior))<7,"rotation has no wrap jump");prior=r.degrees;}
            near(RulerGeometry.angle(r.degrees-target),0,.001f,"magnet reached in each quadrant");check(Float.isFinite(r.snapTarget),"magnet exposes selected angle");g.end();
        }
        System.out.println("PASS one/two-finger movement, moving pivot, fixed size, pointer handoff, cancellation and all angle magnets across wrap");
    }
    static void bothEdges(){
        RulerGeometry ruler=new RulerGeometry();ruler.x=310;ruler.y=440;
        for(float degrees:new float[]{0,30,45,90,135,180,225,270,315,-170}){
            ruler.degrees=degrees;double angle=Math.toRadians(degrees),dx=Math.cos(angle),dy=Math.sin(angle);
            for(float edge:new float[]{0,RulerGeometry.THICKNESS})for(float offset:new float[]{-19,-5,0,5,19}){
                for(float u:new float[]{-20000,-300,0,300,20000}){
                    float v=edge+offset;Ink.Pt source=new Ink.Pt(ruler.x+(float)(u*dx-v*dy),ruler.y+(float)(u*dy+v*dx),.37f,.6f,2.4f,-.8f);
                    near(ruler.edge(source.x,source.y,22),edge,.001f,"nearest edge in every orientation and on either side of body");
                    Ink.Pt projected=ruler.project(source,edge);
                    near(projected.x,ruler.x+(float)(u*dx-edge*dy),.006f,"both infinite edges x");near(projected.y,ruler.y+(float)(u*dy+edge*dx),.006f,"both infinite edges y");attributes(projected,source);
                    float density=2.2f,scale=.27f,tx=8800,ty=-6100;
                    Ink.Pt world=new Ink.Pt((source.x*density-tx)/scale,(source.y*density-ty)/scale,source.p,source.tilt,source.angle,source.azimuth),snapped=GuideTools.rulerPoint(ruler,world,density,scale,tx,ty,edge);
                    near((snapped.x*scale+tx)/density,projected.x,.015f,"both edges respect camera x");near((snapped.y*scale+ty)/density,projected.y,.015f,"both edges respect camera y");attributes(snapped,source);
                    // Explicit projection retains the requested edge; following it is tested separately below.
                    float other=edge==0?RulerGeometry.THICKNESS:0;Ink.Pt crossing=new Ink.Pt(ruler.x+(float)(u*dx-other*dy),ruler.y+(float)(u*dy+other*dx),1,0,0);
                    near(ruler.localY(ruler.project(crossing,edge).x,ruler.project(crossing,edge).y),edge,.003f,"explicit projection selects requested edge");
                }
            }
            for(float v:new float[]{-23,-100,RulerGeometry.THICKNESS+23,RulerGeometry.THICKNESS+100}){
                float px=ruler.x-(float)(v*dy),py=ruler.y+(float)(v*dx);check(Float.isNaN(ruler.edge(px,py,22)),"distant pen stays free of magnets");
            }
        }
        ruler.degrees=0;check(ruler.edge(0,RulerGeometry.THICKNESS/2+ruler.y,22)==0,"midpoint tie is deterministic");
        System.out.println("PASS top/bottom magnets, rotated infinite edges, free drawing outside reach, camera/nib parity and explicit edge projection");
    }
    static void dynamicMagnet(){
        RulerGeometry ruler=new RulerGeometry();ruler.x=300;ruler.y=500;
        for(float degrees:new float[]{0,31,45,90,135,180,225,270,315})for(float density:new float[]{1,2,3}){
            ruler.degrees=degrees;float xdpi=density*160,ydpi=density*180;
            float distance=GuideTools.centimeter(density,xdpi,ydpi,degrees+90)*.2f;
            double normal=Math.toRadians(degrees+90);near((float)(Math.hypot(distance*density*Math.cos(normal)/xdpi,distance*density*Math.sin(normal)/ydpi)*25.4),2,.00001f,"magnet capture is two physical millimeters perpendicular to ruler");
            for(float target:new float[]{0,RulerGeometry.THICKNESS}){
                float edge=Float.NaN,side=target==0?-1:1;
                for(float far:new float[]{50,200,2000}){float[] p=ruler.world(-500,target+side*far);edge=ruler.followEdge(p[0],p[1],distance,edge);check(Float.isNaN(edge),"any distant starting position stays free");}
                float[] before=ruler.world(-100,target+side*distance*1.1f);edge=ruler.followEdge(before[0],before[1],distance,edge);check(Float.isNaN(edge),"outside capture distance remains free");
                float[] inside=ruler.world(0,target+side*distance*.9f);edge=ruler.followEdge(inside[0],inside[1],distance,edge);check(edge==target,"mid-stroke arrival captures either edge");
                float[] jitter=ruler.world(50,target+side*distance*1.1f);edge=ruler.followEdge(jitter[0],jitter[1],distance,edge);check(edge==target,"small boundary jitter does not flicker");
                float[] leaving=ruler.world(100,target+side*distance*1.3f);edge=ruler.followEdge(leaving[0],leaving[1],distance,edge);check(Float.isNaN(edge),"moving away releases without lifting pen");
                float other=target==0?RulerGeometry.THICKNESS:0;float[] next=ruler.world(150,other);edge=ruler.followEdge(next[0],next[1],distance,edge);check(edge==other,"same stroke can capture opposite edge");
                Ink.Pt source=new Ink.Pt(next[0]+1,next[1]+1,.6f,.7f,2.4f,-.8f),projected=ruler.project(source,edge);near(ruler.localY(projected.x,projected.y),other,.001f,"captured point immediately lies on edge");attributes(projected,source);
                for(float scale:new float[]{.1f,1,8}){Ink.Pt world=new Ink.Pt((source.x*density-6100)/scale,(source.y*density+4300)/scale,source.p,source.tilt,source.angle,source.azimuth),snap=GuideTools.rulerPoint(ruler,world,density,scale,6100,-4300,edge);near((snap.x*scale+6100)/density,projected.x,.004f,"dynamic capture independent of document zoom x");near((snap.y*scale-4300)/density,projected.y,.004f,"dynamic capture independent of document zoom y");}
            }
        }
        System.out.println("PASS mid-stroke two-millimeter capture from any start, release/re-entry/opposite edge, jitter stability and camera independence");
    }
    static void selections(){
        String[] order={"line","pen","compass","eraser","brush","invalid","marker"};String current="pen";
        for(int i=0;i<30;i++){current=GuideTools.cycle(current,order);check(Arrays.asList("pen","brush","marker").contains(current),"cycle never selects a drawing aid or eraser");}
        check(GuideTools.cycle("line",new String[]{"line","compass","eraser"}).equals("pen"),"legacy all-aid palette falls back to ink");check(GuideTools.cycle("pen",new String[0]).equals("pen"),"empty palette has safe fallback");
        for(String tool:Ink.TOOLS)check(GuideTools.ink(tool)==Arrays.asList("pen","fountain","pencil","brush","marker","rainbow","tape").contains(tool),"only free ink snaps: "+tool);
        check(GuideTools.auxiliary("line")&&GuideTools.auxiliary("compass")&&!GuideTools.auxiliary("pen"),"legacy IDs identify aids without altering serialized tools");
        System.out.println("PASS independent aid/ink policy and defensive squeeze cycle with legacy/custom tool lists");
    }
    static void compass(){
        for(String tool:new String[]{"pen","fountain","pencil","brush","marker"}){
            Ink.Stroke s=new Ink.Stroke();s.tool=tool;s.width=9;s.color=0xffb42356;CompassTrace trace=new CompassTrace();
            Ink.Pt begin=new Ink.Pt(220,100,.2f,.1f,3.05f,-3.1f);trace.begin(s,100,100,80,begin);attributes(s.points.get(0),begin);
            for(int i=1;i<=40;i++){double a=Math.PI*i/40;trace.move(s,new Ink.Pt(100+120*(float)Math.cos(a),100+120*(float)Math.sin(a),.2f+i*.02f,.1f+i*.01f,3.05f+i*.012f,-3.1f+i*.01f));}
            check(s.tool.equals(tool)&&s.width==9&&s.color==0xffb42356,"compass retains chosen ink style");near(trace.sweep,(float)Math.PI,.0001f,"manual semicircle sweep");
            Ink.Pt end=s.points.get(s.points.size()-1);near(end.p,1,.0001f,"end pressure retained");near(end.tilt,.5f,.0001f,"end tilt retained");near(InkGeometry.delta(end.angle,3.53f),0,.0001f,"end barrel roll retained");near(InkGeometry.delta(end.azimuth,-2.7f),0,.0001f,"end azimuth retained");
            float previous=.19f;for(Ink.Pt point:s.points){near(Ink.dist(point.x,point.y,100,100),80,.001f,"all interpolated arc points lie at chosen radius");check(point.p>=previous-.0001&&point.p<=1.0001,"pressure varies smoothly along arc");previous=point.p;}
            int n=s.points.size();trace.move(s,new Ink.Pt(-20,100,1,.5f,3.53f,-2.3f));check(s.points.size()==n+1,"stationary tilt direction change retained");trace.clear();trace.move(s,20,20);check(s.points.size()==n+1,"lift releases compass");
        }
        System.out.println("PASS manual compass retains round/pressure/brush/marker style, interpolated tilt/roll and exact traveled arc");
    }
    static void centimeters(){
        for(int n=0;n<1000;n++){
            float density=1+random.nextFloat()*3,xdpi=160+random.nextFloat()*400,ydpi=160+random.nextFloat()*400,degrees=random.nextFloat()*360;
            double a=Math.toRadians(degrees);float dp=GuideTools.centimeter(density,xdpi,ydpi,degrees);
            double inches=Math.hypot(dp*density*Math.cos(a)/xdpi,dp*density*Math.sin(a)/ydpi);near((float)(inches*2.54),1,.00001f,"centimeter ticks use screen physical metrics in any direction");
        }
        near(GuideTools.centimeter(2,Float.NaN,0,0),160/2.54f,.0001f,"bad device metrics fall back to dp size");
        System.out.println("PASS centimeter scale follows screen density/orientation, never document zoom");
    }
    public static void main(String[] args){infinite();bothEdges();dynamicMagnet();fingers();selections();compass();centimeters();System.out.println("PASS "+assertions+" drawing-aid assertions; desktop math only, Android validation pending");}
}
