package ru.folio.notes;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Configurable, ordered, application-local pen commands. */
final class GestureActions {
    static final String[] GESTURES={"single","double","squeeze","hold","double_squeeze","up","down","button_primary","button_secondary"};
    static final String[] LABELS={"Одиночный тап корпуса","Двойной тап корпуса","Сжатие","Удержание сжатия","Двойное сжатие","Свайп корпуса вверх","Свайп корпуса вниз","Основная кнопка пера","Вторая кнопка пера"};
    static final String[] DEFAULTS={"cycle","eraser","cycle","none","palette","prev","next","eraser","palette"};
    static final String[] COMMANDS={"none","cycle","eraser","palette","undo","redo","prev","next","add","color","thicker","thinner","focus","pages","paper","export","toolbar","copy","delete","clear"};
    static final String[] NAMES={"Ничего","Следующий инструмент панели","Инструмент ↔ ластик","Круговая палитра","Отменить","Повторить","Предыдущая страница","Следующая страница","Добавить страницу","Следующий цвет","Толще","Тоньше","Режим фокуса","Показать / скрыть страницы","Настройки бумаги","Экспорт","Настроить панель","Копировать выделение","Удалить выделение","Очистить страницу (с отменой)"};
    final MainActivity a;
    final SqueezeGestures squeezes=new SqueezeGestures();
    final Runnable hold,squeezeSingle;
    GestureActions(MainActivity a){
        this.a=a;installSqueezeBindings(a.prefs);
        hold=()->{if(a.penActive())squeezes.holdTimeout(this::run);else cancel();};
        squeezeSingle=()->{if(a.penActive())squeezes.singleTimeout(this::run);else cancel();};
    }
    static void installSqueezeBindings(android.content.SharedPreferences prefs){
        if(prefs.getInt("squeezeBindingsVersion",0)>=1)return;
        prefs.edit().putString("gesture_squeeze","[\"cycle\"]").putString("gesture_double_squeeze","[\"palette\"]").putInt("squeezeBindingsVersion",1).apply();
    }
    static final class Plan {final String raw;final List<String> commands;final String summary;final boolean enabled;Plan(String raw,List<String> commands){this.raw=raw;this.commands=Collections.unmodifiableList(commands);ArrayList<String> labels=new ArrayList<>();boolean on=false;for(String cmd:commands){labels.add(name(cmd));on|=!cmd.equals("none");}summary=String.join(" → ",labels);enabled=on;}}
    final HashMap<String,Plan> plans=new HashMap<>();int planBuilds;
    Plan plan(String gesture){int i=Arrays.asList(GESTURES).indexOf(gesture);String fallback=i<0?"none":DEFAULTS[i],raw=a.prefs.getString("gesture_"+gesture,"[\""+fallback+"\"]");Plan cached=plans.get(gesture);if(cached!=null&&cached.raw.equals(raw))return cached;ArrayList<String> list=new ArrayList<>();try{JSONArray json=new JSONArray(raw);for(int n=0;n<Math.min(12,json.length());n++)list.add(json.getString(n));}catch(Exception e){list.clear();list.add(fallback);}Plan value=new Plan(raw,list);plans.put(gesture,value);planBuilds++;return value;}
    ArrayList<String> commands(String gesture){return new ArrayList<>(plan(gesture).commands);}
    boolean enabled(String gesture){return plan(gesture).enabled;}
    void save(String gesture,List<String> commands){a.prefs.edit().putString("gesture_"+gesture,new JSONArray(commands).toString()).apply();}
    static String name(String command){int index=Arrays.asList(COMMANDS).indexOf(command);if(index>=0)return NAMES[index];if(command.startsWith("tool:"))return Ink.label(command.substring(5));return "Неизвестная команда";}
    String summary(String gesture){return plan(gesture).summary;}
    void run(String gesture){if(a.current==null||a.canvas==null)return;for(String cmd:plan(gesture).commands)command(cmd);}
    void command(String command){NotesView c=a.canvas;if(c==null)return;if(command.startsWith("tool:")){String name=command.substring(5);if(!GuideTools.auxiliary(name))c.setTool(name);return;}switch(command){
        case "cycle":c.cycle();break;case "eraser":c.toggleEraser();break;case "palette":c.showPalette();break;case "undo":c.undo();break;case "redo":c.redo();break;
        case "prev":a.page(-1);break;case "next":a.page(1);break;case "add":a.addPage();break;case "color":if(c.canColor()){int i=c.presets.selected(c.tool,c.activeColor());c.presets.select(c.tool,(i+1)%3);c.activeColor(c.presets.color(c.tool,(i+1)%3));a.toolChanged();}break;
        case "thicker":if(c.tool.equals("eraser"))c.eraserWidth=Math.min(200,c.eraserWidth+5);else c.width=Math.min(c.tool.equals("laser")?18:60,c.width+1);a.toolChanged();break;
        case "thinner":if(c.tool.equals("eraser"))c.eraserWidth=Math.max(5,c.eraserWidth-5);else c.width=Math.max(c.tool.equals("laser")?2:.5f,c.width-1);a.toolChanged();break;
        case "focus":a.toggleFocus();break;case "pages":a.showPages=!a.showPages;a.rebuildPageStrip();break;case "paper":a.paperDialog();break;case "export":a.exportDialog();break;case "toolbar":a.toolbarDialog();break;
        case "copy":c.copySelection();break;case "delete":c.deleteSelection();break;case "clear":c.checkpoint();c.page.strokes.clear();c.indexAll();c.rebuild();c.changed();break;
    }c.invalidate();}
    void squeeze(long timestamp,int value){
        if(!a.penActive()){cancel();return;}
        boolean changed=value==1?squeezes.down(timestamp,enabled("hold"),enabled("double_squeeze"),this::run):value==0&&squeezes.up(timestamp,this::run);
        if(!changed)return;
        a.ui.removeCallbacks(hold);a.ui.removeCallbacks(squeezeSingle);
        if(squeezes.awaitingHold())a.ui.postDelayed(hold,EventTiming.remaining(squeezes.downAt,SqueezeGestures.HOLD_MS,SystemClock.uptimeMillis()));
        if(squeezes.awaitingSingle())a.ui.postDelayed(squeezeSingle,EventTiming.remaining(squeezes.firstUp,SqueezeGestures.DOUBLE_MS,SystemClock.uptimeMillis()));
    }
    void cancel(){squeezes.cancel();a.ui.removeCallbacks(hold);a.ui.removeCallbacks(squeezeSingle);}
    void dialog(){LinearLayout box=a.column();box.addView(Studio.eyebrow(a,"ЖЕСТ → ВАШЕ ДЕЙСТВИЕ"));for(int i=0;i<GESTURES.length;i++){String key=GESTURES[i];Studio.addCard(a,box,Studio.row(a,i==1?"eraser":i>=5&&i<=6?"paper":"pen",LABELS[i],summary(key),()->edit(key)));}TextView note=a.text("Доступность жестов корпуса зависит от пера и прошивки. Кнопки пера используют назначенные здесь действия.",12,Studio.MUTED,false);a.pad(note,0,14);box.addView(note);new Studio.Builder(a).setTitle("Действия пера").setView(box).setPositiveButton("Готово",null).show();}
    void edit(String gesture){ArrayList<String> sequence=commands(gesture);LinearLayout box=a.column();a.pad(box,20,8);Runnable[] fill=new Runnable[1];fill[0]=()->{box.removeAllViews();for(int i=0;i<sequence.size();i++){final int index=i;LinearLayout row=a.row();TextView title=a.text((i+1)+". "+name(sequence.get(i)),14,MainActivity.INK,false);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));a.addIcon(row,"back","Выше",()->{if(index>0){Collections.swap(sequence,index,index-1);fill[0].run();}});a.addIcon(row,"trash","Убрать",()->{sequence.remove(index);fill[0].run();});box.addView(row);}box.addView(a.pill("+ Добавить действие",false,()->choose(command->{if(sequence.size()<12)sequence.add(command);fill[0].run();})));};fill[0].run();ScrollView scroll=new ScrollView(a);scroll.addView(box);new Studio.Builder(a).setTitle("Последовательность действий").setView(scroll).setNegativeButton("Отмена",null).setNeutralButton("По умолчанию",(d,w)->{a.prefs.edit().remove("gesture_"+gesture).apply();}).setPositiveButton("Сохранить",(d,w)->save(gesture,sequence)).show();}
    interface Pick {void choose(String command);}
    void choose(Pick callback){ArrayList<String> ids=new ArrayList<>(Arrays.asList(COMMANDS)),labels=new ArrayList<>(Arrays.asList(NAMES));for(String tool:Ink.TOOLS)if(!GuideTools.auxiliary(tool)){ids.add("tool:"+tool);labels.add("Выбрать: "+Ink.label(tool));}new Studio.Builder(a).setTitle("Действие").setItems(labels.toArray(new String[0]),(d,w)->callback.choose(ids.get(w))).show();}
}
