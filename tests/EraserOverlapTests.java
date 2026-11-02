package ru.folio.notes;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.nio.file.*;

/** Production geometry/damage with an independent software compositor; not Android FPS. */
public final class EraserOverlapTests {
    static int checks;static final Random random=new Random(87219);
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static Ink.Stroke marker(int count,int seed){Ink.Stroke s=new Ink.Stroke();s.tool="marker";s.width=22+seed%30;s.color=0xff000000|((seed*119331+0x988841)&0xffffff);for(int i=0;i<count;i++)s.points.add(new Ink.Pt(i*4,100+(float)Math.sin(i*.045+seed*.02)*18,1,.2f,i*.007f+seed*.01f));return s;}
    static void parity(){
        for(int n=0;n<20;n++){Ink.Stroke s=marker(n==0?1:100+n*11,n);StrokeChunks chunks=new StrokeChunks(s);
            for(int j=0;j<100;j++){float x=random.nextFloat()*s.points.size()*4,y=45+random.nextFloat()*110,z=x+(random.nextFloat()-.5f)*80,w=y+(random.nextFloat()-.5f)*80,r=random.nextFloat()*12;
                check(chunks.sweep(s,x,y,z,w,r)==InkGeometry.markerSweepHit(s,x,y,z,w,r),"cached rectangular marker matches original swept intersection");}
        }
        Ink.Stroke s=marker(180,1);SpatialIndex index=new SpatialIndex();index.add(s,0);StrokeChunks geometry=index.entries.get(s).geometry;
        geometry.sweep(s,500,100,505,105,12);int built=StrokeChunks.MARKERS.get().builds;
        Ink.Stroke next=s.copy();next.cuts.add(new Ink.Cut(500,100,3));index.replaceMask(s,next);
        check(index.entries.get(next).geometry==geometry,"mask updates preserve indexed geometry");geometry.sweep(next,500,100,505,105,12);
        check(StrokeChunks.MARKERS.get().builds==built,"eraser edits do not regenerate marker polygons");
        check(StrokeChunks.MARKERS.get().floats<=StrokeChunks.MarkerMeshes.MAX_FLOATS,"marker hit cache stays within memory budget");
    }
    static void cacheBudget(){
        StrokeChunks.MARKERS.get().clear();
        for(int n=0;n<32;n++){Ink.Stroke stroke=marker(96,n);for(int i=0;i<stroke.points.size();i++){Ink.Pt p=stroke.points.get(i);stroke.points.set(i,new Ink.Pt(p.x,p.y,1,0,i*2.9f));}StrokeChunks geometry=new StrokeChunks(stroke);StrokeChunks.MARKERS.get().get(stroke,geometry.chunks.get(0));check(StrokeChunks.MARKERS.get().floats<=StrokeChunks.MarkerMeshes.MAX_FLOATS,"worst-case rotating markers cannot exceed hit-cache budget");}
        check(StrokeChunks.MARKERS.get().meshes.size()<32,"large meshes evict older geometry instead of growing without bound");
        Ink.Stroke far=new Ink.Stroke();far.tool="marker";far.width=64;far.points.add(new Ink.Pt(10000000,-10000000,1,0,0));StrokeChunks geometry=new StrokeChunks(far);
        check(geometry.sweep(far,10000000,-10000000,10000000,-10000000,1),"large world coordinates keep a nondegenerate marker footprint");
        check(!geometry.sweep(far,10000100,-10000000,10000100,-10000000,1),"large coordinates do not create false eraser hits");
    }
    static Path2D outline(Ink.Stroke stroke){Path2D path=new Path2D.Float(Path2D.WIND_NON_ZERO);InkGeometry.sweep(stroke,new InkGeometry.Sink(){public void ellipse(float x,float y,float rx,float ry,float a){throw new AssertionError("marker cannot become round");}public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){path.moveTo(ax,ay);path.lineTo(bx,by);path.lineTo(cx,cy);path.lineTo(dx,dy);path.closePath();}});return path;}
    static Area hole(Ink.Cut cut){if(cut.x==cut.z&&cut.y==cut.w)return new Area(new Ellipse2D.Float(cut.x-cut.r,cut.y-cut.r,cut.r*2,cut.r*2));return new Area(new BasicStroke(cut.r*2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(new Line2D.Float(cut.x,cut.y,cut.z,cut.w)));}
    static Area damage(EraserDamage d){if(d.rectangular)return new Area(new Rectangle2D.Float(d.bounds[0]-2,d.bounds[1]-2,d.bounds[2]-d.bounds[0]+4,d.bounds[3]-d.bounds[1]+4));Area area=new Area();for(Ink.Cut cut:d.cuts)area.add(hole(new Ink.Cut(cut.x,cut.y,cut.r+2,cut.z,cut.w)));return area;}
    static BufferedImage image(){return new BufferedImage(240,180,BufferedImage.TYPE_INT_ARGB);}
    static void paint(BufferedImage bitmap,Ink.Page page,Shape clip){Graphics2D g=bitmap.createGraphics();if(clip!=null)g.clip(clip);g.setColor(new Color(page.paper,true));g.fillRect(0,0,240,180);
        for(Ink.Stroke s:page.strokes){if(!page.visible(s))continue;Graphics2D ink=(Graphics2D)g.create();Area visible=new Area(new Rectangle(0,0,240,180));for(Ink.Cut cut:s.cuts)visible.subtract(hole(cut));ink.clip(visible);ink.setComposite(AlphaComposite.SrcOver.derive(70/255f));ink.setColor(new Color(s.color,true));ink.fill(outline(s));ink.dispose();}g.dispose();}
    static void composition(boolean overflow){
        Ink.Page page=new Ink.Page();page.paper=0xfffaf5ed;page.infinite=false;
        Ink.Layer locked=new Ink.Layer();locked.id="locked";locked.locked=true;page.layers.add(locked);
        Ink.Layer hidden=new Ink.Layer();hidden.id="hidden";hidden.visible=false;page.layers.add(hidden);
        for(int j=0;j<18;j++){Ink.Stroke s=marker(55,j);if(j%7==0)s.layer="locked";if(j==17)s.layer="hidden";page.strokes.add(s);}page.order();
        BufferedImage sequential=image(),batched=image();paint(sequential,page,null);paint(batched,page,null);
        EraserDamage accumulated=new EraserDamage();int samples=overflow?90:16;
        for(int i=0;i<samples;i++){float x=35+i%15*9,y=75+i%7*7;Ink.Cut cut=new Ink.Cut(x,y,8,x+5,y+4);
            for(Ink.Stroke s:page.strokes)if(page.editable(s)&&new StrokeChunks(s).sweep(s,cut.x,cut.y,cut.z,cut.w,cut.r))EraserDamage.append(s.cuts,cut);
            paint(sequential,page,hole(cut));accumulated.add(cut);
        }
        check(accumulated.rectangular==overflow,"bounded frame damage falls back to exact recomposition of its rectangle");paint(batched,page,damage(accumulated));
        BufferedImage finalReference=image();paint(finalReference,page,null);
        for(int y=0;y<180;y++)for(int x=0;x<240;x++)check(batched.getRGB(x,y)==finalReference.getRGB(x,y),"batched overlapping markers match full composition including antialiased damage fringe");
        for(Ink.Stroke s:page.strokes)if(!page.editable(s))check(s.cuts.isEmpty(),"locked/hidden strokes never gain eraser masks");
        for(int i=0;i<samples;i++){float x=35+i%15*9,y=75+i%7*7;check(accumulated.bounds[0]<=x-8&&accumulated.bounds[1]<=y-8&&accumulated.bounds[2]>=x+5+8&&accumulated.bounds[3]>=y+4+8,"damage bounds contain every historical segment");}
        accumulated.clear();check(!accumulated.dirty&&!accumulated.rectangular&&accumulated.cuts.isEmpty(),"cancel/frame completion clears pending raster work");
    }
    static double median(ArrayList<Long> values){Collections.sort(values);return values.get(values.size()/2)/1e6;}
    // Frozen 1.13.3 marker branch: the old app already culled 96-point blocks.
    static boolean previousIndexed(StrokeChunks geometry,Ink.Stroke stroke,float x,float y,float z,float w,float radius){
        float l=Math.min(x,z)-radius,t=Math.min(y,w)-radius,r=Math.max(x,z)+radius,b=Math.max(y,w)+radius;
        if(!CanvasViewport.overlaps(geometry.bounds,l,t,r,b))return false;
        for(StrokeChunks.Chunk chunk:geometry.chunks)if(CanvasViewport.overlaps(chunk.bounds,l,t,r,b)&&InkGeometry.markerSweepHit(chunk.fragment(stroke),x,y,z,w,radius))return true;
        return false;
    }
    static String benchmark(){
        Ink.Stroke[] strokes=new Ink.Stroke[96];StrokeChunks[] indexed=new StrokeChunks[96];for(int i=0;i<96;i++){strokes[i]=marker(384,i);indexed[i]=new StrokeChunks(strokes[i]);}
        StrokeChunks.MARKERS.get().clear();long coldStart=System.nanoTime();
        for(int frame=0;frame<24;frame++)for(int i=0;i<96;i++)indexed[i].sweep(strokes[i],500+frame*8,100,505+frame*8,104,14);
        double coldMs=(System.nanoTime()-coldStart)/1e6;int builds=StrokeChunks.MARKERS.get().builds;ArrayList<Long> old=new ArrayList<>(),fast=new ArrayList<>();long reference=0,actual=0;
        for(int pass=0;pass<5;pass++){
            long start=System.nanoTime();for(int frame=0;frame<24;frame++)for(int i=0;i<96;i++)if(previousIndexed(indexed[i],strokes[i],500+frame*8,100,505+frame*8,104,14))reference++;old.add(System.nanoTime()-start);
            start=System.nanoTime();for(int frame=0;frame<24;frame++)for(int i=0;i<96;i++)if(indexed[i].sweep(strokes[i],500+frame*8,100,505+frame*8,104,14))actual++;fast.add(System.nanoTime()-start);
        }
        check(reference==actual,"identical before/after overlapping-marker workload produces identical hits");
        check(StrokeChunks.MARKERS.get().builds==builds,"warm dense erasure allocates no new marker meshes");
        double before=median(old),after=median(fast);
        return String.format(Locale.ROOT,"Desktop JVM only: 96 overlapping markers x 384 points, 24 eraser sweeps; cold cached/indexed %.3f ms; median previous indexed geometry %.3f ms, warm cached/indexed %.3f ms (%.2fx). Per-frame batching of 8 historical samples reduces raster replays from 8 to 1; not tablet latency or FPS.",coldMs,before,after,before/after);
    }
    public static void main(String[] args)throws Exception{parity();composition(false);composition(true);cacheBudget();String benchmark=benchmark();System.out.println("PASS "+checks+" eraser overlap assertions: exact marker hits, alpha/order, locked/hidden layers, complete swept damage, bounded memory, alias reuse and cancellation.");System.out.println(benchmark);if(args.length>0)Files.write(Paths.get(args[0]),benchmark.getBytes("UTF-8"));}
}
