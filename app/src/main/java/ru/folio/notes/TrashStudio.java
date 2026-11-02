package ru.folio.notes;

import android.content.SharedPreferences;
import android.graphics.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Multi-select trash. The footer stays available while the notebook list scrolls. */
final class TrashStudio {
    final MainActivity a;
    final ArrayList<Ink.Doc> documents=new ArrayList<>();
    final LinkedHashSet<String> selected=new LinkedHashSet<>();
    final LinkedHashMap<String,View> rows=new LinkedHashMap<>();
    final LinearLayout box,list;
    final TextView count,selectAll;
    Studio.Sheet sheet;boolean busy;

    static TrashStudio show(MainActivity a){TrashStudio screen=new TrashStudio(a);screen.open();return screen;}
    TrashStudio(MainActivity a){
        this.a=a;box=a.column();LinearLayout head=a.row();
        count=a.text("",12,Studio.MUTED,false);count.setContentDescription("Количество выбранных тетрадей");head.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        selectAll=CompactInspector.chip(a,"Выбрать всё",false,this::toggleAll);selectAll.setPadding(a.dp(12),0,a.dp(12),0);head.addView(selectAll,new LinearLayout.LayoutParams(-2,a.dp(35)));box.addView(head);CompactInspector.gap(a,box,11);
        list=a.column();box.addView(list);reload();
    }
    void open(){
        sheet=new Studio.Builder(a).width(510).contentHeight(430).setTitle("Корзина").setView(box).setNeutralButton("Вернуть",null).setNegativeButton("Готово",null).setPositiveButton("Удалить",null).create();
        sheet.setOnShowListener(d->{sheet.getButton(-3).setOnClickListener(v->restore(targets()));sheet.getButton(-1).setOnClickListener(v->confirmDelete());update();});sheet.show();
    }
    void reload(){
        documents.clear();for(Ink.Doc doc:a.docs)if(a.prefs.getBoolean("trash_"+doc.id,false))documents.add(doc);
        documents.sort((x,y)->Long.compare(y.modified,x.modified));HashSet<String> available=new HashSet<>();for(Ink.Doc doc:documents)available.add(doc.id);selected.retainAll(available);
        list.removeAllViews();rows.clear();
        for(Ink.Doc doc:documents){
            LinearLayout row=a.row();a.pad(row,12,12);Icons book=new Icons(a,"book");book.tint=doc.cover;row.addView(book,new LinearLayout.LayoutParams(a.dp(35),a.dp(45)));a.space(row,12);
            LinearLayout labels=a.column();TextView name=a.text(doc.title,14,Studio.TEXT,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);labels.addView(name);
            TextView detail=a.text("Страниц: "+doc.pages.size()+" · "+new SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(new Date(doc.modified)),11,Studio.MUTED,false);LinearLayout.LayoutParams meta=new LinearLayout.LayoutParams(-1,-2);meta.topMargin=a.dp(7);labels.addView(detail,meta);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));a.space(row,10);
            View check=new View(a){final Paint p=new Paint(3);protected void onDraw(Canvas c){boolean on=selected.contains(doc.id);float x=getWidth()/2f,y=getHeight()/2f,r=a.dp(10);p.setStyle(on?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.4f));p.setColor(on?Studio.ACCENT:Studio.MUTED);c.drawCircle(x,y,r,p);if(on){p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Studio.BG);p.setStrokeWidth(a.dp(1.8f));Path mark=new Path();mark.moveTo(x-a.dp(4),y);mark.lineTo(x-a.dp(1),y+a.dp(3));mark.lineTo(x+a.dp(5),y-a.dp(4));c.drawPath(mark,p);}}};check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(check,new LinearLayout.LayoutParams(a.dp(29),a.dp(44)));
            row.setFocusable(true);row.setTag(doc);row.setOnClickListener(v->toggle(doc.id));row.setOnLongClickListener(v->{if(!busy){toggle(doc.id);v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}return true;});Studio.press(row);rows.put(doc.id,row);Studio.addCard(a,list,row);
        }
        if(documents.isEmpty()){TextView empty=a.text("Корзина пуста",17,Studio.MUTED,false);empty.setGravity(Gravity.CENTER);a.pad(empty,12,42);list.addView(empty);}
        update();
    }
    void toggle(String id){if(busy||!rows.containsKey(id))return;if(!selected.remove(id))selected.add(id);update();}
    void toggleAll(){if(busy)return;if(selected.size()==documents.size())selected.clear();else for(Ink.Doc doc:documents)selected.add(doc.id);update();}
    ArrayList<Ink.Doc> targets(){ArrayList<Ink.Doc> result=new ArrayList<>();for(Ink.Doc doc:documents)if(selected.contains(doc.id)&&a.prefs.getBoolean("trash_"+doc.id,false))result.add(doc);return result;}
    void update(){
        count.setText(busy?"Удаление…":"Выбрано "+selected.size()+" из "+documents.size());selectAll.setText(!documents.isEmpty()&&selected.size()==documents.size()?"Снять выбор":"Выбрать всё");selectAll.setEnabled(!busy&&!documents.isEmpty());
        for(Map.Entry<String,View> entry:rows.entrySet()){boolean on=selected.contains(entry.getKey());View row=entry.getValue();row.setActivated(on);row.setEnabled(!busy);row.setBackground(Studio.ripple(a,on?0xff38364d:Studio.CARD,17));Ink.Doc doc=(Ink.Doc)row.getTag();row.setContentDescription((on?"Выбрано: ":"Выбрать: ")+(doc==null?"тетрадь":doc.title));row.invalidate();if(row instanceof ViewGroup){ViewGroup group=(ViewGroup)row;group.getChildAt(group.getChildCount()-1).invalidate();}}
        if(sheet!=null&&sheet.getButton(-1)!=null){for(int id:new int[]{-3,-1}){View button=sheet.getButton(id);boolean enabled=!busy&&!selected.isEmpty();button.setEnabled(enabled);button.setAlpha(enabled?1:.35f);}sheet.getButton(-3).setContentDescription("Восстановить выбранные тетради");sheet.getButton(-1).setContentDescription("Удалить выбранные тетради навсегда");}
    }
    void restore(ArrayList<Ink.Doc> targets){
        if(busy||targets.isEmpty())return;SharedPreferences.Editor edit=a.prefs.edit();int restored=0;for(Ink.Doc doc:targets)if(a.prefs.getBoolean("trash_"+doc.id,false)){edit.remove("trash_"+doc.id);restored++;}edit.apply();selected.clear();a.library();reload();a.toast("Восстановлено: "+restored);
    }
    void confirmDelete(){
        ArrayList<Ink.Doc> targets=targets();if(busy||targets.isEmpty())return;
        StringBuilder names=new StringBuilder();for(int i=0;i<Math.min(5,targets.size());i++){if(i>0)names.append('\n');names.append(targets.get(i).title);}if(targets.size()>5)names.append("\nИ ещё: ").append(targets.size()-5);
        a.confirm("Удалить выбранные навсегда?",names+"\n\nТетрадей: "+targets.size(),()->delete(targets));
    }
    void delete(ArrayList<Ink.Doc> targets){
        if(busy||targets.isEmpty())return;busy=true;update();
        a.store.io.execute(()->{
            ArrayList<Ink.Doc> deleted=new ArrayList<>();for(Ink.Doc doc:targets)try{a.store.delete(doc);if(!new File(a.store.root,doc.id+".json").exists())deleted.add(doc);}catch(RuntimeException e){android.util.Log.e("Folio","Trash delete failed",e);}
            SharedPreferences.Editor edit=a.prefs.edit();for(Ink.Doc doc:deleted)edit.remove("trash_"+doc.id);edit.apply();
            a.runOnUiThread(()->{if(a.isDestroyed())return;a.docs.removeAll(deleted);busy=false;for(Ink.Doc doc:deleted)selected.remove(doc.id);if(sheet.isShowing()){a.library();reload();}a.toast(deleted.size()==targets.size()?"Удалено: "+deleted.size():"Удалено: "+deleted.size()+". Остальные тетради остались в корзине.");});
        });
    }
}
