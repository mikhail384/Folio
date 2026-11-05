package ru.folio.notes;

public final class PaletteTests {
    static int assertions;
    static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);assertions++;}
    public static void main(String[] args){
        RadialPalette state=new RadialPalette();check(state.hovered==-1,"starts without hover");
        for(int i=0;i<8;i++)for(float angle:new float[]{-21,-10,0,10,21}){double a=Math.toRadians(i*45-90+angle);for(float radius:new float[]{16,32,49,51,78,114}){int expected=i+(radius<=50?8:0);check(RadialPalette.hit((float)Math.cos(a)*radius,(float)Math.sin(a)*radius)==expected,"angular hit and band match displayed items");}}
        for(int i=0;i<8;i++){float x=RadialPalette.x(i,RadialPalette.TOOL_RADIUS),y=RadialPalette.y(i,RadialPalette.TOOL_RADIUS);check(state.hover(x,y)&&state.hovered==i,"tool hover");check(!state.hover(x,y),"same target does not restart motion");float[] targets=state.targets();for(int j=0;j<20;j++)check(targets[j]==(j==i?1:0),"only hovered tool lifts");x=RadialPalette.x(i,RadialPalette.COLOR_RADIUS);y=RadialPalette.y(i,RadialPalette.COLOR_RADIUS);check(state.hover(x,y)&&state.hovered==i+8,"color hover");targets=state.targets();for(int j=0;j<20;j++)check(targets[j]==(j==i+8?1:0),"only hovered color grows ring");}
        for(float[] p:new float[][]{{0,0},{14,0},{116,0},{1000,200},{Float.NaN,0},{Float.POSITIVE_INFINITY,0}})check(RadialPalette.hit(p[0],p[1])==-1,"dead zone, outside and invalid input rejected");
        state.clear();check(state.hovered==-1,"exit clears hover");for(float v:state.targets())check(v==0,"exit removes all feedback targets");
        for(int count:new int[]{8,10,12})for(int i=0;i<count;i++){float x=RadialPalette.x(i,RadialPalette.TOOL_RADIUS,count),y=RadialPalette.y(i,RadialPalette.TOOL_RADIUS,count);check(RadialPalette.toolHit(x,y,count)==i,"adaptive ring hit matches displayed sector: "+count+"/"+i);}
        SmoothSpring[] springs=RadialPalette.highlights();check(springs.length==20,"twelve tool and eight color animation values");state.hover(0,-78);float[] target=state.targets();for(int i=0;i<20;i++)springs[i].target(target[i]);for(int f=0;f<8;f++)for(SmoothSpring s:springs)s.step(1f/120);float position=springs[0].value,velocity=springs[0].velocity;check(position>0&&position<1,"feedback has intermediate frames");state.hover(32,0);target=state.targets();for(int i=0;i<20;i++)springs[i].target(target[i]);check(springs[0].value==position&&springs[0].velocity==velocity,"hover retarget preserves ongoing motion");for(int f=0;f<200;f++)for(SmoothSpring s:springs)s.step(1f/120);check(springs[0].value==0&&springs[10].value==1,"tool lowers and hovered color settles");state.clear();target=state.targets();for(int i=0;i<20;i++)springs[i].target(target[i]);for(int f=0;f<200;f++)for(SmoothSpring s:springs)s.step(1f/120);for(SmoothSpring s:springs)check(s.value==0&&s.velocity==0,"exit fully settles");
        System.out.println("PASS palette shared hover/touch geometry, adaptive 8/10/12 tool sectors, colors, dead zones, rings and spring continuity");System.out.println("PASS: "+assertions+" palette assertions");
    }
}
