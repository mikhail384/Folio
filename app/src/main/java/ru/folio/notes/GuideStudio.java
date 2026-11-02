package ru.folio.notes;

import android.view.*;
import android.widget.*;

/** Persistent pen/guide tabs; changing ink never dismisses a drawing aid. */
final class GuideStudio {
    static View tabs(MainActivity a){
        LinearLayout tabs=a.column();tabs.setGravity(Gravity.CENTER);a.pad(tabs,5,3);
        for(String tab:new String[]{"ink","guides"}){
            TextView button=a.text(tab.equals("ink")?"Перо":"Чертёж",11,Studio.TEXT,true);
            button.setGravity(Gravity.CENTER);button.setTag("tool_tab_"+tab);button.setContentDescription("Вкладка «"+button.getText()+"»");
            button.setOnClickListener(v->{a.toolsTab=tab;a.refreshTools();});ToolbarMotion.press(a,button);
            tabs.addView(button,new LinearLayout.LayoutParams(-1,a.dp(28)));
        }
        return tabs;
    }
    static void updateTabs(MainActivity a){
        if(a.body==null)return;
        for(String tab:new String[]{"ink","guides"}){
            TextView button=a.body.findViewWithTag("tool_tab_"+tab);if(button==null)continue;
            boolean selected=a.toolsTab.equals(tab);button.setSelected(selected);
            if(tab.equals("guides"))button.setText(a.canvas!=null&&!a.canvas.guideMode.equals("none")?"Чертёж ·":"Чертёж");
            button.setTextColor(selected?Studio.ACCENT:Studio.MUTED);
            button.setBackground(Studio.ripple(a,selected?0xff36354a:android.graphics.Color.TRANSPARENT,10));
        }
    }
    static String selected(NotesView v){return v.guideMode.equals("ruler")?"line":v.guideMode.equals("compass")?"compass":"";}
    static View anchor(MainActivity a){return CompactInspector.anchor(a,selected(a.canvas));}
    static void select(MainActivity a,String mode,boolean options){
        if(a.canvas==null)return;a.toolsTab="guides";a.canvas.setGuide(mode);a.refreshTools();
        if(options){if(mode.equals("ruler"))RulerOverlay.options(a);else if(mode.equals("compass"))CompactInspector.compass(a);}
    }
    static void populate(MainActivity a){
        for(String tool:new String[]{"line","compass"}){
            String mode=GuideTools.mode(tool);ToolArt.Tile tile=new ToolArt.Tile(a,tool);
            tile.setContentDescription(tool.equals("line")?"Бесконечная линейка":"Циркуль");
            tile.setOnClickListener(v->select(a,a.canvas.guideMode.equals(mode)?"none":mode,false));
            tile.setOnLongClickListener(v->{select(a,mode,true);return true;});
            a.tools.addView(tile,new LinearLayout.LayoutParams(a.dp(51),a.dp(64)));
        }
        View divider=new View(a);divider.setBackgroundColor(Studio.LINE);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(a.dp(1),a.dp(26));dl.setMargins(a.dp(9),0,a.dp(9),0);a.tools.addView(divider,dl);
        TextView ink=a.text("",10,Studio.MUTED,true);ink.setTag("guide-ink");ink.setGravity(Gravity.CENTER);a.pad(ink,7,0);
        ink.setContentDescription("Выбрать перо для рисования по линейке или циркулем");ink.setOnClickListener(v->{a.toolsTab="ink";a.refreshTools();});ToolbarMotion.press(a,ink);a.tools.addView(ink,new LinearLayout.LayoutParams(-2,a.dp(48)));
        a.addIcon(a.tools,"settings","Настройки чертёжного приспособления",()->{if(a.canvas.guideMode.equals("none"))a.toast("Выберите линейку или циркуль");else select(a,a.canvas.guideMode,true);});
        a.addIcon(a.tools,"close","Убрать линейку или циркуль",()->a.canvas.setGuide("none"));
    }
}
