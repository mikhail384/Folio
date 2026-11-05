package ru.folio.notes;

public final class CursiveJoinTests {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Ink.Stroke stroke(float width,int color,float[][] points){
        Ink.Stroke s=new Ink.Stroke();s.tool="fountain";s.width=width;s.color=color;
        for(float[] p:points)s.points.add(new Ink.Pt(p[0],p[1],p[2],0,0));
        return s;
    }
    static float visible(Ink.Stroke s,Ink.Pt p){return InkGeometry.diameter(s,p);}

    public static void main(String[] args){
        Ink.Stroke first=stroke(2,0xff28344b,new float[][]{{0,0,.20f},{5,0,.45f},{9,0,.60f}});
        Ink.Stroke second=stroke(2.8f,0xff28344b,new float[][]{{13,0,.80f},{17,1,.75f},{22,2,.50f}});
        float firstStart=visible(first,first.points.get(0)),firstEnd=visible(first,first.points.get(2));
        float secondEnd=visible(second,second.points.get(2));
        check(CursiveJoin.merge(first,second,7),"nearby, aligned glyphs join");
        check(first.points.size()>6,"join adds a smooth connector");
        check(Math.abs(visible(first,first.points.get(0))-firstStart)<.015f,"first glyph pressure appearance is preserved");
        check(Math.abs(visible(first,first.points.get(2))-firstEnd)<.015f,"previous glyph terminal width is preserved");
        check(Math.abs(visible(first,first.points.get(first.points.size()-1))-secondEnd)<.015f,"next glyph width is preserved after merge");

        Ink.Stroke farA=stroke(2,1,new float[][]{{0,0,.5f},{4,0,.5f}}),farB=stroke(2,1,new float[][]{{20,0,.5f},{24,0,.5f}});
        int farCount=farA.points.size();
        check(!CursiveJoin.merge(farA,farB,6),"large gap remains a pen lift");
        check(farA.points.size()==farCount,"rejected large gap does not mutate source");

        Ink.Stroke turnA=stroke(2,1,new float[][]{{0,0,.5f},{5,0,.5f}}),turnB=stroke(2,1,new float[][]{{9,0,.5f},{5,0,.5f}});
        check(!CursiveJoin.merge(turnA,turnB,6),"reversing terminal tangent is not joined into a loop");

        Ink.Stroke colorA=stroke(2,1,new float[][]{{0,0,.5f},{5,0,.5f}}),colorB=stroke(2,2,new float[][]{{8,0,.5f},{12,0,.5f}});
        check(!CursiveJoin.merge(colorA,colorB,6),"different colors are never merged");

        Ink.Stroke widthA=stroke(1,1,new float[][]{{0,0,.021f},{5,0,.021f}}),widthB=stroke(10,1,new float[][]{{8,0,1.5f},{12,0,1.5f}});
        check(!CursiveJoin.merge(widthA,widthB,6),"incompatible nib widths remain separate instead of changing thickness");

        Ink.Stroke touchingA=stroke(2,1,new float[][]{{0,0,.5f},{5,0,.5f}}),touchingB=stroke(2,1,new float[][]{{5,0,.5f},{9,0,.5f}});
        check(CursiveJoin.merge(touchingA,touchingB,6),"touching strokes join without a duplicate pen lift");
        for(int i=1;i<touchingA.points.size();i++)check(Ink.dist(touchingA.points.get(i-1).x,touchingA.points.get(i-1).y,touchingA.points.get(i).x,touchingA.points.get(i).y)>.001f,"joined path does not contain repeated points");
        System.out.println("PASS "+checks+" cursive join checks");
    }
}
