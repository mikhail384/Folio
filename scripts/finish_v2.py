"""One-time source migration for the 1.1 release; not part of the source distribution."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/folio/notes'
def edit(name,replacements):
    p=SRC/name
    s=p.read_text(encoding='utf-8')
    for before,after in replacements:
        assert before in s,(name,before)
        s=s.replace(before,after)
    p.write_text(s,encoding='utf-8')
edit('MainActivity.java',[
('final Runnable saveLater=()->saveNow();','final Runnable saveLater=new Runnable(){public void run(){if(canvas!=null&&(canvas.penId!=-1||canvas.draft!=null||canvas.gestureBefore!=null||canvas.moving)){ui.postDelayed(this,350);return;}saveNow();}};'),
('Icons v=new Icons(this,name);v.setContentDescription','Icons v=new Icons(this,name);v.tint=ACCENT;v.setContentDescription'),
('if(Float.isFinite(PenProvider.rotationDegrees))canvas.rotation(PenProvider.rotationDegrees,SystemClock.uptimeMillis());','if(Float.isFinite(PenProvider.rotationDegrees)&&SystemClock.uptimeMillis()-PenProvider.rotationAt<1500)canvas.rotation(PenProvider.rotationDegrees,PenProvider.rotationAt);'),
('case 6:confirm("Удалить тетрадь?","Для восстановления заранее экспортируйте файл .folio.",()->{docs.remove(doc);store.io.execute(()->store.delete(doc));library();});break;','case 6:prefs.edit().putBoolean("trash_"+doc.id,true).apply();library();toast("Тетрадь перемещена в корзину");break;'),
('"\\nПоследний сигнал: "+PenProvider.lastEvent;','"\\nПоследний сигнал: "+PenProvider.lastEvent+"\\nВращение: "+(Float.isFinite(PenProvider.rotationDegrees)?String.format(Locale.ROOT,"%.0f° · %d сигналов",PenProvider.rotationDegrees,PenProvider.rotationEvents):"нет сигнала от датчика");'),
('"Настоящий угол вращения Focus Pen Pro через ADB-мост"','"По сигналу датчика; Xiaomi официально поддерживает вращение на Pad 8 и новее"'),
('card.setBackground(bg(Color.WHITE,24))','card.setBackground(bg(BG,24))')])
edit('PenProvider.java',[
('static volatile long rotationAt;','static volatile long rotationAt,rotationEvents;'),
('reply.putLong("rotationAt",rotationAt);','reply.putLong("rotationAt",rotationAt);reply.putLong("rotationEvents",rotationEvents);'),
('if(value>=0&&value<=360){rotationDegrees=value;rotationAt=SystemClock.uptimeMillis();','if(value>=-360&&value<=360){rotationDegrees=(value%360+360)%360;rotationAt=SystemClock.uptimeMillis();rotationEvents++;')])
edit('WorkspaceUi.java',[
('a.addIcon(top,"book","Библиотека",()->{})','a.addIcon(top,"book","Библиотека",()->{a.folder="";a.library();})'),
('tp.topMargin=a.dp(20);sidebar.addView(title,tp);','tp.topMargin=0;sidebar.addView(title,tp);'),
('a.docs.remove(doc);a.store.io.execute(()->a.store.delete(doc));a.library();','a.docs.remove(doc);a.prefs.edit().remove("trash_"+doc.id).apply();a.store.io.execute(()->a.store.delete(doc));a.library();')])
edit('bridge/PenBridge.java',[
('static volatile int rotation=-1;','static volatile int rotation=Integer.MIN_VALUE;'),
('if(n>=0&&n<=360)rotation=n;','/* Initial state is not a fresh rotation sample. */'),
('long sent=0;while(true)','long sent=0;boolean dirty=false;while(true)'),
('if(type==3&&code==10){if(value>=0&&value<=360)rotation=value;else rotation=-1;}','if(type==3&&code==10){if(value>=-360&&value<=360){rotation=value;dirty=true;}else rotation=Integer.MIN_VALUE;}'),
('if(type==0&&code==0&&rotation>=0&&active&&time-sent>=12){events.offer(time+",-10,"+rotation);sent=time;}','if(type==0&&code==0&&dirty&&rotation!=Integer.MIN_VALUE&&active&&time-sent>=12){events.offer(time+",-10,"+rotation);sent=time;dirty=false;}'),
('if(active&&rotation>=0)events.offer(android.os.SystemClock.uptimeMillis()+",-10,"+rotation);','')])
