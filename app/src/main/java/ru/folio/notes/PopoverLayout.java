package ru.folio.notes;

/** Window-independent placement for a compact inspector adjacent to a visible toolbar item. */
public final class PopoverLayout {
    private PopoverLayout(){}
    public static int[] place(int left,int top,int right,int bottom,int ax,int ay,int ar,int ab,int width,int height,int gap){
        int w=Math.min(width,Math.max(1,right-left-2*gap)),h=Math.min(height,Math.max(1,bottom-top-2*gap));
        int x=Math.max(left+gap,Math.min(right-gap-w,(ax+ar-w)/2));
        int below=ab+gap,above=ay-gap-h,y=below+h<=bottom-gap?below:above>=top+gap?above:Math.max(top+gap,Math.min(below,bottom-gap-h));
        return new int[]{x,y,w,h};
    }
    public static float spring(float t){if(t>=1)return 1;if(t<=0)return 0;return (float)(1-Math.exp(-8.5*t)*Math.cos(10*t));}
}
