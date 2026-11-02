package ru.folio.notes;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Live tool inspectors with original previews, tactile controls and persistent settings. */
final class ToolStudio {
    static void options(MainActivity a,String tool){if(a.canvas==null)return;if(GuideTools.auxiliary(tool)){GuideStudio.select(a,GuideTools.mode(tool),true);return;}if(!a.canvas.tool.equals(tool))a.canvas.setTool(tool);
        if(tool.equals("laser")){LaserInspector.show(a);return;}if(tool.equals("eraser")){eraser(a);return;}if(tool.equals("lasso")){selection(a);return;}
        if(tool.equals("protractor")){new Studio.Builder(a).anchor(CompactInspector.anchor(a,tool)).setTitle("Транспортир").setMessage("Касание задаёт центр. Ведите перо вокруг него, чтобы повернуть шкалу.").show();return;}
        if(tool.equals("hand")){new Studio.Builder(a).anchor(CompactInspector.anchor(a,tool)).setTitle("Навигация").setMessage("Перемещайте лист пальцами, меняйте масштаб щипком. Короткое касание двумя пальцами отменяет действие.").show();return;}
        CompactInspector.writing(a,tool);
    }
    
    
    
    static final class ColorDot extends View {final MainActivity a;final int color;final boolean chosen;final Paint p=new Paint(3);ColorDot(MainActivity a,int color,boolean chosen){super(a);this.a=a;this.color=color;this.chosen=chosen;setFocusable(true);}protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f;p.setColor(color);c.drawCircle(x,y,a.dp(13),p);if(chosen){p.setColor(Studio.ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));c.drawCircle(x,y,a.dp(17),p);p.setStyle(Paint.Style.FILL);p.setColor(0xfffaf8f1);c.drawCircle(x,y,a.dp(3),p);}}}
    static void choice(MainActivity a,LinearLayout parent,String title,String detail,boolean selected,Runnable action){LinearLayout row=Studio.row(a,selected?"star":"ellipse",title,detail,action);if(selected)row.setBackground(Studio.ripple(a,0xff36354a,18));Studio.addCard(a,parent,row);}
    static void eraser(MainActivity a){if(a.canvas!=null)CompactInspector.eraser(a);}
    static void compass(MainActivity a){if(a.canvas!=null)CompactInspector.compass(a);}
    static void selection(MainActivity a){SelectionStudio.show(a); }
    static void panel(MainActivity a){ArrayList<String> order=ToolSettings.ordered(a);HashSet<String> enabled=new HashSet<>(ToolSettings.visible(a));LinearLayout box=a.column();TextView info=a.text("Ваши инструменты. В том порядке, который удобен вам.",14,Studio.MUTED,false);a.pad(info,0,12);box.addView(info);LinearLayout cards=a.column();box.addView(cards);Runnable[] fill=new Runnable[1];fill[0]=()->{cards.removeAllViews();for(int i=0;i<order.size();i++){String name=order.get(i);final int index=i;LinearLayout row=a.row();a.pad(row,6,4);row.setBackground(Studio.ripple(a,Studio.CARD,18));ToolArt.Tile art=new ToolArt.Tile(a,name);row.addView(art,new LinearLayout.LayoutParams(a.dp(48),a.dp(61)));TextView label=a.text(Ink.label(name),13,Studio.TEXT,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));Studio.Toggle on=new Studio.Toggle(a,enabled.contains(name),v->{if(v)enabled.add(name);else enabled.remove(name);});on.setContentDescription("В панели: "+Ink.label(name));row.addView(on,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));Icons up=a.icon("back","Выше: "+Ink.label(name),()->{if(index>0){Collections.swap(order,index,index-1);fill[0].run();Studio.enter(cards.getChildAt(index-1),0);}});up.setRotation(90);up.tint=Studio.MUTED;row.addView(up,new LinearLayout.LayoutParams(a.dp(34),a.dp(42)));Icons down=a.icon("next","Ниже: "+Ink.label(name),()->{if(index<order.size()-1){Collections.swap(order,index,index+1);fill[0].run();Studio.enter(cards.getChildAt(index+1),0);}});down.setRotation(90);down.tint=Studio.MUTED;row.addView(down,new LinearLayout.LayoutParams(a.dp(34),a.dp(42)));Studio.addCard(a,cards,row);}};fill[0].run();new Studio.Builder(a).setTitle("Ваш пенал").setView(box).setNegativeButton("Отмена",null).setNeutralButton("Сбросить",(d,w)->{android.content.SharedPreferences.Editor e=a.prefs.edit().remove("tool_order");for(String name:Ink.TOOLS)e.remove("tool_visible_"+name);e.apply();a.refreshTools();}).setPositiveButton("Сохранить",(d,w)->{if(enabled.isEmpty())enabled.add("pen");android.content.SharedPreferences.Editor e=a.prefs.edit().putString("tool_order",String.join(",",order));for(String name:Ink.TOOLS)e.putBoolean("tool_visible_"+name,enabled.contains(name));e.apply();a.refreshTools();}).show();}
    static void gallery(MainActivity a){
        if(a.canvas==null){panel(a);return;}final Studio.Sheet[] gallery=new Studio.Sheet[1];LinearLayout box=a.column();LinearLayout row=null;int index=0;
        for(String tool:Ink.TOOLS){if(GuideTools.auxiliary(tool))continue;if(index++%4==0){row=a.row();box.addView(row);}
            LinearLayout cell=a.column();cell.setGravity(Gravity.CENTER);cell.setBackground(Studio.ripple(a,Studio.CARD,17));ToolArt.Tile art=new ToolArt.Tile(a,tool);art.setClickable(false);art.setFocusable(false);cell.addView(art,new LinearLayout.LayoutParams(a.dp(42),a.dp(65)));TextView label=a.text(Ink.label(tool),10,Studio.TEXT,false);label.setGravity(Gravity.CENTER);label.setMaxLines(2);cell.addView(label,new LinearLayout.LayoutParams(-1,a.dp(30)));cell.setOnClickListener(v->{gallery[0].dismissThen(()->options(a,tool));});Studio.press(cell);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(106),1);lp.setMargins(a.dp(3),a.dp(3),a.dp(3),a.dp(3));row.addView(cell,lp);
        }
        CompactInspector.gap(a,box,12);box.addView(Studio.button(a,"Линейка и циркуль  ›",false,()->{gallery[0].dismissThen(()->{a.toolsTab="guides";a.refreshTools();});}));
        gallery[0]=new Studio.Builder(a).setTitle("Мастерская инструментов").setView(box).setPositiveButton("Готово",null).show();
    }
    static void paper(MainActivity a){PaperStudio.show(a);}
    static final class PaperTile extends View {final MainActivity a;final String pattern;final Ink.Page source;PaperTile(MainActivity a,String pattern){this(a,pattern,null);}PaperTile(MainActivity a,String pattern,Ink.Page source){super(a);this.a=a;this.pattern=pattern;this.source=source;}protected void onDraw(Canvas c){Paint p=new Paint(3);Ink.Page current=source!=null?source:a.canvas.page;Ink.Page page=new Ink.Page();page.paper=current.paper;page.lines=current.lines;page.spacing=current.spacing;page.width=150;page.height=190;page.strokes.clear();page.pattern=pattern;p.setColor(current.pattern.equals(pattern)?Studio.ACCENT:Studio.LINE);c.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),a.dp(12),a.dp(12),p);c.save();c.translate(a.dp(3),a.dp(3));c.scale((getWidth()-a.dp(6))/150f,(getHeight()-a.dp(6))/190f);c.clipRect(0,0,150,190);InkRenderer.paper(c,page,null);c.restore();}}

}
