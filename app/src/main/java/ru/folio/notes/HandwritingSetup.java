package ru.folio.notes;

import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Optional first-run connection and handwriting setup. Existing notebooks are never edited. */
final class HandwritingSetup extends FrameLayout {
    static final String SEEN="handwriting_setup_seen",PENDING="handwriting_setup_pending";
    final MainActivity a;int page,group,index,loadGeneration;boolean saving,closing;PenPad pad;TextView status,moveButton;
    final Runnable saveDraftLater=this::persistDraft;
    final Runnable refreshStatus=new Runnable(){public void run(){if(!isAttachedToWindow())return;updateStatus();postDelayed(this,1200);}};

    static void show(MainActivity a,boolean firstRun){
        if(a.handwritingSetup!=null||a.isFinishing()||a.isDestroyed())return;
        if(a.canvas!=null)a.canvas.finishGesture(false);
        if(firstRun)a.prefs.edit().putBoolean(PENDING,true).apply();
        HandwritingSetup view=new HandwritingSetup(a);a.handwritingSetup=view;a.setupOpen=true;
        a.root.addView(view,new FrameLayout.LayoutParams(-1,-1));a.systemBars(false);view.requestFocus();Studio.enter(view,0);
    }
    HandwritingSetup(MainActivity activity){super(activity);a=activity;setBackgroundColor(Studio.BG);setClickable(true);setFocusableInTouchMode(true);setContentDescription("Настройка связи и почерка");build();}
    String label(){HandwritingTraining.Group g=HandwritingTraining.GROUPS[group];return g.prompts[Math.max(0,Math.min(index,g.prompts.length-1))];}
    void build(){
        removeCallbacks(refreshStatus);removeAllViews();pad=null;status=null;moveButton=null;
        LinearLayout all=a.column();a.pad(all,a.wide()?42:20,18);addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout head=a.row();TextView brand=a.text("folio",30,Studio.TEXT,true);brand.setLetterSpacing(-.06f);head.addView(brand);a.space(head,14);
        TextView mark=Studio.eyebrow(a,page==0?"СВЯЗЬ  ·  01 / 02":"ВАШ ПОЧЕРК  ·  02 / 02");head.addView(mark);a.stretch(head);if(page==1&&a.wide()){head.addView(Studio.button(a,"← Связь",false,()->{persistDraft();page=0;build();}));a.space(head,8);}head.addView(Studio.button(a,"Позже",false,this::finish));all.addView(head,new LinearLayout.LayoutParams(-1,a.dp(56)));
        if(page==0)connection(all);else exercise(all);
        post(refreshStatus);
    }
    void connection(LinearLayout all){
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);all.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout centre=new FrameLayout(a);scroll.addView(centre,new ScrollView.LayoutParams(-1,-1));LinearLayout box=a.column();a.pad(box,a.wide()?26:8,16);
        FrameLayout.LayoutParams inner=new FrameLayout.LayoutParams(a.wide()?a.dp(760):-1,-2,Gravity.CENTER);centre.addView(box,inner);
        TextView eyebrow=Studio.eyebrow(a,"ИДЕИ МОГУТ ПРОДОЛЖАТЬСЯ");box.addView(eyebrow);
        TextView title=a.text("Ваш холст.\nВаш помощник.",a.wide()?42:30,Studio.TEXT,true);title.setLetterSpacing(-.04f);box.addView(title);
        TextView detail=a.text("Свяжите Folio с ChatGPT или Codex, чтобы читать открытую тетрадь и добавлять записи. Планшет и помощник могут быть в разных сетях; VPN остаётся включённым.",15,Studio.MUTED,false);detail.setLineSpacing(a.dp(5),1);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=a.dp(16);box.addView(detail,dl);
        LinearLayout card=a.column();a.pad(card,18,14);card.setBackground(Studio.surface(Studio.CARD,22,getResources().getDisplayMetrics().density));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2);cl.topMargin=a.dp(22);box.addView(card,cl);
        card.addView(a.text("01  Откройте страницу подключения",15,Studio.TEXT,true));
        TextView one=a.text("Войдите в нужный аккаунт и скопируйте строку подключения.",13,Studio.MUTED,false);a.pad(one,0,8);card.addView(one);
        card.addView(Studio.button(a,"Открыть страницу",false,()->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(a.agent.cloud.configured()?a.agent.url():AgentCloud.ORIGIN)));}catch(Exception e){a.toast("Не удалось открыть браузер");}}),new LinearLayout.LayoutParams(-1,-2));
        TextView two=a.text("02  Вернитесь в Folio и вставьте подключение",15,Studio.TEXT,true);a.pad(two,0,14);card.addView(two);
        card.addView(Studio.button(a,"Вставить и включить связь",true,()->{a.agent.importConnection(true);updateStatus();}),new LinearLayout.LayoutParams(-1,-2));
        TextView three=a.text("03  В чате выберите подключённый плагин Folio",13,Studio.MUTED,false);a.pad(three,0,12);card.addView(three);
        status=a.text("",12,Studio.MUTED,false);a.pad(status,1,12);card.addView(status);
        moveButton=Studio.button(a,"Перенести ожидающие образцы",false,this::movePending);moveButton.setVisibility(GONE);card.addView(moveButton,new LinearLayout.LayoutParams(-1,-2));
        TextView privacy=a.text("Доступ можно выключить в настройках. Сам холст доступен помощнику, только пока открыта тетрадь. Настройку можно пропустить и вернуться к ней позже.",12,Studio.MUTED,false);privacy.setLineSpacing(a.dp(4),1);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,-2);pl.topMargin=a.dp(16);box.addView(privacy,pl);
        LinearLayout footer=a.row();a.stretch(footer);footer.addView(Studio.button(a,"Настроить почерк  →",true,()->{page=1;build();}));all.addView(footer,new LinearLayout.LayoutParams(-1,a.dp(62)));
    }
    void exercise(LinearLayout all){
        TextView title=a.text("Напишите так, как вам удобно.",a.wide()?27:23,Studio.TEXT,true);title.setLetterSpacing(-.025f);all.addView(title);
        TextView hint=a.text("Выберите раздел и перепишите образец пером. Слова, предложения и формулы запоминаются целиком; отдельные буквы помогают составлять новые слова.",13,Studio.MUTED,false);hint.setLineSpacing(a.dp(3),1);LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);hl.topMargin=a.dp(8);all.addView(hint,hl);
        HorizontalScrollView tabs=new HorizontalScrollView(a);tabs.setHorizontalScrollBarEnabled(false);LinearLayout strip=a.row();
        final View[] currentChip={null};for(int i=0;i<HandwritingTraining.GROUPS.length;i++){final int selected=i;TextView chip=Studio.button(a,HandwritingTraining.GROUPS[i].title,i==group,()->switchGroup(selected));if(i==group)currentChip[0]=chip;LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,a.dp(46));cp.rightMargin=a.dp(8);strip.addView(chip,cp);}tabs.addView(strip);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,a.dp(54));tl.topMargin=a.dp(12);all.addView(tabs,tl);tabs.post(()->{if(currentChip[0]!=null)tabs.smoothScrollTo(Math.max(0,currentChip[0].getLeft()-a.dp(12)),0);});
        HandwritingTraining.Group g=HandwritingTraining.GROUPS[group];LinearLayout prompt=a.row();LinearLayout labels=a.column();TextView label=a.text(label(),a.wide()?27:22,Studio.TEXT,true);label.setContentDescription("Образец: "+label());labels.addView(label);TextView desc=a.text(g.description,12,Studio.MUTED,false);LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,-2);d.topMargin=a.dp(5);labels.addView(desc,d);prompt.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView count=a.text((index+1)+" / "+g.prompts.length,12,Studio.ACCENT,true);prompt.addView(count);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=a.dp(10);pp.bottomMargin=a.dp(12);all.addView(prompt,pp);
        pad=new PenPad();LinearLayout.LayoutParams padParams=new LinearLayout.LayoutParams(-1,0,1);padParams.bottomMargin=a.dp(8);all.addView(pad,padParams);
        Studio.enter(prompt,0);Studio.enter(pad,35);
        status=a.text("Загружаем сохранённые штрихи…",12,Studio.MUTED,false);status.setMinHeight(a.dp(32));all.addView(status);
        if(a.wide()){
            LinearLayout actions=a.row();actions.addView(Studio.button(a,"Отменить",false,()->pad.undo()));a.space(actions,8);actions.addView(Studio.button(a,"Очистить",false,()->pad.clear()));a.stretch(actions);actions.addView(Studio.button(a,"Пропустить",false,this::nextPrompt));a.space(actions,8);actions.addView(Studio.button(a,"Сохранить  →",true,this::saveSample));all.addView(actions,new LinearLayout.LayoutParams(-1,a.dp(62)));
        }else{
            LinearLayout edit=a.row();edit.addView(Studio.button(a,"Отменить",false,()->pad.undo()),new LinearLayout.LayoutParams(0,-2,1));a.space(edit,8);edit.addView(Studio.button(a,"Очистить",false,()->pad.clear()),new LinearLayout.LayoutParams(0,-2,1));all.addView(edit,new LinearLayout.LayoutParams(-1,a.dp(54)));
            LinearLayout next=a.row();next.addView(Studio.button(a,"Пропустить",false,this::nextPrompt),new LinearLayout.LayoutParams(0,-2,1));a.space(next,8);next.addView(Studio.button(a,"Сохранить  →",true,this::saveSample),new LinearLayout.LayoutParams(0,-2,1));all.addView(next,new LinearLayout.LayoutParams(-1,a.dp(54)));
        }
        TextView note=a.text("При сохранении одноимённый образец в профиле my-handwriting заменится. До подключения образцы остаются на устройстве.",11,Studio.MUTED,false);note.setMaxLines(2);all.addView(note);
        loadPrompt();
    }
    void loadPrompt(){
        final int generation=++loadGeneration;final String name=label();final PenPad target=pad;target.setEnabled(false);
        a.store.io.execute(()->{try{JSONArray draft=a.handwritingTraining.draft(name);JSONObject info=a.handwritingTraining.info(name);JSONArray strokes=draft.length()>0?draft:info==null?new JSONArray():info.getJSONArray("strokes");a.runOnUiThread(()->{if(a.handwritingSetup!=this||generation!=loadGeneration||pad!=target)return;target.load(strokes);target.setEnabled(true);updateStatus();});}catch(Exception e){a.runOnUiThread(()->{if(a.handwritingSetup==this&&generation==loadGeneration){target.setEnabled(false);status.setText("Не удалось открыть образцы: "+e.getMessage());}});}});
    }
    void switchGroup(int target){if(saving||target==group)return;persistDraft();group=target;index=0;build();}
    void movePending(){
        final String target=a.agent.trainingTarget();if(target.isEmpty()){a.toast("Сначала подключите нужный аккаунт");return;}
        a.store.io.execute(()->{try{int count=a.handwritingTraining.pendingOther(target);a.runOnUiThread(()->{if(a.handwritingSetup!=this)return;if(count==0){a.toast("Нет образцов для переноса");return;}new Studio.Builder(a).setTitle("Перенести образцы?").setMessage("Ожидающие образцы будут отправлены в подключённый сейчас аккаунт. Ранее отправленные образцы могут оставаться в прежнем профиле.").setNegativeButton("Отмена",null).setPositiveButton("Перенести",(d,w)->{a.store.io.execute(()->{try{int moved=a.handwritingTraining.movePending(target);a.runOnUiThread(()->a.toast("Подготовлено к отправке: "+moved));}catch(Exception e){a.runOnUiThread(()->a.toast("Не удалось перенести образцы"));}});}).show();});}catch(Exception e){a.runOnUiThread(()->a.toast("Не удалось прочитать образцы"));}});
    }
    void nextPrompt(){advance(true);}
    void advance(boolean keepDraft){if(saving)return;if(keepDraft)persistDraft();HandwritingTraining.Group g=HandwritingTraining.GROUPS[group];if(index+1<g.prompts.length)index++;else if(group+1<HandwritingTraining.GROUPS.length){group++;index=0;}else {a.toast("Все разделы просмотрены");return;}build();}
    void draftChanged(){removeCallbacks(saveDraftLater);postDelayed(saveDraftLater,450);}
    void persistDraft(){
        removeCallbacks(saveDraftLater);if(pad==null||!pad.isEnabled()||saving)return;final String name=label();final JSONArray strokes=pad.json();
        a.store.io.execute(()->{try{a.handwritingTraining.saveDraft(name,strokes);}catch(Exception e){a.runOnUiThread(()->{if(a.handwritingSetup==this)a.toast("Не удалось сохранить черновик");});}});
    }
    void saveSample(){
        if(saving||pad==null||!pad.isEnabled())return;removeCallbacks(saveDraftLater);final String name=label(),target=a.agent.trainingTarget();final JSONArray strokes=pad.json();
        try{HandwritingTraining.validate(strokes);}catch(Exception e){a.toast(e.getMessage());return;}
        saving=true;status.setText("Сохраняем образец…");
        a.store.io.execute(()->{try{a.handwritingTraining.save(name,strokes,target);a.runOnUiThread(()->{saving=false;if(a.handwritingSetup!=this)return;a.toast(target.isEmpty()?"Сохранено на устройстве · подключите облако позже":"Сохранено · отправим в облако");advance(false);});}catch(Exception e){a.runOnUiThread(()->{saving=false;if(a.handwritingSetup==this)status.setText("Не удалось сохранить: "+e.getMessage());});}});
    }
    void updateStatus(){
        if(status==null)return;
        if(page==0){status.setText(a.agent.cloud.configured()?(a.agent.enabled()?a.agent.displayStatus():"Подключение сохранено · доступ выключен"):"Пока не подключено · можно настроить позже");final TextView button=moveButton;final String target=a.agent.trainingTarget();a.store.io.execute(()->{try{int count=a.handwritingTraining.pendingOther(target);a.runOnUiThread(()->{if(a.handwritingSetup==this&&page==0&&moveButton==button&&button!=null)button.setVisibility(count>0?VISIBLE:GONE);});}catch(Exception ignored){}});return;}
        if(saving)return;
        final String name=label();final int generation=loadGeneration;final String target=a.agent.trainingTarget();
        a.store.io.execute(()->{try{String state=a.handwritingTraining.sampleStatus(name,target);int other=a.handwritingTraining.pendingOther(target);String text=state.isEmpty()?"Пишите стилусом в светлой области.":"synced".equals(state)?"Образец сохранён в облачном профиле.":"synced_other".equals(state)?"Этот образец сохранён для другого подключения.":"pending_other".equals(state)?"Образец ожидает отправки в другом подключении.":"Образец на устройстве · ожидает отправки.";if(other>0&&!state.equals("pending_other"))text+=" Есть образцы для другого подключения.";final String result=text;a.runOnUiThread(()->{if(a.handwritingSetup==this&&generation==loadGeneration&&status!=null&&!saving)status.setText(result);});}catch(Exception ignored){}});
    }
    void finish(){if(closing||saving)return;persistDraft();closing=true;a.prefs.edit().putBoolean(SEEN,true).putBoolean(PENDING,false).apply();if(Studio.motion())animate().alpha(0).translationY(-a.dp(12)).setDuration(180).setInterpolator(new DecelerateInterpolator()).withEndAction(this::closeNow).start();else closeNow();}
    void closeNow(){removeCallbacks(refreshStatus);removeCallbacks(saveDraftLater);animate().cancel();if(getParent() instanceof ViewGroup)((ViewGroup)getParent()).removeView(this);if(a.handwritingSetup==this)a.handwritingSetup=null;a.setupOpen=false;if(a.rotation!=null)a.rotation.update();}
    boolean back(){if(saving)return true;if(page==1){persistDraft();page=0;build();}else finish();return true;}
    void restoreAfterLayout(int oldPage,int oldGroup,int oldIndex,JSONArray strokes){page=oldPage;group=oldGroup;index=oldIndex;build();if(page==1&&pad!=null&&strokes!=null){final PenPad p=pad;final int generation=++loadGeneration;p.load(strokes);p.setEnabled(true);}}

    final class PenPad extends View {
        static final float WORLD_W=1200,WORLD_H=360;final Paint ink=new Paint(3);final ArrayList<Ink.Stroke> strokes=new ArrayList<>();Ink.Stroke active;Bitmap committed;int pointer=-1,points;float scale,left,top;boolean limited;
        PenPad(){super(a);setFocusable(true);setContentDescription("Область для письма стилусом");}
        void geometry(){scale=Math.max(.01f,Math.min((getWidth()-a.dp(12))/WORLD_W,(getHeight()-a.dp(12))/WORLD_H));left=(getWidth()-WORLD_W*scale)/2;top=(getHeight()-WORLD_H*scale)/2;}
        protected void onDraw(Canvas c){
            super.onDraw(c);geometry();c.save();c.translate(left,top);c.scale(scale,scale);
            ink.setStyle(Paint.Style.FILL);ink.setColor(0xfff9f7f1);c.drawRoundRect(0,0,WORLD_W,WORLD_H,24,24,ink);
            ink.setStrokeWidth(1.5f);ink.setColor(0xffe1e2df);c.drawLine(36,235,WORLD_W-36,235,ink);ink.setColor(0xffeeeeea);c.drawLine(36,105,WORLD_W-36,105,ink);
            if(strokes.isEmpty()){ink.setColor(0xffafb3bc);ink.setTextSize(25);c.drawText("Напишите здесь",40,65,ink);}
            ensureCache();ink.setStyle(Paint.Style.FILL);ink.setColor(Color.WHITE);c.drawBitmap(committed,0,0,ink);if(active!=null)drawStroke(c,active);
            c.restore();
        }
        void drawStroke(Canvas c,Ink.Stroke stroke){ink.setColor(0xff29354c);ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);Ink.Pt previous=null;for(Ink.Pt p:stroke.points){ink.setStrokeWidth(2.8f+3.3f*Math.min(1,p.p));if(previous==null)c.drawPoint(p.x,p.y,ink);else c.drawLine(previous.x,previous.y,p.x,p.y,ink);previous=p;}}
        void ensureCache(){if(committed!=null)return;committed=Bitmap.createBitmap((int)WORLD_W,(int)WORLD_H,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(committed);for(Ink.Stroke s:strokes)if(s!=active)drawStroke(c,s);}
        void rebuildCache(){if(committed!=null){committed.recycle();committed=null;}ensureCache();}
        void add(MotionEvent e,int i,int history){if(active==null||points>=HandwritingTraining.MAX_POINTS){limited=true;return;}float x=(history<0?e.getX(i):e.getHistoricalX(i,history)),y=(history<0?e.getY(i):e.getHistoricalY(i,history)),p=history<0?e.getPressure(i):e.getHistoricalPressure(i,history);x=Math.max(0,Math.min(WORLD_W,(x-left)/scale));y=Math.max(0,Math.min(WORLD_H,(y-top)/scale));if(!Float.isFinite(p))p=1;active.points.add(new Ink.Pt(x,y,p,0,0));points++;}
        public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(!isEnabled())return false;
            if(action==MotionEvent.ACTION_DOWN){int i=e.getActionIndex();if(e.getToolType(i)!=MotionEvent.TOOL_TYPE_STYLUS)return false;if(strokes.size()>=HandwritingTraining.MAX_STROKES){a.toast("Слишком много штрихов");return false;}geometry();if(e.getX(i)<left||e.getX(i)>left+WORLD_W*scale||e.getY(i)<top||e.getY(i)>top+WORLD_H*scale)return false;getParent().requestDisallowInterceptTouchEvent(true);ensureCache();pointer=e.getPointerId(i);active=new Ink.Stroke();active.tool="fountain";active.width=4;strokes.add(active);limited=false;add(e,i,-1);invalidate();return true;}
            if(pointer<0)return false;int i=e.findPointerIndex(pointer);boolean lifted=action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer);
            if(i>=0&&(action==MotionEvent.ACTION_MOVE||lifted)){for(int h=0;h<e.getHistorySize();h++)add(e,i,h);add(e,i,-1);invalidate();}
            if(lifted||action==MotionEvent.ACTION_CANCEL){pointer=-1;if(active!=null){ensureCache();drawStroke(new Canvas(committed),active);}active=null;getParent().requestDisallowInterceptTouchEvent(false);if(limited)a.toast("Образец достиг предела точек");draftChanged();performClick();}
            return true;
        }
        public boolean performClick(){super.performClick();return true;}
        JSONArray json(){JSONArray out=new JSONArray();for(Ink.Stroke s:strokes)try{out.put(s.json());}catch(Exception ignored){}return out;}
        void load(JSONArray raw){strokes.clear();points=0;for(int i=0;i<raw.length();i++)try{Ink.Stroke s=Ink.Stroke.parse(raw.getJSONObject(i));strokes.add(s);points+=s.points.size();}catch(Exception ignored){}rebuildCache();invalidate();}
        void undo(){if(strokes.isEmpty())return;Ink.Stroke s=strokes.remove(strokes.size()-1);points-=s.points.size();rebuildCache();invalidate();draftChanged();}
        void clear(){strokes.clear();points=0;rebuildCache();invalidate();draftChanged();}
        protected void onDetachedFromWindow(){if(committed!=null){committed.recycle();committed=null;}super.onDetachedFromWindow();}
    }
}
