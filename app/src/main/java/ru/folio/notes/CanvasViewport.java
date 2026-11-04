package ru.folio.notes;

/** Screen-independent world geometry. No bitmap has the size of the drawing plane. */
public final class CanvasViewport {
    public static final float LIMIT=10000000,MIN_ZOOM=.000001f,MAX_ZOOM=12;
    static boolean overlaps(float[] a,float l,float t,float r,float b){return a[0]<=r&&a[2]>=l&&a[1]<=b&&a[3]>=t;}
    public static float[] content(Ink.Page page){return content(page,null);}
    static float[] content(Ink.Page page,SpatialIndex index){float l=Float.MAX_VALUE,t=l,r=-l,b=-l;for(Ink.Stroke stroke:page.strokes){if(!page.visible(stroke))continue;SpatialIndex.Entry entry=index==null?null:index.entries.get(stroke);float[] bounds=entry==null?stroke.bounds():entry.geometry.bounds;if(!Float.isFinite(bounds[0])||!Float.isFinite(bounds[1])||!Float.isFinite(bounds[2])||!Float.isFinite(bounds[3]))continue;l=Math.min(l,bounds[0]);t=Math.min(t,bounds[1]);r=Math.max(r,bounds[2]);b=Math.max(b,bounds[3]);}if(!page.background.isEmpty()){l=Math.min(l,0);t=Math.min(t,0);r=Math.max(r,page.width);b=Math.max(b,page.height);}return l==Float.MAX_VALUE?new float[]{0,0,page.width,page.height}:new float[]{l,t,r,b};}
    public static float[] exportBounds(Ink.Page page){if(!page.infinite)return new float[]{0,0,page.width,page.height};float[] b=content(page);if(page.strokes.isEmpty()&&page.background.isEmpty())return b;float cx=(b[0]+b[2])/2,cy=(b[1]+b[3])/2,w=Math.max(320,b[2]-b[0]+96),h=Math.max(240,b[3]-b[1]+96);return new float[]{cx-w/2,cy-h/2,cx+w/2,cy+h/2};}
    public static float gridStep(float spacing,float scale,boolean dots){float step=Math.max(8,spacing),pixels=dots?12:6;while(step*scale<pixels&&step<1e12f)step*=2;return step;}
    // Round up: a tablet-sized viewport must not magnify a lower-resolution raster.
    public static int level(float scale,float visibleWidth,float visibleHeight){float target=Math.max(1e-12f,Math.min(scale,3600f/Math.max(1,Math.max(visibleWidth,visibleHeight))));return (int)Math.ceil(Math.log(target)/Math.log(2)*2-1e-7);}
    public static float levelScale(int level){return (float)Math.pow(2,level/2f);}
    public static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    public static float[] zoom(float tx,float ty,float fit,float current,float next,float fromX,float fromY,float toX,float toY){float x=(fromX-tx)/(fit*current),y=(fromY-ty)/(fit*current);return new float[]{toX-x*fit*next,toY-y*fit*next};}
}
