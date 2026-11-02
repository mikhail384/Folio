package ru.folio.notes;

import android.widget.*;

/** An isolated draft of the defaults, available from both the library and an open notebook. */
final class CanvasDefaultsStudio {
    static Studio.Sheet show(MainActivity a){
        Ink.Page draft=CanvasDefaults.read(a.prefs);LinearLayout box=a.column();
        box.addView(a.text("Размер и бумага новых тетрадей. Новые страницы наследуют оформление своей тетради.",12,Studio.MUTED,false));CompactInspector.gap(a,box,12);
        box.addView(PaperStudio.controls(a,draft,Runnable::run,(preset,w,h)->PaperStudio.resize(draft,preset,w,h)));
        return new Studio.Builder(a).width(365).contentHeight(420).setTitle("Холст по умолчанию").setView(box)
            .setNeutralButton("Сбросить",(d,w)->{CanvasDefaults.reset(a.prefs);a.toast("Стандартное оформление восстановлено");})
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{CanvasDefaults.save(a.prefs,draft);a.toast("Сохранено для новых тетрадей");}).show();
    }
}
