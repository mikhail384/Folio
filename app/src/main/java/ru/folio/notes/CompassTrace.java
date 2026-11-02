package ru.folio.notes;

/** A compass constrains the pen to a chosen radius and retains only the path the user travels. */
final class CompassTrace {
    float x,y,radius,last,start,sweep;boolean active;Ink.Pt sample;
    void begin(Ink.Stroke stroke,float cx,float cy,float r,float px,float py){
        begin(stroke,cx,cy,r,new Ink.Pt(px,py,1,0,0));
    }
    void begin(Ink.Stroke stroke,float cx,float cy,float r,Ink.Pt cursor){x=cx;y=cy;radius=Ink.clamp(r,5,2400);start=last=(float)Math.atan2(cursor.y-cy,cursor.x-cx);sweep=0;active=true;sample=cursor;stroke.points.add(styled(last,cursor));}
    Ink.Pt styled(float angle,Ink.Pt cursor){return new Ink.Pt(x+radius*(float)Math.cos(angle),y+radius*(float)Math.sin(angle),cursor.p,cursor.tilt,cursor.angle,cursor.azimuth);}
    Ink.Pt point(float angle){return new Ink.Pt(x+radius*(float)Math.cos(angle),y+radius*(float)Math.sin(angle),1,0,0);}
    void move(Ink.Stroke stroke,float px,float py){
        move(stroke,new Ink.Pt(px,py,1,0,0));
    }
    void move(Ink.Stroke stroke,Ink.Pt cursor){
        if(!active||Ink.dist(cursor.x,cursor.y,x,y)<Math.max(2,radius*.08f))return;
        float angle=(float)Math.atan2(cursor.y-y,cursor.x-x),delta=InkGeometry.delta(last,angle);
        if(Math.abs(delta)<.0001f){if(Math.abs(sample.p-cursor.p)>.02f||Math.abs(sample.tilt-cursor.tilt)>.02f||Math.abs(InkGeometry.delta(sample.angle,cursor.angle))>.02f||Math.abs(InkGeometry.delta(sample.azimuth,cursor.azimuth))>.02f){if(stroke.points.size()<300000)stroke.points.add(styled(last,cursor));}sample=cursor;return;}
        int n=Math.min(1024,Math.max(1,(int)Math.ceil(Math.abs(delta)*radius/3)));
        for(int i=1;i<=n&&stroke.points.size()<300000;i++){float t=i/(float)n;stroke.points.add(styled(last+delta*t,InkGeometry.between(sample,cursor,t)));}
        sweep+=delta;last=angle;sample=cursor;
    }
    void clear(){active=false;sample=null;}
}
