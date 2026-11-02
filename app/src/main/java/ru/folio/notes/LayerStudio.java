package ru.folio.notes;

import android.view.*;
import android.widget.*;
import java.util.*;

/** Layers share page history, archive persistence, eraser protection and export visibility. */
final class LayerStudio {
    static Studio.Sheet show(MainActivity a){if(a.canvas==null)return null;a.canvas.finishGesture(false);LinearLayout box=a.column();Studio.Sheet[] sheet={null};Runnable[] fill={null};
        fill[0]=()->{box.removeAllViews();Ink.Page page=a.canvas.page;box.addView(a.text("Сверху — передний план. Замок защищает от пера, ластика и лассо.",11,Studio.MUTED,false));CompactInspector.gap(a,box,10);
            for(int i=page.layers.size()-1;i>=0;i--){final int index=i;Ink.Layer layer=page.layers.get(i);LinearLayout card=a.row();a.pad(card,5,5);boolean active=layer.id.equals(page.activeLayer);card.setBackground(Studio.ripple(a,active?0xff3a374e:Studio.CARD,16));
                LinearLayout label=a.column();TextView name=a.text(layer.name,13,active?Studio.ACCENT:Studio.TEXT,true);name.setMaxLines(2);label.addView(name);long count=page.strokes.stream().filter(s->s.layer.equals(layer.id)).count();label.addView(a.text(count+" штрихов"+(active?" · выбран":""),10,Studio.MUTED,false));a.pad(label,9,6);card.addView(label,new LinearLayout.LayoutParams(0,-2,1));
                label.setOnClickListener(v->{a.canvas.checkpoint();page.activeLayer=layer.id;a.canvas.layersChanged();fill[0].run();});label.setOnLongClickListener(v->{a.edit("Название слоя","Название",layer.name,text->{a.canvas.checkpoint();layer.name=text;a.canvas.layersChanged();fill[0].run();});return true;});
                a.addIcon(card,layer.visible?"eye":"eye-off",layer.visible?"Скрыть слой":"Показать слой",()->{a.canvas.checkpoint();layer.visible=!layer.visible;a.canvas.layersChanged();fill[0].run();});
                a.addIcon(card,layer.locked?"lock":"unlock",layer.locked?"Разблокировать слой":"Защитить слой",()->{a.canvas.checkpoint();layer.locked=!layer.locked;a.canvas.layersChanged();fill[0].run();});
                a.addIcon(card,"more","Действия со слоем",()->{LinearLayout actions=a.column();Studio.addCard(a,actions,Studio.row(a,"pen","Переименовать","",()->a.edit("Название слоя","Название",layer.name,text->{a.canvas.checkpoint();layer.name=text;a.canvas.layersChanged();fill[0].run();})));
                    for(int delta:new int[]{1,-1})if(index+delta>=0&&index+delta<page.layers.size())Studio.addCard(a,actions,Studio.row(a,delta>0?"up":"down",delta>0?"На передний план":"На задний план","На один слой",()->{a.canvas.checkpoint();int at=page.layers.indexOf(layer),to=at+delta;if(at<0||to<0||to>=page.layers.size())return;Collections.swap(page.layers,at,to);a.canvas.layersChanged();fill[0].run();}));
                    if(page.layers.size()>1)Studio.addCard(a,actions,Studio.row(a,"trash","Удалить слой","Вместе с его штрихами · доступна отмена",()->a.confirm("Удалить «"+layer.name+"»?","Изменение можно отменить на холсте.",()->{if(!page.layers.contains(layer)||page.layers.size()==1)return;a.canvas.checkpoint();int at=page.layers.indexOf(layer);page.strokes.removeIf(s->s.layer.equals(layer.id));page.layers.remove(layer);if(page.activeLayer.equals(layer.id))page.activeLayer=page.layers.get(Math.max(0,at-1)).id;a.canvas.layersChanged();fill[0].run();})));
                    new Studio.Builder(a).anchor(card).setTitle(layer.name).setView(actions).show();});Studio.addCard(a,box,card);Studio.enter(card,Math.min(4,page.layers.size()-1-i)*20);
            }
            box.addView(Studio.button(a,"+  Новый слой",true,()->{if(page.layers.size()>=64){a.toast("Доступно до 64 слоёв");return;}a.canvas.checkpoint();Ink.Layer layer=new Ink.Layer();layer.id=Ink.id();layer.name="Слой "+(page.layers.size()+1);page.layers.add(layer);page.activeLayer=layer.id;a.canvas.layersChanged();fill[0].run();}));};fill[0].run();sheet[0]=new Studio.Builder(a).anchor(a.editorHeader).width(410).contentHeight(390).setTitle("Слои").setView(box).setPositiveButton("Готово",null).show();return sheet[0];
    }
}
