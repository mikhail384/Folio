package ru.folio.notes;

/** Screen-dp gesture geometry. Two fingers rotate about their moving midpoint; pinch never resizes. */
final class RulerManipulation {
    boolean active,paired;int first=-1,second=-1;
    float originX,originY,originAngle,baseX,baseY,baseAngle,anchorX,anchorY,lastFingerAngle,turn;
    void begin(RulerGeometry r,int id,float x,float y){active=true;paired=false;first=id;second=-1;originX=r.x;originY=r.y;originAngle=r.degrees;one(r,id,x,y);}
    void one(RulerGeometry r,int id,float x,float y){first=id;second=-1;paired=false;baseX=r.x;baseY=r.y;baseAngle=r.degrees;anchorX=x;anchorY=y;}
    void pair(RulerGeometry r,int id,float ax,float ay,float bx,float by){second=id;paired=true;baseX=r.x;baseY=r.y;baseAngle=r.degrees;anchorX=(ax+bx)/2;anchorY=(ay+by)/2;lastFingerAngle=(float)Math.atan2(by-ay,bx-ax);turn=0;}
    void moveOne(RulerGeometry r,float x,float y){if(!active)return;r.x=baseX+x-anchorX;r.y=baseY+y-anchorY;}
    void movePair(RulerGeometry r,float ax,float ay,float bx,float by){
        if(!active||!paired)return;float angle=(float)Math.atan2(by-ay,bx-ax);
        if(Math.hypot(bx-ax,by-ay)>=8){turn+=InkGeometry.delta(lastFingerAngle,angle);lastFingerAngle=angle;}
        r.rotate(baseAngle+(float)Math.toDegrees(turn));double rotate=Math.toRadians(RulerGeometry.angle(r.degrees-baseAngle));float dx=baseX-anchorX,dy=baseY-anchorY;
        r.x=(ax+bx)/2+(float)(dx*Math.cos(rotate)-dy*Math.sin(rotate));r.y=(ay+by)/2+(float)(dx*Math.sin(rotate)+dy*Math.cos(rotate));
    }
    void end(){active=paired=false;first=second=-1;}
    void cancel(RulerGeometry r){if(active){r.x=originX;r.y=originY;r.degrees=originAngle;r.snapTarget=Float.NaN;}end();}
}
