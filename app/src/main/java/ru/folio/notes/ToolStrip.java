package ru.folio.notes;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A single selection surface travels behind the instruments instead of flashing per-tile backgrounds. */
final class ToolStrip extends LinearLayout {
    final MainActivity a;final Paint paint=new Paint(3);
    final SmoothSpring left=new SmoothSpring(0,24),right=new SmoothSpring(0,24),opacity=new SmoothSpring(0,24);
    final SpringDriver motion;String selected="";boolean placed;
    ToolStrip(MainActivity a){super(a);this.a=a;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setWillNotDraw(false);motion=new SpringDriver(this,this::invalidate,left,right,opacity);}
    void select(String name){boolean changed=!selected.equals(name);selected=name;locate();if(changed)post(this::reveal);}
    View selectedView(){for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child instanceof ToolArt.Tile&&((ToolArt.Tile)child).tool.equals(selected))return child;}return null;}
    void locate(){View child=selectedView();if(child==null){motion.to(a.prefs.getBoolean("toolbar_motion",true),left.target,right.target,0);return;}if(child.getWidth()==0)return;float l=child.getLeft()+a.dp(3),r=child.getRight()-a.dp(3);if(!placed){left.snap(l);right.snap(r);opacity.snap(1);placed=true;invalidate();}else motion.to(a.prefs.getBoolean("toolbar_motion",true),l,r,1);}
    void reveal(){View child=selectedView();if(child==null||!(getParent() instanceof HorizontalScrollView))return;HorizontalScrollView scroll=(HorizontalScrollView)getParent();int margin=a.dp(12),x=scroll.getScrollX(),target=x;if(child.getLeft()<x+margin)target=Math.max(0,child.getLeft()-margin);else if(child.getRight()>x+scroll.getWidth()-margin)target=child.getRight()-scroll.getWidth()+margin;if(target!=x){if(a.prefs.getBoolean("toolbar_motion",true))scroll.smoothScrollTo(target,0);else scroll.scrollTo(target,0);}}
    protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);locate();}
    protected void onDraw(Canvas c){super.onDraw(c);float alpha=Ink.clamp(opacity.value,0,1);if(alpha<=.001f)return;paint.setColor(Color.argb((int)(alpha*255),61,60,83));c.drawRoundRect(new RectF(left.value,a.dp(3),Math.max(left.value,right.value),getHeight()-a.dp(3)),a.dp(15),a.dp(15),paint);}
    public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus)motion.finish();}
}
