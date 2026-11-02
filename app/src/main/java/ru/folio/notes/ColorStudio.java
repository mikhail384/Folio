package ru.folio.notes;

import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Compact gradient and swatch picker. Colors are applied immediately; closing keeps the last valid choice. */
final class ColorStudio {
    final MainActivity a;final android.content.SharedPreferences preferences;final ColorModel model;final MainActivity.ColorResult accept;
    final java.util.ArrayList<Swatch> swatches=new java.util.ArrayList<>();
    final LinearLayout content;final FrameLayout pages;final Grid grid;final Gradient gradient;final EditText hex;final Swatch preview;final Tabs tabs;
    Studio.Sheet sheet;View anchor;String title="Цвет";Runnable closed;boolean syncing,changed;int tab,lastEmitted;
    static Studio.Sheet show(MainActivity a,int color,MainActivity.ColorResult accept){return new ColorStudio(a,color,accept).open();}
    static Studio.Sheet show(MainActivity a,int color,MainActivity.ColorResult accept,View anchor,String title){ColorStudio picker=new ColorStudio(a,color,accept);picker.anchor=anchor;picker.title=title;return picker.open();}
    ColorStudio(MainActivity a,int color,MainActivity.ColorResult accept){
        this.a=a;preferences=a.prefs;this.accept=accept;model=new ColorModel(color);lastEmitted=model.color();content=a.column();
        tabs=new Tabs();content.addView(tabs,new LinearLayout.LayoutParams(-1,a.dp(36)));CompactInspector.gap(a,content,10);
        pages=new FrameLayout(a);grid=new Grid();pages.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        gradient=new Gradient();pages.addView(gradient,new FrameLayout.LayoutParams(-1,-1));content.addView(pages,new LinearLayout.LayoutParams(-1,a.dp(225)));CompactInspector.gap(a,content,9);
        LinearLayout presets=a.row();TextView presetLabel=a.text("Готовые цвета",10,Studio.MUTED,false);presets.addView(presetLabel,new LinearLayout.LayoutParams(0,-2,1));String tool=a.canvas==null?"pen":a.canvas.tool;
        for(int preset:a.canvas!=null&&a.canvas.canColor()?a.canvas.presets.forTool(tool):ColorDefaults.forTool(tool)){Swatch dot=new Swatch(preset);swatches.add(dot);dot.setContentDescription("Готовый цвет "+ColorModel.hex(preset));dot.setOnClickListener(v->choose(preset,false));presets.addView(dot,new LinearLayout.LayoutParams(a.dp(38),a.dp(33)));Studio.press(dot);}content.addView(presets,new LinearLayout.LayoutParams(-1,a.dp(33)));CompactInspector.gap(a,content,8);
        LinearLayout exact=a.row();preview=new Swatch(model.color());preview.setContentDescription("Выбранный цвет");exact.addView(preview,new LinearLayout.LayoutParams(a.dp(34),a.dp(36)));a.space(exact,8);hex=Studio.input(a,ColorModel.hex(model.color()),"#RRGGBB");hex.setTextSize(13);hex.setPadding(a.dp(12),0,a.dp(12),0);hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);hex.setSelectAllOnFocus(true);hex.setContentDescription("Точный цвет HEX");exact.addView(hex,new LinearLayout.LayoutParams(0,a.dp(36),1));content.addView(exact);
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(syncing)return;try{choose(ColorModel.parse(s.toString()),true);hex.setError(null);}catch(IllegalArgumentException ignored){hex.setError(s.length()>0?"Формат #RRGGBB":null);}}public void afterTextChanged(Editable e){}});
        if(a.canvas!=null){CompactInspector.gap(a,content,7);content.addView(CompactInspector.chip(a,"◉  Пипетка с холста",false,()->{sheet.dismissThen(()->Eyedropper.show(a,model.color(),sampled->choose(sampled,false),()->remember(model.color())));}),new LinearLayout.LayoutParams(-1,a.dp(32)));}
        int[] recent=recent();if(recent.length>0){CompactInspector.gap(a,content,8);LinearLayout row=a.row();row.addView(a.text("Недавние",10,Studio.MUTED,false),new LinearLayout.LayoutParams(0,-2,1));for(int c:recent){Swatch dot=new Swatch(c);swatches.add(dot);dot.setContentDescription("Недавний цвет "+ColorModel.hex(c));dot.setOnClickListener(v->choose(c,false));row.addView(dot,new LinearLayout.LayoutParams(a.dp(28),a.dp(30)));}content.addView(row);}
        tab=Math.max(0,Math.min(1,preferences.getInt("color_picker_gradient_tab",1)));selectTab(tab,false);refresh(false);
    }
    Studio.Sheet open(){sheet=new Studio.Builder(a).anchor(anchor!=null?anchor:a.canvas==null?null:CompactInspector.anchor(a,a.canvas.tool)).width(365).contentHeight(420).setTitle(title).setView(content).create();sheet.setOnDismissListener(dialog->{if(changed)remember(model.color());if(closed!=null)closed.run();});sheet.show();return sheet;}
    void emit(){int color=model.color();if(color!=lastEmitted){lastEmitted=color;changed=true;accept.accept(color);}}
    void choose(int color,boolean fromHex){model.set(color);refresh(!fromHex);}
    void refresh(boolean updateHex){int color=model.color();preview.color=color;preview.invalidate();for(Swatch dot:swatches)dot.invalidate();grid.invalidate();gradient.invalidate();if(updateHex&&!hex.getText().toString().equals(ColorModel.hex(color))){syncing=true;hex.setText(ColorModel.hex(color));hex.setSelection(hex.length());hex.setError(null);syncing=false;}emit();}
    void selectTab(int value,boolean animate){tab=value;tabs.invalidate();preferences.edit().putInt("color_picker_gradient_tab",value).apply();for(int i=0;i<2;i++){View page=pages.getChildAt(i);page.animate().cancel();page.setVisibility(i==value?View.VISIBLE:View.GONE);if(i==value){page.setAlpha(animate?0:1);page.setTranslationY(animate?a.dp(4):0);if(animate)page.animate().alpha(1).translationY(0).setDuration(170).start();}}}
    int[] recent(){try{JSONArray data=new JSONArray(preferences.getString("color_recent","[]"));int[] colors=new int[Math.min(8,data.length())];for(int i=0;i<colors.length;i++)colors[i]=data.getInt(i)|0xff000000;return colors;}catch(Exception e){return new int[0];}}
    void remember(int color){int[] colors=ColorModel.recent(recent(),color,8);JSONArray data=new JSONArray();for(int c:colors)data.put(c);preferences.edit().putString("color_recent",data.toString()).apply();}
    final class Tabs extends View {final Paint p=new Paint(3);Tabs(){super(a);setContentDescription("Образцы или градиент цвета");setFocusable(true);}protected void onDraw(Canvas c){float d=a.dp(1),half=getWidth()/2f;p.setColor(0xff191c23);c.drawRoundRect(0,0,getWidth(),getHeight(),10*d,10*d,p);p.setColor(0xff414550);c.drawRoundRect(tab*half+3*d,3*d,(tab+1)*half-3*d,getHeight()-3*d,8*d,8*d,p);p.setTextSize(12*d);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);for(int i=0;i<2;i++){p.setColor(tab==i?Studio.TEXT:Studio.MUTED);c.drawText(i==0?"Образцы":"Градиент",half*(i+.5f),getHeight()/2f+4*d,p);}}public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP){selectTab(e.getX()<getWidth()/2f?0:1,preferences.getBoolean("card_motion",true));performClick();}return true;}public boolean performClick(){super.performClick();return true;}}
    final class Grid extends View {final Paint p=new Paint(3);final RectF rect=new RectF();Grid(){super(a);setFocusable(true);setContentDescription("Палитра: 117 оттенков, 13 столбцов и 9 строк");}protected void onDraw(Canvas c){float w=getWidth()/13f,h=getHeight()/9f;int selected=-1,current=model.color();for(int i=0;i<ColorDefaults.GRID.length;i++){p.setColor(ColorDefaults.GRID[i]);rect.set(i%13*w,i/13*h,(i%13+1)*w,(i/13+1)*h);c.drawRect(rect,p);if(ColorDefaults.GRID[i]==current)selected=i;}if(selected>=0){float x=(selected%13+.5f)*w,y=(selected/13+.5f)*h;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(2));p.setColor(0xff15171c);c.drawCircle(x,y,Math.min(w,h)*.34f,p);p.setStrokeWidth(a.dp(1.4f));p.setColor(Color.WHITE);c.drawCircle(x,y,Math.min(w,h)*.34f-a.dp(1),p);p.setStyle(Paint.Style.FILL);}}public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){int at=ColorDefaults.hit(e.getX(),e.getY(),getWidth(),getHeight());if(at>=0)choose(ColorDefaults.GRID[at],false);if(action==MotionEvent.ACTION_UP)performClick();}return true;}public boolean performClick(){super.performClick();return true;}}
    /** Hue ring and saturation/value field: no sliders, no bitmap allocations while dragging. */
    final class Gradient extends View {
        final Paint p=new Paint(3);final RectF field=new RectF();Shader ring,colorField,shade;int drag,lastHue;
        Gradient(){super(a);setFocusable(true);setContentDescription("Градиент: кольцо оттенка, внутри насыщенность и яркость");}
        void layoutField(){float side=Math.min(getWidth(),getHeight()),half=side*.265f;field.set(getWidth()/2f-half,getHeight()/2f-half,getWidth()/2f+half,getHeight()/2f+half);}
        protected void onSizeChanged(int w,int h,int oldw,int oldh){ring=colorField=shade=null;layoutField();}
        protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,side=Math.min(getWidth(),getHeight()),radius=side*.435f;
            if(ring==null)ring=new SweepGradient(cx,cy,new int[]{0xffff0000,0xffffff00,0xff00ff00,0xff00ffff,0xff0000ff,0xffff00ff,0xffff0000},null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(side*.10f);p.setShader(ring);c.drawCircle(cx,cy,radius,p);p.setShader(null);p.setStyle(Paint.Style.FILL);
            int hue=ColorModel.rgb(model.hue,1,.5f);
            if(colorField==null||lastHue!=hue){lastHue=hue;colorField=new LinearGradient(field.left,0,field.right,0,Color.WHITE,hue,Shader.TileMode.CLAMP);}if(shade==null)shade=new LinearGradient(0,field.top,0,field.bottom,0x00000000,Color.BLACK,Shader.TileMode.CLAMP);
            p.setShader(colorField);c.drawRect(field,p);p.setShader(shade);c.drawRect(field,p);p.setShader(null);
            float angle=(float)Math.toRadians(model.hue);cursor(c,cx+radius*(float)Math.cos(angle),cy+radius*(float)Math.sin(angle),side*.032f,hue);
            cursor(c,field.left+field.width()*model.vividness(),field.bottom-field.height()*model.value(),a.dp(6),model.color());
        }
        void cursor(Canvas c,float x,float y,float r,int color){p.setColor(0xff15171c);c.drawCircle(x,y,r+a.dp(2),p);p.setColor(Color.WHITE);c.drawCircle(x,y,r,p);p.setColor(color);c.drawCircle(x,y,Math.max(1,r-a.dp(2)),p);}
        public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();float x=e.getX(),y=e.getY();
            if(action==MotionEvent.ACTION_DOWN){layoutField();float side=Math.min(getWidth(),getHeight()),distance=(float)Math.hypot(x-getWidth()/2f,y-getHeight()/2f);drag=field.contains(x,y)?1:Math.abs(distance-side*.435f)<side*.1f?2:0;if(drag==0)return false;getParent().requestDisallowInterceptTouchEvent(true);}
            if(action==MotionEvent.ACTION_CANCEL){drag=0;return true;}
            if(drag!=0&&(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)){
                if(drag==1)model.gradient((x-field.left)/field.width(),(field.bottom-y)/field.height());
                else {float angle=(float)Math.toDegrees(Math.atan2(y-getHeight()/2f,x-getWidth()/2f));model.hue=(angle+360)%360;}
                refresh(true);if(action==MotionEvent.ACTION_UP){drag=0;performClick();}
            }return true;
        }
        public boolean performClick(){super.performClick();return true;}
    }
    final class Swatch extends View {int color;final Paint p=new Paint(3);Swatch(int color){super(a);this.color=color;setFocusable(true);}protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())*.32f;p.setColor(color);c.drawCircle(getWidth()/2f,getHeight()/2f,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0x558f95a3);c.drawCircle(getWidth()/2f,getHeight()/2f,r,p);if(this!=preview&&color==model.color()){p.setColor(Studio.ACCENT);p.setStrokeWidth(a.dp(1.5f));c.drawCircle(getWidth()/2f,getHeight()/2f,r+a.dp(3),p);}p.setStyle(Paint.Style.FILL);}}
}
