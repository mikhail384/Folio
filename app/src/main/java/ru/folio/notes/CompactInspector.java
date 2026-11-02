package ru.folio.notes;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** A small live inspector anchored to the instrument that opened it. */
final class CompactInspector {
    static View anchor(MainActivity a,String tool){
        Rect visible=new Rect();
        if(a.tools!=null){
            for(int i=0;i<a.tools.getChildCount();i++){
                View child=a.tools.getChildAt(i);
                if(child instanceof ToolArt.Tile&&((ToolArt.Tile)child).tool.equals(tool)&&child.getGlobalVisibleRect(visible)&&visible.width()>a.dp(18))return child;
            }
            if(a.tools.getParent() instanceof View)return (View)a.tools.getParent();
        }
        return a.editorHeader;
    }
    static TextView chip(MainActivity a,String label,boolean selected,Runnable action){
        TextView t=a.text(label,12,selected?Studio.BG:Studio.TEXT,true);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setBackground(Studio.ripple(a,selected?Studio.ACCENT:Studio.CARD,11));t.setOnClickListener(v->action.run());Studio.press(t);return t;
    }
    static void add(MainActivity a,LinearLayout row,View child,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,a.dp(height),1);p.setMargins(a.dp(2),0,a.dp(2),0);row.addView(child,p);}
    static void gap(MainActivity a,LinearLayout box,int h){box.addView(new View(a),new LinearLayout.LayoutParams(1,a.dp(h)));}
    static Studio.Sheet writing(MainActivity a,String tool){
        LinearLayout box=a.column();String caption=tool.equals("pen")?"Круглая · Постоянная толщина":tool.equals("fountain")?"Круглая · Чувствительность к нажиму":tool.equals("brush")?"Нажим · Наклон · Вращение":"Ваш цвет и характер линии";
        box.addView(a.text(caption,11,Studio.MUTED,false));gap(a,box,9);
        Preview preview=new Preview(a,tool,false);box.addView(preview,new LinearLayout.LayoutParams(-1,a.dp(66)));gap(a,box,10);
        size(a,box,false,preview);gap(a,box,10);if(tool.equals("tape")){LinearLayout materials=a.column();Runnable[] fillMaterials={null};fillMaterials[0]=()->{materials.removeAllViews();for(int row=0;row<2;row++){LinearLayout choices=a.row();for(int col=0;col<3;col++){final int id=row*3+col;add(a,choices,chip(a,StrokeTextures.LABELS[id],a.canvas.tapeTexture==id,()->{a.canvas.tapeTexture=id;a.savePenPrefs();preview.invalidate();fillMaterials[0].run();}),30);}materials.addView(choices);gap(a,materials,4);}};fillMaterials[0].run();box.addView(materials);gap(a,box,8);}
        LinearLayout colors=a.row();Runnable[] fill=new Runnable[1];fill[0]=()->{colors.removeAllViews();for(int color:a.canvas.presets.forTool(tool)){
            View dot=new ToolStudio.ColorDot(a,color,a.canvas.colorFor(tool)==color);dot.setContentDescription(String.format(Locale.ROOT,"Цвет #%06X",color&0xffffff));dot.setOnClickListener(v->{a.canvas.colorFor(tool,color);preview.invalidate();a.toolChanged();fill[0].run();});add(a,colors,dot,34);Studio.press(dot);
        }TextView custom=chip(a,"+",false,()->a.colorDialog(a.canvas.colorFor(tool),color->{a.canvas.colorFor(tool,color);preview.invalidate();a.toolChanged();fill[0].run();}));custom.setContentDescription("Свой оттенок");add(a,colors,custom,32);};fill[0].run();box.addView(colors);
        return new Studio.Builder(a).anchor(anchor(a,tool)).setTitle(Ink.label(tool)).setView(box).show();
    }
    static void size(MainActivity a,LinearLayout box,boolean eraser,Preview preview){
        LinearLayout labels=a.row();labels.addView(a.text(eraser?"Диаметр":"Толщина",12,Studio.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));TextView value=a.text("",12,Studio.ACCENT,true);labels.addView(value);box.addView(labels);
        Studio.Slider slider=new Studio.Slider(a,eraser?5:.5f,eraser?200:60,eraser?a.canvas.eraserWidth:a.canvas.width,n->{if(eraser)a.canvas.eraserWidth=n;else a.canvas.width=n;value.setText(String.format(Locale.ROOT,"%.1f pt",n));preview.invalidate();a.toolChanged();a.canvas.invalidate();});value.setText(String.format(Locale.ROOT,"%.1f pt",slider.value));box.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(37)));
        LinearLayout presets=a.row();for(float n:eraser?new float[]{12,30,60,120}:new float[]{1,3,6,12,24})add(a,presets,chip(a,String.format(Locale.ROOT,"%.0f",n),false,()->slider.set(n)),29);box.addView(presets);
    }
    static Studio.Sheet eraser(MainActivity a){
        LinearLayout box=a.column();ToolArt.Tile eraserArt=new ToolArt.Tile(a,"eraser");LinearLayout artRow=a.row();eraserArt.setClickable(false);artRow.addView(eraserArt,new LinearLayout.LayoutParams(a.dp(46),a.dp(66)));box.addView(artRow);Preview preview=new Preview(a,"eraser",true);artRow.addView(preview,new LinearLayout.LayoutParams(0,a.dp(66),1));gap(a,box,10);
        LinearLayout modes=a.row();Runnable[] fill=new Runnable[1];fill[0]=()->{modes.removeAllViews();for(String mode:new String[]{"area","stroke"})add(a,modes,chip(a,mode.equals("area")?"В пределах круга":"Целый штрих",a.canvas.eraserMode.equals(mode),()->{a.canvas.eraserMode=mode;a.canvas.hoverHidden.clear();a.prefs.edit().putString("eraserMode",mode).apply();a.canvas.invalidate();a.refreshTools();eraserArt.invalidate();preview.invalidate();fill[0].run();}),32);};fill[0].run();box.addView(modes);gap(a,box,12);size(a,box,true,preview);
        return new Studio.Builder(a).anchor(anchor(a,"eraser")).setTitle("Ластик").setView(box).show();
    }
    static Studio.Sheet compass(MainActivity a){
        NotesView v=a.canvas;LinearLayout box=a.column();final Studio.Sheet[] sheet={null};
        box.addView(a.text("1  Выберите центр на холсте.\n2  Задайте радиус.\n3  Ведите перо по нужной части окружности.",12,Studio.TEXT,false));gap(a,box,12);
        box.addView(a.text("Радиус, px",12,Studio.MUTED,false));gap(a,box,7);EditText radius=Studio.input(a,""+(int)v.compassRadius,"Радиус");radius.setInputType(8194);radius.setTextSize(14);radius.setPadding(a.dp(12),0,a.dp(12),0);box.addView(radius,new LinearLayout.LayoutParams(-1,a.dp(38)));gap(a,box,9);
        LinearLayout presets=a.row();for(int n:new int[]{50,100,180,300})add(a,presets,chip(a,""+n,false,()->radius.setText(""+n)),29);box.addView(presets);gap(a,box,10);
        Runnable apply=()->{v.finishGesture(false);try{v.compassRadius=Ink.clamp(Float.parseFloat(radius.getText().toString().replace(',','.')),5,2400);}catch(Exception ignored){}a.savePenPrefs();v.invalidate();};
        box.addView(Studio.button(a,v.compassPinned?"Выбрать другой центр":"Выбрать центр",false,()->{apply.run();v.compassCenterNext=true;v.invalidate();sheet[0].dismiss();}));gap(a,box,9);
        box.addView(a.text("Перо направляет дугу, циркуль удерживает радиус. Можно отвести перо и продолжить с другого места.",11,Studio.MUTED,false));
        sheet[0]=new Studio.Builder(a).anchor(GuideStudio.anchor(a)).setTitle("Циркуль").setView(box).setNegativeButton("Убрать",(d,w)->v.setGuide("none")).setPositiveButton("Применить",(d,w)->apply.run()).show();return sheet[0];
    }
    static final class Preview extends View {
        final MainActivity a;final String tool;final boolean eraser;final Paint paint=new Paint(3);final Ink.Stroke sample=new Ink.Stroke();
        Preview(MainActivity a,String tool,boolean eraser){super(a);this.a=a;this.tool=tool;this.eraser=eraser;setBackground(Studio.surface(0xff181b21,14,a.getResources().getDisplayMetrics().density));setContentDescription("Предпросмотр: "+Ink.label(tool));}
        protected void onDraw(Canvas c){super.onDraw(c);if(a.canvas==null)return;c.save();c.scale(getWidth()/274f,getHeight()/66f);c.clipRect(0,0,274,66);
            sample.texture=tool.equals("tape")?a.canvas.tapeTexture:0;sample.tool=eraser?"pen":tool;sample.width=eraser?5:a.canvas.width*.65f;sample.color=eraser?Studio.ACCENT:a.canvas.colorFor(tool);if(sample.points.isEmpty()){
            for(int i=0;i<=90;i++){float t=i/90f;sample.points.add(new Ink.Pt(16+t*242,33+(float)Math.sin(t*Math.PI*2)*12,.08f+.92f*(float)Math.sin(t*Math.PI),t*1.2f,t*2.7f,.7f));}
            if(tool.equals("rect")||tool.equals("ellipse")){sample.points.clear();sample.points.add(new Ink.Pt(35,16,1,0,0));sample.points.add(new Ink.Pt(239,50,1,0,0));}
            else if(tool.equals("triangle")){sample.points.clear();sample.points.add(new Ink.Pt(137,12,1,0,0));sample.points.add(new Ink.Pt(218,53,1,0,0));sample.points.add(new Ink.Pt(56,53,1,0,0));sample.points.add(sample.points.get(0));}
            else if(tool.equals("arrow")){sample.points.clear();sample.points.add(new Ink.Pt(25,43,1,0,0));sample.points.add(new Ink.Pt(249,23,1,0,0));sample.points.add(new Ink.Pt(233,15,1,0,0));sample.points.add(new Ink.Pt(249,23,1,0,0));sample.points.add(new Ink.Pt(235,34,1,0,0));}
            else if(tool.equals("text")){sample.text="Aa · Идея";sample.width=Math.max(12,a.canvas.width*.65f);sample.points.clear();sample.points.add(new Ink.Pt(18,12,1,0,0));}
            }if(tool.equals("text"))sample.width=Math.max(12,a.canvas.width*.65f);
            if(eraser){float t=Studio.motion()?(SystemClock.uptimeMillis()%3000)/3000f:.5f,x=50+174*(.5f-.5f*(float)Math.cos(t*Math.PI*2)),y=33+(float)Math.sin((x-16)/242*Math.PI*2)*12,r=a.canvas.eraserWidth*.33f;boolean whole=a.canvas.eraserMode.equals("stroke");c.save();if(!whole)c.clipOutPath(circle(x,y,r));if(!whole||x<55)InkRenderer.stroke(c,sample);c.restore();paint.setStyle(Paint.Style.STROKE);paint.setColor(0xffe4e6ef);paint.setStrokeWidth(.8f);c.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(Studio.ACCENT);c.drawCircle(x,y,1.4f,paint);if(Studio.motion()&&hasWindowFocus())postInvalidateOnAnimation();}
            else InkRenderer.stroke(c,sample);c.restore();
        }
        Path circle(float x,float y,float r){Path p=new Path();p.addCircle(x,y,r,Path.Direction.CW);return p;}
    }
}
