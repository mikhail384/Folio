package ru.folio.notes;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Paper presets and page proportions, kept beside the paper command. */
final class PaperStudio {
    static String caption(Ink.Page p){return p.infinite?"∞  Бесконечное поле":String.format(Locale.ROOT,"%.0f × %.0f px",p.width,p.height);}
    interface Change {void apply(Runnable update);}
    interface SizeChange {void apply(int preset,float width,float height);}
    static void show(MainActivity a){
        if(a.canvas==null)return;Ink.Page page=a.canvas.page;boolean[] checkpoint={false};
        LinearLayout box=controls(a,page,update->{if(!checkpoint[0]){a.canvas.checkpoint();checkpoint[0]=true;}update.run();a.canvas.paperChanged();},(preset,w,h)->apply(a,preset,w,h,()->{}));
        CompactInspector.gap(a,box,10);Studio.addCard(a,box,Studio.row(a,"star","Сделать стандартом","Это оформление для новых тетрадей",()->{CanvasDefaults.save(a.prefs,page);a.toast("Сохранено для новых тетрадей");}));
        Studio.addCard(a,box,Studio.row(a,"settings","Холст по умолчанию","Настроить оформление новых тетрадей",()->CanvasDefaultsStudio.show(a)));
        new Studio.Builder(a).anchor(a.body.findViewWithTag("paper")).width(365).contentHeight(400).setTitle("Холст и бумага").setView(box).setPositiveButton("Готово",(d,w)->a.rebuildPageStrip()).show();
    }
    static void resize(Ink.Page page,int preset,float w,float h){page.infinite=preset==0;if(preset!=0){page.width=w;page.height=h;}page.hasViewport=false;}
    static LinearLayout controls(MainActivity a,Ink.Page page,Change change,SizeChange sizeChange){
        LinearLayout box=a.column();
        TextView size=Studio.button(a,caption(page)+"  ›",false,null);
        size.setContentDescription("Размер холста");size.setOnClickListener(v->sizes(a,page,sizeChange,()->size.setText(caption(page)+"  ›")));box.addView(size);
        CompactInspector.gap(a,box,13);LinearLayout patterns=a.row();
        String[] ids={"blank","grid","lined","dots"},names={"Чистая","Клетка","Линии","Точки"};
        for(int i=0;i<ids.length;i++){
            String id=ids[i];LinearLayout cell=a.column();cell.setGravity(Gravity.CENTER);
            ToolStudio.PaperTile tile=new ToolStudio.PaperTile(a,id,page);cell.addView(tile,new LinearLayout.LayoutParams(-1,a.dp(72)));
            TextView label=a.text(names[i],10,Studio.MUTED,false);a.pad(label,0,8);cell.addView(label);
            cell.setContentDescription("Разметка: "+names[i]);cell.setOnClickListener(v->{change.apply(()->{page.pattern=id;});invalidate(patterns);});Studio.press(cell);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(a.dp(3),0,a.dp(3),0);patterns.addView(cell,lp);
        }box.addView(patterns);CompactInspector.gap(a,box,9);
        LinearLayout papers=a.row();papers.addView(a.text("Бумага",12,Studio.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        for(int color:PaperSizes.COLORS){View dot=new View(a){final Paint p=new Paint(3);protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=a.dp(10);p.setColor(color);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xff787d8a);c.drawCircle(x,y,r,p);if(page.paper==color){p.setColor(Studio.ACCENT);p.setStrokeWidth(a.dp(1.5f));c.drawCircle(x,y,r+a.dp(3),p);}p.setStyle(Paint.Style.FILL);}};
            dot.setContentDescription("Бумага "+ColorModel.hex(color));dot.setOnClickListener(v->{change.apply(()->{page.paper=color;page.lines=PaperSizes.linesFor(color);});invalidate(box);});papers.addView(dot,new LinearLayout.LayoutParams(a.dp(39),a.dp(38)));Studio.press(dot);
        }
        TextView custom=CompactInspector.chip(a,"+",false,()->a.colorDialog(page.paper,c->{change.apply(()->{page.paper=c;});invalidate(box);}));papers.addView(custom,new LinearLayout.LayoutParams(a.dp(32),a.dp(32)));box.addView(papers);
        Studio.addCard(a,box,Studio.row(a,"line","Цвет разметки","Свой оттенок",()->a.colorDialog(page.lines,c->{change.apply(()->{page.lines=c;});invalidate(box);})));
        CompactInspector.gap(a,box,8);TextView spacing=a.text("Шаг разметки: "+Math.round(page.spacing)+" px",12,Studio.MUTED,false);box.addView(spacing);
        Studio.Slider slider=new Studio.Slider(a,12,100,page.spacing,n->{change.apply(()->{page.spacing=n;spacing.setText("Шаг разметки: "+Math.round(n)+" px");});invalidate(patterns);});box.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(38)));
        return box;
    }
    static void invalidate(View v){v.invalidate();if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)invalidate(group.getChildAt(i));}}
    static void apply(MainActivity a,int preset,float w,float h,Runnable changed){a.canvas.checkpoint();Ink.Page page=a.canvas.page;page.infinite=preset==0;if(preset!=0){page.width=w;page.height=h;}page.hasViewport=false;a.canvas.zoom=1;a.canvas.fitPage();a.canvas.paperChanged();a.rebuildPageStrip();changed.run();}
    static void sizes(MainActivity a,Ink.Page page,SizeChange sizeChange,Runnable changed){
        LinearLayout box=a.column();final Studio.Sheet[] sheet={null};
        for(int row=0;row<3;row++){
            LinearLayout line=a.row();for(int col=0;col<2;col++){
                final int index=row*2+col;float[] dimensions=PaperSizes.SIZES[index];
                LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER);tile.setBackground(Studio.ripple(a,Studio.CARD,13));a.pad(tile,4,8);
                View illustration=new View(a){final Paint p=new Paint(3);protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f;p.setColor(Studio.ACCENT);if(index==0){p.setTextAlign(Paint.Align.CENTER);p.setTextSize(a.dp(33));c.drawText("∞",cx,cy+a.dp(11),p);return;}float scale=Math.min(a.dp(55)/dimensions[0],a.dp(37)/dimensions[1]),w=dimensions[0]*scale,h=dimensions[1]*scale;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.4f));c.drawRoundRect(cx-w/2,cy-h/2,cx+w/2,cy+h/2,a.dp(3),a.dp(3),p);p.setStyle(Paint.Style.FILL);}};
                tile.addView(illustration,new LinearLayout.LayoutParams(-1,a.dp(41)));TextView title=a.text(PaperSizes.NAMES[index],10,Studio.TEXT,true);title.setGravity(Gravity.CENTER);tile.addView(title);
                tile.setContentDescription(PaperSizes.NAMES[index]);tile.setOnClickListener(v->{sizeChange.apply(index,dimensions[0],dimensions[1]);changed.run();sheet[0].dismiss();});Studio.press(tile);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(77),1);lp.setMargins(a.dp(3),a.dp(3),a.dp(3),a.dp(3));line.addView(tile,lp);
            }box.addView(line);
        }
        CompactInspector.gap(a,box,10);box.addView(a.text("Свой размер",12,Studio.TEXT,true));CompactInspector.gap(a,box,7);
        LinearLayout fields=a.row();EditText width=Studio.input(a,""+Math.round(page.width),"Ширина"),height=Studio.input(a,""+Math.round(page.height),"Высота");
        for(EditText field:new EditText[]{width,height}){field.setInputType(8194);field.setTextSize(13);field.setSelectAllOnFocus(true);field.setPadding(a.dp(10),0,a.dp(10),0);fields.addView(field,new LinearLayout.LayoutParams(0,a.dp(37),1));a.space(fields,5);}width.setContentDescription("Ширина холста");height.setContentDescription("Высота холста");box.addView(fields);
        CompactInspector.gap(a,box,7);boolean[] mm={false};LinearLayout units=a.row();Runnable[] fill={null};fill[0]=()->{units.removeAllViews();for(boolean useMm:new boolean[]{false,true})CompactInspector.add(a,units,CompactInspector.chip(a,useMm?"Миллиметры":"Пиксели",mm[0]==useMm,()->{if(mm[0]!=useMm){try{float factor=useMm?1/PaperSizes.MM:PaperSizes.MM;width.setText(String.format(Locale.ROOT,"%.2f",Float.parseFloat(width.getText().toString().replace(',','.'))*factor));height.setText(String.format(Locale.ROOT,"%.2f",Float.parseFloat(height.getText().toString().replace(',','.'))*factor));}catch(Exception ignored){}mm[0]=useMm;fill[0].run();}}),30);};fill[0].run();box.addView(units);
        CompactInspector.gap(a,box,6);TextView limit=a.text("200–10 000 px · 1 см = 37,8 px",10,Studio.MUTED,false);box.addView(limit);
        sheet[0]=new Studio.Builder(a).anchor(a.canvas!=null?a.body.findViewWithTag("paper"):null).width(365).contentHeight(435).setTitle("Размер холста").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Свой размер",null).create();
        sheet[0].setOnShowListener(d->sheet[0].getButton(-1).setOnClickListener(v->{try{float w=PaperSizes.pixels(Float.parseFloat(width.getText().toString().replace(',','.')),mm[0]),h=PaperSizes.pixels(Float.parseFloat(height.getText().toString().replace(',','.')),mm[0]);if(!PaperSizes.valid(w,h))throw new IllegalArgumentException();sizeChange.apply(6,w,h);changed.run();sheet[0].dismiss();}catch(Exception error){width.setError("Размер от 200 до 10 000 px");}}));sheet[0].show();
    }
}
