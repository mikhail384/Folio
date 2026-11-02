package ru.folio.notes;

import android.view.*;
import android.widget.*;

/** A single animated card system for notebook, content and instrument commands. */
final class FeatureStudio {
    static Studio.Sheet show(MainActivity a){if(a.canvas==null)return null;LinearLayout box=a.column(),tabs=a.row(),cards=a.column();box.addView(tabs);CompactInspector.gap(a,box,10);box.addView(cards);final int[] selected={0};Studio.Sheet[] dialog={null};Runnable[] fill={null};
        fill[0]=()->{tabs.removeAllViews();cards.removeAllViews();String[] labels={"Содержимое","Инструменты","Тетрадь"};for(int i=0;i<3;i++){final int tab=i;CompactInspector.add(a,tabs,CompactInspector.chip(a,labels[i],selected[0]==i,()->{selected[0]=tab;fill[0].run();Studio.enter(cards,0);}),32);}
            if(selected[0]==0){add(a,cards,dialog,"layers","Слои",a.canvas.page.layers.size()+" · видимость, порядок и защита",()->LayerStudio.show(a));add(a,cards,dialog,"bookmark","Страницы и закладки","Быстрый переход и поиск текста",()->NotebookNavigator.show(a));add(a,cards,dialog,"audio","Аудиозаметки",a.current.audio.size()+" · запись рядом с мыслями",()->a.audio.show());add(a,cards,dialog,"text","Распознавание текста","Русский и английский · без интернета",()->a.ocr.show());}
            else if(selected[0]==1){add(a,cards,dialog,"pen","Все инструменты","Ваш пенал и индивидуальные настройки",()->ToolStudio.gallery(a));add(a,cards,dialog,"compass","Чертёж","Линейка, циркуль и дуги",()->{a.toolsTab="guides";a.refreshTools();});add(a,cards,dialog,"dropper","Пипетка","Возьмите цвет с холста",()->Eyedropper.show(a,a.canvas.activeColor(),c->{if(a.canvas.canColor()){a.canvas.activeColor(c);a.toolChanged();}}));add(a,cards,dialog,"settings","Действия пера","Назначения тапов и сжатий",()->a.actions.dialog());add(a,cards,dialog,"grid","Настроить панель","Оставьте любимые инструменты",a::toolbarDialog);}
            else {add(a,cards,dialog,"paper","Холст и бумага","Размер, цвет и разметка",a::paperDialog);add(a,cards,dialog,"plus","Добавить страницу","С оформлением текущего холста",a::addPage);add(a,cards,dialog,"import","Импорт","PDF, изображения и .folio",a::importPicker);add(a,cards,dialog,"cloud","Резервные копии","Папка устройства или облачного провайдера",()->a.backup.show());add(a,cards,dialog,"pen","Переименовать",a.current.title,()->a.renameDoc(a.current));add(a,cards,dialog,"hand","Режим фокуса","Оставить только мысли и холст",a::toggleFocus);add(a,cards,dialog,"search","Показать весь рисунок","Вернуть камеру к содержимому",()->a.canvas.resetView());add(a,cards,dialog,"book","Инструкция","Возможности Folio и жесты",a::help);}
        };fill[0].run();dialog[0]=new Studio.Builder(a).anchor(a.editorHeader).width(420).contentHeight(410).setTitle("Мастерская").setView(box).setPositiveButton("Готово",null).show();return dialog[0];
    }
    static void add(MainActivity a,LinearLayout box,Studio.Sheet[] sheet,String glyph,String title,String detail,Runnable action){Studio.addCard(a,box,Studio.row(a,glyph,title,detail,()->{sheet[0].dismissThen(()->{if(a.canvas!=null)action.run();});}));}
}
