package ru.folio.notes;

import java.util.*;

/** Drawing aids and drawing ink are independent selections. Serialized legacy tool IDs stay valid. */
final class GuideTools {
    static boolean auxiliary(String name){return "line".equals(name)||"compass".equals(name);}
    static boolean ink(String name){switch(name){case "pen":case "fountain":case "pencil":case "brush":case "marker":case "rainbow":case "tape":return true;default:return false;}}
    static String mode(String name){return "line".equals(name)?"ruler":"compass".equals(name)?"compass":"none";}
    static String cycle(String current,String[] order){int at=Arrays.asList(order).indexOf(current);for(int j=1;j<=order.length;j++){String next=order[(at+j)%order.length];if(!auxiliary(next)&&!"eraser".equals(next)&&Arrays.asList(Ink.TOOLS).contains(next))return next;}return "pen";}
    static float centimeter(float density,float xdpi,float ydpi,float degrees){
        if(!Float.isFinite(xdpi)||xdpi<80||xdpi>1200)xdpi=160*density;
        if(!Float.isFinite(ydpi)||ydpi<80||ydpi>1200)ydpi=160*density;
        double angle=Math.toRadians(degrees),x=Math.cos(angle)/xdpi,y=Math.sin(angle)/ydpi;
        return (float)(1/(2.54*density*Math.hypot(x,y)));
    }
    static Ink.Pt rulerPoint(RulerGeometry ruler,Ink.Pt world,float density,float scale,float tx,float ty){
        return rulerPoint(ruler,world,density,scale,tx,ty,0);
    }
    static Ink.Pt rulerPoint(RulerGeometry ruler,Ink.Pt world,float density,float scale,float tx,float ty,float edge){
        Ink.Pt screen=new Ink.Pt((world.x*scale+tx)/density,(world.y*scale+ty)/density,world.p,world.tilt,world.angle,world.azimuth),snap=ruler.project(screen,edge);
        return new Ink.Pt((snap.x*density-tx)/scale,(snap.y*density-ty)/scale,world.p,world.tilt,world.angle,world.azimuth);
    }
}
