from pathlib import Path
from edit_source import method
p='app/src/main/java/ru/folio/notes/MainActivity.java'
source=Path(p).read_text(encoding='utf-8-sig')
source=source.replace('static final int BG=0xfff7f8fc,INK=0xff2c364d,MUTED=0xff8992a6,ACCENT=0xff6979de;','static final int BG=0xff202226,INK=0xffeff1f5,MUTED=0xff959da9,ACCENT=0xff1687ed;')
source=source.replace('NotebookStore store;','NotebookStore store;\n    GestureActions actions;\n    final ArrayList<View> chrome=new ArrayList<>();')
source=source.replace('store=new NotebookStore(this);PenProvider.activity=', 'store=new NotebookStore(this);actions=new GestureActions(this);PenProvider.activity=')
source=source.replace('solid?ACCENT:0xffeceffc','solid?ACCENT:0xff29333e').replace('0xffe9edf4','0xff363c44').replace('0xfff5f6fa','0xff1e2228').replace('0xffe3e8fc','0xff233c56')
source=source.replace('51,INK','51,0xff28344b').replace('31,INK','31,0xff28344b')
source=source.replace('resumed=false;taps.cancel();closeLearning();','resumed=false;taps.cancel();actions.cancel();closeLearning();')
source=source.replace('if(!focus){taps.cancel();','if(!focus){taps.cancel();actions.cancel();')
Path(p).write_text(source,encoding='utf-8')
method(p,'void library()', 'void library(){WorkspaceUi.library(this);}')
method(p,'void buildEditor()', 'void buildEditor(){WorkspaceUi.editor(this);}')
method(p,'void refreshTools()', 'void refreshTools(){WorkspaceUi.refresh(this);}')
method(p,'void applyPenPrefs()', '''void applyPenPrefs(){
        canvas.pressure=prefs.getBoolean("pressure",true);canvas.showHover=prefs.getBoolean("hover",true);canvas.fingerDraw=prefs.getBoolean("finger",false);canvas.edgeSwipe=prefs.getBoolean("edge",true);canvas.rotationEnabled=prefs.getBoolean("rotation",true);canvas.eraserMode=prefs.getString("eraserMode","area");canvas.tool=prefs.getString("tool","pen");if(!Arrays.asList(Ink.TOOLS).contains(canvas.tool))canvas.tool="pen";canvas.previous=canvas.tool.equals("eraser")?"pen":canvas.tool;canvas.color=prefs.getInt("ink",0xff28344b);
        for(String name:Ink.TOOLS)canvas.widths.put(name,prefs.getFloat("width_"+name,Ink.defaultWidth(name)));canvas.width=canvas.widths.get(canvas.tool);canvas.eraserWidth=prefs.getFloat("eraser",42);if(Float.isFinite(PenProvider.rotationDegrees))canvas.rotation(PenProvider.rotationDegrees,SystemClock.uptimeMillis());
    }''')
method(p,'void toggleFocus()', '''void toggleFocus(){focusMode=!focusMode;editorHeader.setVisibility(focusMode?View.GONE:View.VISIBLE);for(View view:chrome)view.setVisibility(focusMode?View.GONE:View.VISIBLE);systemBars(false);}''')
method(p,'public void hardware(', '''public void hardware(long timestamp,int code,int value){
        if(!penActive())return;
        if(learningKey!=null&&value==1){String key=learningKey;SharedPreferences.Editor edit=prefs.edit();for(String other:new String[]{"tapCode","doubleCode","squeezeCode","prevCode","nextCode"})if(!other.equals(key)&&prefs.getInt(other,0)==code)edit.putInt(other,0);edit.putInt(key,code).apply();closeLearning();toast("Запомнен сигнал Linux "+code);return;}
        if(code==squeezeCode()&&code!=0){actions.squeeze(timestamp,value);return;}
        if(value!=1)return;
        if(code==tapCode()&&code!=0){taps.tap(timestamp,this);ui.postDelayed(()->taps.flush(SystemClock.uptimeMillis(),this),320);}
        else if(code==prefs.getInt("doubleCode",96)){taps.cancel();actions.run("double");}
        else if(code==prefs.getInt("prevCode",73))actions.run("up");
        else if(code==prefs.getInt("nextCode",81))actions.run("down");
    }''')
method(p,'public void single()', 'public void single(){if(penActive())actions.run("single");}')
method(p,'public void doubleTap()', 'public void doubleTap(){if(penActive())actions.run("double");}')
method(p,'public boolean dispatchKeyEvent(', '''public boolean dispatchKeyEvent(KeyEvent event){
        if(penActive()&&event.getScanCode()!=0){int code=event.getScanCode();if(code==tapCode()||code==prefs.getInt("doubleCode",96)||code==squeezeCode()||code==prefs.getInt("prevCode",73)||code==prefs.getInt("nextCode",81)){if(event.getRepeatCount()==0&&!PenProvider.connected())hardware(event.getEventTime(),code,event.getAction()==KeyEvent.ACTION_DOWN?1:0);return true;}}
        if(penActive()&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){if(event.isCtrlPressed()&&event.getKeyCode()==KeyEvent.KEYCODE_Z){if(event.isShiftPressed())canvas.redo();else canvas.undo();return true;}if(event.isCtrlPressed()&&event.getKeyCode()==KeyEvent.KEYCODE_Y){canvas.redo();return true;}if(event.getKeyCode()==KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY){gesture("button_primary");return true;}if(event.getKeyCode()==KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY){gesture("button_secondary");return true;}}
        return super.dispatchKeyEvent(event);
    }''')
method(p,'void settings()', '''void settings(){
        ScrollView scroll=new ScrollView(this);LinearLayout box=column();pad(box,24,8);box.addView(pill("Действия тапов и нажатий",true,()->actions.dialog()));TextView panel=pill("Набор инструментов панели",false,this::toolbarDialog);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(10);pp.bottomMargin=dp(14);box.addView(panel,pp);
        toggle(box,"Нажим пера","Толщина зависит от силы нажатия","pressure",true);toggle(box,"Вращение корпуса пера","Настоящий угол вращения Focus Pen Pro через ADB-мост","rotation",true);toggle(box,"Предпросмотр наконечника","Форма и размер кисти; предпросмотр ластика учитывает его режим","hover",true);toggle(box,"Рисование пальцем","По умолчанию пальцы перемещают лист","finger",false);toggle(box,"Свайп пером от края","Свайп от края листа перелистывает страницы","edge",true);toggle(box,"Подавлять панель HyperOS","Только в открытом холсте, без изменения настроек системы","suppress",true);
        if(canvas!=null)box.addView(pill("Режим и размер ластика",false,()->ToolSettings.eraser(this)));
        TextView h=text("FOCUS PEN PRO",11,ACCENT,true);pad(h,0,22);box.addView(h);bridgeInfo=text(bridgeText(),12,MUTED,false);pad(bridgeInfo,0,18);box.addView(bridgeInfo);box.addView(pill("Аппаратные коды и обучение",false,this::keySettings));TextView note=text("На этом пере прошивка передаёт двойной тап, сжатие и свайпы. Отдельного сигнала одиночного тапа корпуса не обнаружено. После перезагрузки планшета запустите Start-Folio-Pen.cmd. Перехват отключается при выходе из Folio.",12,MUTED,false);note.setLineSpacing(dp(4),1);pad(note,0,18);box.addView(note);box.addView(pill("Инструкция",false,this::help));scroll.addView(box);penSettingsDialog=new AlertDialog.Builder(this).setTitle("Перо и инструменты").setView(scroll).setPositiveButton("Готово",(d,w)->{bridgeInfo=null;if(canvas!=null)refreshTools();}).show();
    }''')
method(p,'void editorMenu()', '''void editorMenu(){String[] items={"Все инструменты","Настроить панель","Действия пера","Настройки пера","Ластик","Циркуль","Бумага","Добавить страницу","Импорт","Переименовать","Масштаб по размеру экрана","Режим фокуса","Инструкция"};new AlertDialog.Builder(this).setTitle("Тетрадь").setItems(items,(d,w)->{switch(w){case 0:new AlertDialog.Builder(this).setTitle("Пенал").setItems(Ink.LABELS,(pick,index)->canvas.setTool(Ink.TOOLS[index])).show();break;case 1:toolbarDialog();break;case 2:actions.dialog();break;case 3:settings();break;case 4:ToolSettings.eraser(this);break;case 5:canvas.setTool("compass");ToolSettings.compass(this);break;case 6:paperDialog();break;case 7:addPage();break;case 8:importPicker();break;case 9:renameDoc(current);break;case 10:canvas.resetView();break;case 11:toggleFocus();break;case 12:help();break;}}).show();}''')
method(p,'void help()', '''void help(){new AlertDialog.Builder(this).setTitle("Folio · инструменты").setMessage("Перо рисует. Пальцы перемещают и масштабируют лист, короткое касание двумя пальцами отменяет действие.\\n\\nВ настройках можно назначить действия двойному тапу, сжатию, удержанию, двойному сжатию, свайпам и кнопкам пера. Одиночный тап корпуса Focus Pen Pro эта прошивка отдельно не передаёт.\\n\\nУдержание инструмента открывает его настройки. Кнопка + в пенале меняет набор и порядок инструментов. Лента скрывает содержимое: выберите навигацию и коснитесь ленты, чтобы показать её. Лазер исчезает и не сохраняется.\\n\\nЛастик «В пределах окружности» стирает только внутри круга; при наведении скрывается тот же участок. В режиме «Целый штрих» временно скрывается всё действие. Круг виден и во время стирания.\\n\\nЦиркуль: касание задаёт центр, движение — радиус. Для дуги выберите режим в настройках циркуля и ведите перо вокруг центра. Транспортир поворачивается движением пера от центра.\\n\\nВращение кисти использует отдельную ось пера через ADB-мост. После перезагрузки планшета мост нужно запустить снова.\\n\\nPDF импортируется фоном, Folio сохраняет редактируемые штрихи и маски ластика. Автосохранение работает после действий и при выходе.").setPositiveButton("Понятно",null).show();}''')
source=Path(p).read_text(encoding='utf-8')
source=source.replace('void toast(String message)', 'void toolbarDialog(){ToolSettings.panel(this);}\n    @Override public void gesture(String name){if(actions!=null)actions.run(name);}\n    void toast(String message)')
source=source.replace('canvas.edgeSwipe=prefs.getBoolean("edge",true);if(!canvas.showHover)', 'canvas.edgeSwipe=prefs.getBoolean("edge",true);canvas.rotationEnabled=prefs.getBoolean("rotation",true);if(!canvas.showHover)')
Path(p).write_text(source,encoding='utf-8')
