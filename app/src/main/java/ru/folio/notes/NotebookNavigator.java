package ru.folio.notes;

import android.text.*;
import android.widget.*;
import java.util.*;

final class NotebookNavigator {
    static Studio.Sheet show(MainActivity a){if(a.current==null)return null;Ink.Doc doc=a.current;LinearLayout box=a.column();EditText search=Studio.input(a,"","Закладки и текст на страницах");search.setTextSize(13);box.addView(search);LinearLayout tabs=a.row();final boolean[] bookmarks={false};LinearLayout results=a.column();Studio.Sheet[] dialog={null};Runnable[] fill={null};
        fill[0]=()->{tabs.removeAllViews();for(boolean only:new boolean[]{false,true})CompactInspector.add(a,tabs,CompactInspector.chip(a,only?"Закладки":"Все страницы",bookmarks[0]==only,()->{bookmarks[0]=only;fill[0].run();}),32);results.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
            for(int i=0;i<doc.pages.size();i++){final int index=i;Ink.Page page=doc.pages.get(i);if(bookmarks[0]&&page.bookmark.isEmpty())continue;StringBuilder text=new StringBuilder(page.bookmark);for(Ink.Stroke s:page.strokes)if(s.tool.equals("text")&&page.visible(s))text.append(' ').append(s.text);if(!query.isEmpty()&&!text.toString().toLowerCase(Locale.ROOT).contains(query))continue;
                String title=page.bookmark.isEmpty()?"Страница "+(i+1):page.bookmark;String detail=(page.infinite?"∞ · ":"")+"Страница "+(i+1)+(i==doc.current?" · открыта":"");Studio.addCard(a,results,Studio.row(a,page.bookmark.isEmpty()?"paper":"bookmark",title,detail,()->{dialog[0].dismiss();a.goPage(index);}));count++;}
            if(count==0)results.addView(a.text("Ничего не найдено",13,Studio.MUTED,false));};CompactInspector.gap(a,box,10);box.addView(tabs);CompactInspector.gap(a,box,10);box.addView(results);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int start,int before,int count){fill[0].run();}public void afterTextChanged(Editable e){}});fill[0].run();
        dialog[0]=new Studio.Builder(a).anchor(a.editorHeader).width(410).contentHeight(390).setTitle("Навигация").setView(box).setNeutralButton("Закладка",(d,w)->bookmark(a)).setPositiveButton("Закрыть",null).show();return dialog[0];
    }
    static void bookmark(MainActivity a){Ink.Page page=a.current.page();a.editName("Закладка страницы","Название",page.bookmark,value->{a.canvas.checkpoint();page.bookmark=value.isEmpty()?"Страница "+(a.current.current+1):value;a.canvas.changed();a.rebuildPageStrip();});}
    static void remove(MainActivity a){a.canvas.checkpoint();a.current.page().bookmark="";a.canvas.changed();a.rebuildPageStrip();}
}
