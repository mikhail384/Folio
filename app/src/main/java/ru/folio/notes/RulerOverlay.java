package ru.folio.notes;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class RulerOverlay {
    static void place(NotesView v){if(!v.ruler.placed&&v.getWidth()>0){v.ruler.x=v.getWidth()/v.dp(1)/2;v.ruler.y=v.getHeight()/v.dp(1)*.55f;v.ruler.placed=true;}}
    static void keepVisible(NotesView v){if(!v.ruler.placed)return;v.ruler.x=Ink.clamp(v.ruler.x,12,Math.max(12,v.getWidth()/v.dp(1)-12));v.ruler.y=Ink.clamp(v.ruler.y,12,Math.max(12,v.getHeight()/v.dp(1)-45));}
    static float captureDistance(NotesView v){return GuideTools.centimeter(v.dp(1),v.getResources().getDisplayMetrics().xdpi,v.getResources().getDisplayMetrics().ydpi,v.ruler.degrees+90)*.2f;}
    static float edge(NotesView v,float x,float y){place(v);return v.ruler.edge(x/v.dp(1),y/v.dp(1),captureDistance(v));}
    static float followEdge(NotesView v,Ink.Pt point,float current){return v.ruler.followEdge((point.x*v.scale()+v.tx)/v.dp(1),(point.y*v.scale()+v.ty)/v.dp(1),captureDistance(v),current);}
    static boolean nearEdge(NotesView v,float x,float y){return Float.isFinite(edge(v,x,y));}
    static Ink.Pt project(NotesView v,Ink.Pt point){float edge=edge(v,point.x*v.scale()+v.tx,point.y*v.scale()+v.ty);return Float.isFinite(edge)?project(v,point,edge):point;}
    static Ink.Pt project(NotesView v,Ink.Pt point,float edge){return GuideTools.rulerPoint(v.ruler,point,v.dp(1),v.scale(),v.tx,v.ty,edge);}
    static void draw(Canvas c,NotesView v){
        place(v);RulerGeometry r=v.ruler;float d=v.dp(1),w=v.getWidth()/d,h=v.getHeight()/d,left=Float.MAX_VALUE,right=-Float.MAX_VALUE;
        float cm=GuideTools.centimeter(d,v.getResources().getDisplayMetrics().xdpi,v.getResources().getDisplayMetrics().ydpi,r.degrees);
        for(float[] corner:new float[][]{{0,0},{w,0},{w,h},{0,h}}){float u=r.localX(corner[0],corner[1]);left=Math.min(left,u);right=Math.max(right,u);}left-=cm;right+=cm;
        Paint p=v.framePaint;p.reset();p.setAntiAlias(true);c.save();c.scale(d,d);c.translate(r.x,r.y);c.rotate(r.degrees);
        p.setColor(0xe3e8eef7);c.drawRect(left,0,right,RulerGeometry.THICKNESS,p);p.setColor(0xff91a0b8);p.setStrokeWidth(.8f);c.drawLine(left,0,right,0,p);c.drawLine(left,RulerGeometry.THICKNESS,right,RulerGeometry.THICKNESS,p);
        p.setColor(0xff34425b);p.setTextSize(9);p.setTextAlign(Paint.Align.CENTER);float step=cm/10;
        int start=(int)Math.floor(left/step),end=(int)Math.ceil(right/step);
        for(int mm=start;mm<=end;mm++){float x=mm*step,len=mm%10==0?13:mm%5==0?9:5;c.drawLine(x,0,x,len,p);if(mm%10==0)c.drawText(mm==0?"0 см":""+(mm/10),x,25,p);}
        p.setColor(0xffd4deee);c.drawRoundRect(-39,28,39,41,5,5,p);p.setColor(Float.isFinite(r.snapTarget)?0xff6979de:0xff4b5c78);p.setTextSize(10);c.drawText(String.format(Locale.ROOT,"%.1f°",r.degrees),0,38,p);
        p.setStrokeWidth(1.2f);for(int i=-1;i<=1;i++){c.drawLine(-52+i*3,31,-52+i*3,37,p);c.drawLine(52+i*3,31,52+i*3,37,p);}c.restore();
    }
    static boolean touch(NotesView v,MotionEvent e){
        RulerManipulation g=v.rulerManipulation;
        if(!v.guideMode.equals("ruler")){g.end();return false;}
        int action=e.getActionMasked(),ai=e.getActionIndex();float d=v.dp(1);RulerGeometry r=v.ruler;
        if(action==MotionEvent.ACTION_DOWN){
            g.end();
            if(e.getToolType(0)!=MotionEvent.TOOL_TYPE_FINGER||e.getSize(0)>.4f||v.penId!=-1)return false;
            place(v);float x=e.getX()/d,y=e.getY()/d,z=r.localY(x,y);if(z< -5||z>RulerGeometry.THICKNESS+5)return false;
            v.finishGesture(false);g.begin(r,e.getPointerId(0),x,y);v.ignoreTouch=true;if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(true);return true;
        }
        if(!g.active)return false;
        if(action==MotionEvent.ACTION_POINTER_DOWN&&!g.paired&&e.getToolType(ai)==MotionEvent.TOOL_TYPE_FINGER&&e.getSize(ai)<=.4f){int first=e.findPointerIndex(g.first);if(first>=0)g.pair(r,e.getPointerId(ai),e.getX(first)/d,e.getY(first)/d,e.getX(ai)/d,e.getY(ai)/d);}
        if(action==MotionEvent.ACTION_MOVE){int first=e.findPointerIndex(g.first);if(first>=0){float old=r.snapTarget;if(g.paired){int second=e.findPointerIndex(g.second);if(second>=0)g.movePair(r,e.getX(first)/d,e.getY(first)/d,e.getX(second)/d,e.getY(second)/d);}else g.moveOne(r,e.getX(first)/d,e.getY(first)/d);keepVisible(v);if(Float.isFinite(r.snapTarget)&&(!Float.isFinite(old)||old!=r.snapTarget))v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}v.invalidate();}
        if(action==MotionEvent.ACTION_POINTER_UP&&(e.getPointerId(ai)==g.first||e.getPointerId(ai)==g.second)){
            int remain=-1;for(int i=0;i<e.getPointerCount();i++)if(i!=ai&&e.getToolType(i)==MotionEvent.TOOL_TYPE_FINGER&&e.getSize(i)<=.4f){remain=i;break;}
            if(remain>=0)g.one(r,e.getPointerId(remain),e.getX(remain)/d,e.getY(remain)/d);else g.end();
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(action==MotionEvent.ACTION_CANCEL)g.cancel(r);else g.end();keepVisible(v);v.ignoreTouch=true;if(v.getContext() instanceof MainActivity)((MainActivity)v.getContext()).savePenPrefs();v.invalidate();}
        return true;
    }
    static void options(MainActivity a){NotesView v=a.canvas;LinearLayout box=a.column();
        box.addView(a.text("Бесконечная кромка · сантиметровые деления\nОдин палец — перемещение, два — поворот.\nХолст движется под линейкой. Перо и цвет выбираются во вкладке «Перо».",12,Studio.MUTED,false));CompactInspector.gap(a,box,12);
        EditText angle=Studio.input(a,String.format(Locale.ROOT,"%.1f",v.ruler.degrees),"Угол, °");angle.setInputType(12290);angle.setContentDescription("Угол линейки в градусах");box.addView(angle,new LinearLayout.LayoutParams(-1,a.dp(40)));CompactInspector.gap(a,box,10);
        box.addView(a.text("Привязки 30°, 45°, 60°, 90° в каждой четверти. Перо притягивается к обеим кромкам с расстояния около 2 мм, в том числе в середине штриха.",11,Studio.MUTED,false));CompactInspector.gap(a,box,10);
        box.addView(Studio.button(a,"Вернуть в центр экрана",false,()->{v.ruler.placed=false;place(v);a.savePenPrefs();v.invalidate();}));
        new Studio.Builder(a).anchor(GuideStudio.anchor(a)).setTitle("Линейка").setView(box).setNegativeButton("Убрать",(d,w)->v.setGuide("none")).setPositiveButton("Готово",(d,w)->{try{v.ruler.degrees=RulerGeometry.angle(Float.parseFloat(angle.getText().toString().replace(',','.')));v.ruler.snapTarget=RulerGeometry.nearest(v.ruler.degrees,.01f);a.savePenPrefs();}catch(Exception ignored){}v.invalidate();}).show();
    }
}
