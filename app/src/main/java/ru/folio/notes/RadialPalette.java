package ru.folio.notes;

/** Shared screen-dp geometry for hover feedback and touch confirmation. Hover does not select. */
final class RadialPalette {
    static final int COUNT=8;static final float TOOL_RADIUS=78,COLOR_RADIUS=32;
    int hovered=-1;
    static final float[] X=new float[COUNT],Y=new float[COUNT];static {for(int i=0;i<COUNT;i++){X[i]=(float)Math.cos((i*45-90)*Math.PI/180);Y[i]=(float)Math.sin((i*45-90)*Math.PI/180);}}
    static float x(int index,float radius){return X[index&7]*radius;}
    static float y(int index,float radius){return Y[index&7]*radius;}
    static int hit(float x,float y){float radius=(float)Math.hypot(x,y);if(!Float.isFinite(radius)||radius<15||radius>=115)return -1;int i=((int)Math.round((Math.atan2(y,x)+Math.PI/2)/(Math.PI/4))+8)%8;return radius>50?i:i+COUNT;}
    boolean hover(float x,float y){int next=hit(x,y);if(next==hovered)return false;hovered=next;return true;}
    void clear(){hovered=-1;}
    float[] targets(){float[] values=new float[COUNT*2];if(hovered>=0)values[hovered]=1;return values;}
    static SmoothSpring[] highlights(){SmoothSpring[] values=new SmoothSpring[COUNT*2];for(int i=0;i<values.length;i++)values[i]=new SmoothSpring(0,28);return values;}
}
