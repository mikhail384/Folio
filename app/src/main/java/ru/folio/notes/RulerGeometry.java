package ru.folio.notes;

/** Screen-dp ruler. Its position and physical outline are independent of the document camera. */
final class RulerGeometry {
    static final float THICKNESS=43;
    float x,y,degrees,snapTarget=Float.NaN;boolean placed;
    float localX(float px,float py){double r=Math.toRadians(degrees);return (float)((px-x)*Math.cos(r)+(py-y)*Math.sin(r));}
    float localY(float px,float py){double r=Math.toRadians(degrees);return (float)(-(px-x)*Math.sin(r)+(py-y)*Math.cos(r));}
    float[] world(float u,float v){double r=Math.toRadians(degrees);return new float[]{x+(float)(u*Math.cos(r)-v*Math.sin(r)),y+(float)(u*Math.sin(r)+v*Math.cos(r))};}
    float edge(float px,float py,float distance){float v=localY(px,py),edge=Math.abs(v)<=Math.abs(v-THICKNESS)?0:THICKNESS;return Math.abs(v-edge)<=distance?edge:Float.NaN;}
    float followEdge(float px,float py,float distance,float current){if(Float.isFinite(current)&&Math.abs(localY(px,py)-current)<=distance*1.25f)return current;return edge(px,py,distance);}
    Ink.Pt project(Ink.Pt p){return project(p,0);}
    Ink.Pt project(Ink.Pt p,float edge){float[] w=world(localX(p.x,p.y),edge);return new Ink.Pt(w[0],w[1],p.p,p.tilt,p.angle,p.azimuth);}
    static float angle(float n){if(!Float.isFinite(n))return 0;n=(n+180)%360;if(n<0)n+=360;return n-180;}
    static final float[] ANGLES={0,30,45,60,90,120,135,150,180,210,225,240,270,300,315,330};
    static float nearest(float raw,float tolerance){for(float candidate:ANGLES)if(Math.abs(angle(raw-candidate))<=tolerance)return angle(candidate);return Float.NaN;}
    void rotate(float raw){if(Float.isFinite(snapTarget)&&Math.abs(angle(raw-snapTarget))<=5){degrees=snapTarget;return;}snapTarget=nearest(raw,3);degrees=Float.isFinite(snapTarget)?snapTarget:angle(raw);}
}
