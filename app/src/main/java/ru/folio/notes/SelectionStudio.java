package ru.folio.notes;

import android.widget.*;
import java.util.*;

/** The lasso uses the same animated cards as every other instrument. */
final class SelectionStudio {
    static final ArrayList<Ink.Stroke> clipboard=new ArrayList<>();
    static Studio.Sheet show(MainActivity a){LinearLayout box=a.column();Studio.Sheet[] sheet={null};
        item(a,box,sheet,"rainbow","Изменить цвет","Только выделенные штрихи",()->recolor(a));
        item(a,box,sheet,"search","Масштаб и поворот","Размер и угол выделения",()->new Studio.Builder(a).anchor(CompactInspector.anchor(a,"lasso")).setTitle("Преобразовать").setItems(new String[]{"Размер в процентах","Поворот в градусах"},(d,w)->a.edit(w==0?"Размер, %":"Поворот, °","Число",w==0?"100":"0",value->{try{float n=Float.parseFloat(value.replace(',','.'));a.canvas.transformSelection(w==0?Ink.clamp(n/100,.1f,8):1,w==1?n:0);}catch(Exception e){a.toast("Введите число");}})).show());
        for(boolean horizontal:new boolean[]{true,false})item(a,box,sheet,"redo",horizontal?"Отразить по горизонтали":"Отразить по вертикали","Нажим, наклон и маски сохраняются",()->{float[] b=a.canvas.selectionBounds();change(a,s->s.mirrored((b[0]+b[2])/2,(b[1]+b[3])/2,horizontal));});
        item(a,box,sheet,"layers","Перенести на слой","Видимый незаблокированный слой",()->{LinearLayout layers=a.column();for(Ink.Layer l:a.canvas.page.layers)if(l.visible&&!l.locked)Studio.addCard(a,layers,Studio.row(a,"layers",l.name,"",()->change(a,s->{Ink.Stroke copy=s.copy();copy.layer=l.id;return copy;})));new Studio.Builder(a).setTitle("Слой выделения").setView(layers).setPositiveButton("Готово",null).show();});
        item(a,box,sheet,"copy","Дублировать","Копия рядом с исходным выделением",()->a.canvas.copySelection());
        item(a,box,sheet,"copy","Скопировать в буфер Folio","Вставка на другую страницу",()->{clipboard.clear();for(Ink.Stroke s:a.canvas.page.strokes)if(a.canvas.selection.contains(s))clipboard.add(s.copy());a.toast("Скопировано в буфер Folio");});
        item(a,box,sheet,"trash","Удалить","Можно отменить одним действием",()->a.canvas.deleteSelection());
        Studio.addCard(a,box,Studio.row(a,"plus","Вставить из буфера",clipboard.size()+" штрихов",()->{sheet[0].dismiss();paste(a);}));sheet[0]=new Studio.Builder(a).anchor(CompactInspector.anchor(a,"lasso")).width(365).contentHeight(365).setTitle("Выделение").setView(box).setPositiveButton("Готово",null).show();return sheet[0];
    }
    static void recolor(MainActivity a){NotesView v=a.canvas;Ink.Page page=v.page;boolean[] checkpoint={false};a.colorDialog(v.activeColor(),color->{if(a.canvas!=v||v.page!=page)return;if(!checkpoint[0]){v.checkpoint();checkpoint[0]=true;}for(Ink.Stroke stroke:v.selection)if(page.editable(stroke))stroke.color=color;v.rebuild();v.changed();});}
    interface Edit {Ink.Stroke apply(Ink.Stroke s);}
    static void change(MainActivity a,Edit edit){NotesView v=a.canvas;if(v.selection.isEmpty())return;v.checkpoint();HashSet<Ink.Stroke> next=new HashSet<>();for(int i=0;i<v.page.strokes.size();i++){Ink.Stroke old=v.page.strokes.get(i);if(v.selection.contains(old)&&v.page.editable(old)){Ink.Stroke copy=edit.apply(old);v.page.strokes.set(i,copy);next.add(copy);}}v.page.order();v.selection.clear();v.selection.addAll(next);v.indexAll();v.rebuild();v.changed();}
    static void item(MainActivity a,LinearLayout box,Studio.Sheet[] sheet,String icon,String title,String detail,Runnable action){Studio.addCard(a,box,Studio.row(a,icon,title,detail,()->{if(a.canvas.selection.isEmpty()){a.toast("Сначала обведите штрихи");return;}sheet[0].dismissThen(()->{if(a.canvas!=null)action.run();});}));}
    static void paste(MainActivity a){NotesView v=a.canvas;if(clipboard.isEmpty()){a.toast("Буфер Folio пуст");return;}if(!v.page.writable()){a.toast("Выберите видимый незаблокированный слой");return;}v.checkpoint();float left=Float.MAX_VALUE,top=left,right=-left,bottom=-left;for(Ink.Stroke s:clipboard){float[] b=s.bounds();left=Math.min(left,b[0]);top=Math.min(top,b[1]);right=Math.max(right,b[2]);bottom=Math.max(bottom,b[3]);}float dx=v.px(v.getWidth()/2f)-(left+right)/2,dy=v.py(v.getHeight()/2f)-(top+bottom)/2;HashMap<String,String> groups=new HashMap<>();v.selection.clear();for(Ink.Stroke s:clipboard){Ink.Stroke copy=s.translated(dx,dy);copy.layer=v.page.activeLayer;copy.action=groups.computeIfAbsent(s.action,k->Ink.id());v.page.strokes.add(copy);v.selection.add(copy);}v.page.order();v.indexAll();v.rebuild();v.changed();}
}
