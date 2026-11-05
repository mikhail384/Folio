package ru.folio.notes;

/** Shared screen-dp geometry for hover feedback and touch confirmation. Hover does not select. */
final class RadialPalette {
    static final int TOOL_COUNT=12,COLOR_COUNT=8,LEGACY_TOOL_COUNT=8;static final float TOOL_RADIUS=78,COLOR_RADIUS=32;
    int hovered=-1;
    static float x(int index,float radius){return x(index,radius,8);}
    static float y(int index,float radius){return y(index,radius,8);}
    static float x(int index,float radius,int count){return (float)Math.cos((index*360f/Math.max(1,count)-90)*Math.PI/180)*radius;}
    static float y(int index,float radius,int count){return (float)Math.sin((index*360f/Math.max(1,count)-90)*Math.PI/180)*radius;}
    static int toolHit(float x,float y,int count){float radius=(float)Math.hypot(x,y);if(!Float.isFinite(radius)||radius<=50||radius>=115)return -1;return ((int)Math.round((Math.atan2(y,x)+Math.PI/2)*count/(Math.PI*2))+count)%count;}
    static int colorHit(float x,float y){float radius=(float)Math.hypot(x,y);if(!Float.isFinite(radius)||radius<15||radius>=50)return -1;return ((int)Math.round((Math.atan2(y,x)+Math.PI/2)/(Math.PI/4))+8)%8;}
    static int hit(float x,float y){float radius=(float)Math.hypot(x,y);if(radius>50)return toolHit(x,y,LEGACY_TOOL_COUNT);int color=colorHit(x,y);return color<0?-1:color+LEGACY_TOOL_COUNT;}
    boolean hover(float x,float y){int next=hit(x,y);if(next==hovered)return false;hovered=next;return true;}
    void clear(){hovered=-1;}
    float[] targets(){float[] values=new float[TOOL_COUNT+COLOR_COUNT];if(hovered>=0&&hovered<values.length)values[hovered]=1;return values;}
    static SmoothSpring[] highlights(){SmoothSpring[] values=new SmoothSpring[TOOL_COUNT+COLOR_COUNT];for(int i=0;i<values.length;i++)values[i]=new SmoothSpring(0,28);return values;}
}
