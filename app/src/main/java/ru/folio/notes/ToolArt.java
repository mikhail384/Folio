package ru.folio.notes;

import android.animation.*;
import android.graphics.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;

/** Original Freenotes bitmap instruments, with vector fallback for unavailable textures. */
final class ToolArt {
    static boolean nib(String tool){return ToolTextures.nib(tool);}
    static void draw(Canvas c,String tool,float x,float y,float scale,int ink){draw(c,tool,x,y,scale,ink,"area");}
    static void draw(Canvas c,String tool,float x,float y,float scale,int ink,String mode){if(ToolTextures.draw(c,tool,x,y,scale,ink,mode))return;c.save();c.translate(x,y);c.scale(scale,scale);Paint p=new Paint(3);
        if(!nib(tool)||tool.equals("lasso")){Icons.draw(c,tool,0,24,27,Studio.TEXT);c.restore();return;}
        if(tool.equals("laser")){
            p.setShader(new LinearGradient(-7,0,7,0,new int[]{0xff191d28,0xff566072,0xff8c96a6,0xff333b49},null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(-7,16,7,83),5,5,p);p.setShader(null);p.setColor(0xffc8cdd7);c.drawRoundRect(new RectF(-5.5f,7,5.5f,21),3,3,p);p.setColor(0xff1b202a);c.drawRoundRect(new RectF(-5,5,5,14),3,3,p);
            int light=ink&0xffffff;p.setShader(new RadialGradient(0,5,11,new int[]{0xaa000000|light,0x28000000|light,light},new float[]{0,.4f,1},Shader.TileMode.CLAMP));c.drawCircle(0,5,11,p);p.setShader(null);p.setColor(ink);c.drawCircle(0,5,3.5f,p);p.setColor(0xfffff3f3);c.drawCircle(0,5,1.6f,p);p.setColor(0xff222833);c.drawRoundRect(new RectF(-3,32,3,46),3,3,p);p.setColor(ink);c.drawCircle(0,38,1.7f,p);p.setColor(0xff292f3c);for(int grip=57;grip<=74;grip+=4)c.drawRect(-7,grip,7,grip+1,p);c.restore();return;
        }
        boolean pencil=tool.equals("pencil"),eraser=tool.equals("eraser"),marker=tool.equals("marker"),tape=tool.equals("tape"),brush=tool.equals("brush");
        int shell=pencil?0xffa98061:eraser?0xffb4b8c3:marker?0xffe7dfc9:tape?0xffbbb2ce:0xff404652;
        p.setShader(new LinearGradient(-7,0,7,0,new int[]{Studio.blend(shell,Color.BLACK,.58f),shell,Studio.blend(shell,Color.WHITE,.4f),shell,Studio.blend(shell,Color.BLACK,.6f)},null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(-6.5f,19,6.5f,83),3,3,p);p.setShader(null);
        if(pencil){Path wood=new Path();wood.moveTo(0,0);wood.lineTo(-6.5f,20);wood.lineTo(6.5f,20);wood.close();p.setColor(0xffd7ba93);c.drawPath(wood,p);p.setColor(ink);Path tip=new Path();tip.moveTo(0,0);tip.lineTo(-2,6);tip.lineTo(2,6);tip.close();c.drawPath(tip,p);p.setColor(0x40ffffff);c.drawRect(-3,24,-2,76,p);}
        else if(eraser){p.setShader(new LinearGradient(-6,0,6,0,new int[]{0xffa7687e,0xffe4a6b5,0xfff6c6cb},null,Shader.TileMode.CLAMP));c.drawRoundRect(new RectF(-6.5f,0,6.5f,20),4,4,p);p.setShader(null);p.setColor(0xffe3e4e8);c.drawRect(-7,18,7,26,p);p.setColor(0xff747d8e);c.drawRect(-7,22,7,23,p);}
        else if(marker||tape){p.setColor(tape?0xffc7b4eb:ink);Path tip=new Path();tip.moveTo(-6,8);tip.lineTo(6,0);tip.lineTo(6,15);tip.lineTo(-6,15);tip.close();c.drawPath(tip,p);p.setColor(0xffe8e3d8);c.drawRoundRect(new RectF(-7,14,7,24),2,2,p);p.setColor(tape?0xffc7b4eb:ink);c.drawRect(-6,29,6,33,p);}
        else if(tool.equals("fountain")){p.setShader(new LinearGradient(-7,6,6,20,new int[]{0xffd6af73,0xffffe6ae,0xffb98d51},null,Shader.TileMode.CLAMP));Path nib=new Path();nib.moveTo(0,0);nib.cubicTo(-1,5,-5,8,-6,15);nib.lineTo(-3,22);nib.lineTo(3,22);nib.lineTo(6,15);nib.cubicTo(5,8,1,5,0,0);c.drawPath(nib,p);p.setShader(null);p.setColor(0xff5f4b34);p.setStrokeWidth(.65f);c.drawLine(0,1,0,14,p);c.drawCircle(0,14,1.2f,p);p.setColor(0xffd1b27b);c.drawRect(-6.5f,25,6.5f,27,p);}
        else if(brush){Path tip=new Path();tip.moveTo(0,0);tip.cubicTo(-10,12,-4,21,0,21);tip.cubicTo(7,21,5,10,0,0);p.setColor(Studio.blend(ink,Color.BLACK,.25f));c.drawPath(tip,p);p.setColor(0xffc4c7d0);c.drawRoundRect(new RectF(-5,20,5,30),1,1,p);p.setColor(0xff737b8a);p.setStrokeWidth(.8f);for(int i=22;i<29;i+=2)c.drawLine(-5,i,5,i,p);}
        else {p.setShader(new LinearGradient(-4,2,4,19,new int[]{0xff69717e,0xffe3e6eb,0xff5d6572},null,Shader.TileMode.CLAMP));Path tip=new Path();tip.moveTo(0,0);tip.lineTo(-4,17);tip.lineTo(4,17);tip.close();c.drawPath(tip,p);p.setShader(null);p.setColor(tool.equals("laser")?0xfffc7387:ink);c.drawCircle(0,1,1.4f,p);p.setColor(0xff1d222c);c.drawRoundRect(new RectF(-5,15,5,24),2,2,p);}
        if(!pencil&&!eraser){p.setShader(tool.equals("rainbow")?new LinearGradient(-6,0,6,0,new int[]{0xffee9ba6,0xffe6c97c,0xffa6d8bb,0xffa6b6ed},null,Shader.TileMode.CLAMP):null);if(!tool.equals("rainbow"))p.setColor(tool.equals("laser")?0xffef7486:ink);c.drawRoundRect(new RectF(-6,40,6,43),1,1,p);p.setShader(null);p.setColor(0x36ffffff);c.drawRoundRect(new RectF(2,48,3,70),1,1,p);}
        p.setColor(0x58ffffff);p.setStrokeWidth(.5f);c.drawLine(-4.5f,33,-4.5f,76,p);c.restore();
    }
    static final class Tile extends View {
        final MainActivity a;final String tool;final Paint p=new Paint(3);float selected;final SmoothSpring lift=new SmoothSpring(0,23);final SpringDriver motion;
        Tile(MainActivity a,String tool){super(a);this.a=a;this.tool=tool;motion=new SpringDriver(this,()->{selected=Ink.clamp(lift.value,0,1);invalidate();},lift);setContentDescription(Ink.label(tool));setTooltipText(Ink.label(tool));setFocusable(true);setClickable(true);ToolbarMotion.press(a,this);}
        void select(boolean on){float target=on?1:0;if(lift.target==target&&Math.abs(lift.value-target)<.0005f)return;motion.to(a.prefs.getBoolean("toolbar_motion",true),target);}
        int inkColor(){if(a.canvas!=null)return a.canvas.colorFor(tool);int fallback=tool.equals("laser")?a.prefs.getInt("laser_color",LaserEffect.DEFAULT_COLOR):ColorDefaults.first(tool);return a.prefs.getInt("ink_"+tool,fallback);}
        protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(!(getParent() instanceof ToolStrip)){p.setColor(Color.argb((int)(selected*255),61,60,83));c.drawRoundRect(new RectF(a.dp(3),a.dp(3),w-a.dp(3),h-a.dp(3)),a.dp(15),a.dp(15),p);}if(nib(tool)){c.save();c.rotate((1-selected)*-3,w/2,h);ToolArt.draw(c,tool,w/2,a.dp(18-11*selected),a.getResources().getDisplayMetrics().density*.75f,inkColor(),a.canvas==null?a.prefs.getString("eraserMode","area"):a.canvas.eraserMode);c.restore();}else if(!ToolTextures.flat(c,tool,w/2,h/2-a.dp(2),a.dp(28),Studio.blend(Studio.MUTED,Studio.ACCENT,selected)))Icons.draw(c,tool,w/2,h/2-a.dp(2),a.dp(24),Studio.blend(Studio.MUTED,Studio.ACCENT,selected));p.setColor(Studio.ACCENT);if(selected>.01)c.drawRoundRect(new RectF(w/2-a.dp(5)*selected,h-a.dp(6),w/2+a.dp(5)*selected,h-a.dp(4)),a.dp(1),a.dp(1),p);}
        public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)motion.finish();}
        protected void onDetachedFromWindow(){motion.stop();super.onDetachedFromWindow();}
    }
}
