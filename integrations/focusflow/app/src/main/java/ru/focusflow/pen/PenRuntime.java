package ru.focusflow.pen;

import android.content.Context;
import android.os.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class PenRuntime {
    private static PenRuntime instance;
    public static synchronized PenRuntime get(Context c) { if(instance==null) instance=new PenRuntime(c.getApplicationContext()); return instance; }
    final Context context;
    final Handler handler = new Handler(Looper.getMainLooper());
    private final PenGestureDetector detector = new PenGestureDetector((delay,task)->{
        handler.postDelayed(task,delay);return ()->handler.removeCallbacks(task);
    },this::onGesture);
    private final ArrayDeque<JSONObject> log = new ArrayDeque<>();
    private final ArrayDeque<String> executionQueue=new ArrayDeque<>();
    public volatile JSONObject config;
    public volatile boolean bridgeConnected, grabbed, running;
    public volatile int bridgeProtocol;
    public volatile boolean stopRequested;
    public String device="", bridgeError="", lastSignal="", foreground="";
    public int squeezes, doubles, swipes, gestureCount, generation;
    public volatile PenService service;
    final ru.folio.penbridge.ActionFence priorityFence=new ru.folio.penbridge.ActionFence();
    public boolean refreshFolioPriority(){boolean blocked=FolioPriority.active(context);setFolioPriority(blocked);return blocked;}
    void setFolioPriority(boolean blocked){if(priorityFence.update(blocked)){cancel();resetDetector();recordDown=recordLast=-1;addLog(blocked?"Folio открыт: действия FocusFlow приостановлены":"Folio закрыт: действия FocusFlow доступны");FocusFlowTileService.refresh(context);}}
    boolean actionsAllowed(){return !refreshFolioPriority()&&!stopRequested;}
    public volatile long lastHeartbeat;
    public int configRevision,recordRevision;public boolean recording,screenRecording;private long recordDown=-1,recordLast=-1;
    private JSONArray recorded=new JSONArray();private int recordedGap=0;
    private PenRuntime(Context c) {
        context=c; config=Config.load(c);configureDetector(); addLog("Приложение готово. Жесты и комбинации → ваши действия.");
        handler.postDelayed(new Runnable(){public void run(){
            if(bridgeConnected&&SystemClock.elapsedRealtime()-lastHeartbeat>5500){bridgeConnected=false;grabbed=false;resetDetector();addLog("ADB-помощник отключён");FocusFlowTileService.refresh(context);}
            handler.postDelayed(this,1800);
        }},1800);
    }
    public synchronized void addLog(String message) {
        try {
            JSONObject entry=new JSONObject().put("time",new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date())).put("text",message);
            log.addFirst(entry); while(log.size()>60) log.removeLast();
            android.util.Log.i("FocusFlow",message);
        } catch(JSONException ignored) {}
    }
    public void update(JSONObject next) throws JSONException {
        Config.save(context,next); config=Config.load(context); configRevision++;configureDetector(); cancel();
        if(!config.optBoolean("enabled")){endRecording();if(service!=null)service.stopScreenRecording();}
        FocusFlowTileService.refresh(context);
    }
    public void setEnabled(boolean enabled)throws JSONException{JSONObject next=new JSONObject(config.toString());next.put("enabled",enabled);update(next);}
    public void event(boolean down, long time, String source) {
        input(189,down,time,source);
    }
    public void input(int code,boolean down,long time,String source){
        if(source.equals("ADB")&&config.optBoolean("nativePenOnly",true))return;
        if(!actionsAllowed())return;
        if(screenRecording)return;
        if((source.equals("Android")||source.equals("Xiaomi SDK")) && bridgeConnected&&!config.optBoolean("nativePenOnly",true)) return;
        if(recording){
            if(code==189){if(down)recordDown=time;else if(recordDown>=0&&time-recordDown<=900){recordToken("pinch",time);recordDown=-1;}}
            else if(down&&(code==SWIPE_UP_CODE||code==SWIPE_DOWN_CODE))recordToken(code==SWIPE_UP_CODE?"up":"down",time);
            return;
        }
        if(code==189&&!down) { squeezes++; lastSignal="Сжатие · "+source; addLog(lastSignal); }
        if(!config.optBoolean("enabled")) {detector.reset(); return;}
        if(code==189)detector.pinch(down,time,config.optInt("windowMs",550));
        else if(down&&(code==SWIPE_UP_CODE||code==SWIPE_DOWN_CODE)){
            swipes++;String direction=code==SWIPE_UP_CODE?"up":"down";lastSignal=(direction.equals("up")?"Свайп вверх":"Свайп вниз")+" · "+source;addLog(lastSignal);
            detector.swipe(direction,time,config.optInt("swipeWindowMs",450));
        }
    }
    public static final int SWIPE_UP_CODE=9991,SWIPE_DOWN_CODE=9992;
    private void onGesture(String id){
        if(!actionsAllowed())return;
        gestureCount++;if(id.equals("squeeze_double"))doubles++;
        lastSignal=gestureLabel(id);addLog("Жест: "+lastSignal);
        if(actionsFor(id).length()>0)execute(id);
    }
    private JSONArray actionsFor(String id){
        JSONObject mappings=config.optJSONObject("mappings");
        JSONArray list=mappings==null?null:mappings.optJSONArray(id);
        return list==null?new JSONArray():list;
    }
    private String gestureLabel(String id){if(Gestures.ALL.containsKey(id))return Gestures.ALL.get(id);JSONArray custom=config.optJSONArray("customGestures");if(custom!=null)for(int i=0;i<custom.length();i++){JSONObject g=custom.optJSONObject(i);if(g!=null&&g.optString("id").equals(id))return g.optString("label");}return id;}
    private void configureDetector(){
        List<SequenceMatcher.Rule> rules=new ArrayList<>();
        for(String id:Gestures.ALL.keySet())if(actionsFor(id).length()>0){
            List<String> steps;
            if(id.equals("squeeze_single"))steps=Arrays.asList("pinch");else if(id.equals("squeeze_double"))steps=Arrays.asList("pinch","pinch");else steps=Arrays.asList(id.substring(6).split("_"));
            rules.add(new SequenceMatcher.Rule(id,steps,config.optInt(id.startsWith("swipe")?"swipeWindowMs":"windowMs",1000)));
        }
        JSONArray custom=config.optJSONArray("customGestures");if(custom!=null)for(int i=0;i<custom.length();i++){JSONObject g=custom.optJSONObject(i);if(g==null||actionsFor(g.optString("id")).length()==0)continue;List<String> steps=new ArrayList<>();JSONArray a=g.optJSONArray("steps");if(a!=null)for(int j=0;j<a.length();j++)steps.add(a.optString(j));rules.add(new SequenceMatcher.Rule(g.optString("id"),steps,g.optInt("gapMs",1200)));}
        if(rules.isEmpty()){rules.add(new SequenceMatcher.Rule("squeeze_single",Arrays.asList("pinch"),1000));rules.add(new SequenceMatcher.Rule("swipe_up",Arrays.asList("up"),1000));rules.add(new SequenceMatcher.Rule("swipe_down",Arrays.asList("down"),1000));}
        boolean fast=config.optBoolean("fastResponse",true);detector.configure(rules,fast,fast?300:0);
    }
    public void resetDetector() {detector.reset();}
    public void beginRecording(){cancel();resetDetector();recorded=new JSONArray();recordedGap=0;recordLast=recordDown=-1;recordRevision++;recording=true;addLog("Запись комбинации: действия временно приостановлены");}
    public void endRecording(){recording=false;resetDetector();}
    private void recordToken(String token,long time){if(recordLast>=0)recordedGap=(int)Math.max(recordedGap,time-recordLast);recordLast=time;recorded.put(token);lastSignal=token.equals("pinch")?"Сжатие":token.equals("up")?"Вверх":"Вниз";if(recorded.length()>=12)endRecording();}
    public void putAction(String gesture,int index,JSONObject action)throws JSONException{
        JSONObject next=new JSONObject(config.toString());JSONArray list=next.getJSONObject("mappings").optJSONArray(gesture);if(list==null)throw new JSONException("Сначала сохраните жест");
        if(index>=0&&index<list.length())list.put(index,action);else list.put(action);update(next);addLog("Добавлено: "+action.optString("label"));
    }
    public void cancel() {generation++; running=false;executionQueue.clear();}
    public void execute() {
        execute("squeeze_double");
    }
    public void execute(String gesture) {
        if(!actionsAllowed())return;
        if(running) {if(executionQueue.size()<8){executionQueue.addLast(gesture);addLog("Жест в очереди: "+gestureLabel(gesture));}return;}
        if(service==null) {addLog("Включите службу специальных возможностей FocusFlow"); return;}
        JSONArray actions=actionsFor(gesture);
        if(actions==null||actions.length()==0) {addLog("Добавьте хотя бы одно действие");return;}
        if(config.optBoolean("haptic",true)) {
            Vibrator v=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
            if(v!=null&&v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(35,VibrationEffect.DEFAULT_AMPLITUDE));
        }
        running=true; int ticket=++generation;
        addLog("Запуск: "+actions.length()+" действ."); step(actions,0,ticket,priorityFence.ticket());
    }
    private void step(JSONArray actions,int index,int ticket,long priorityTicket) {
        if(!actionsAllowed()||!priorityFence.valid(priorityTicket)||ticket!=generation||service==null) return;
        if(index>=actions.length()){running=false;addLog("Последовательность выполнена");String next=executionQueue.pollFirst();if(next!=null)handler.post(()->{if(ticket==generation)execute(next);});return;}
        JSONObject action=actions.optJSONObject(index);
        if("wait".equals(action.optString("type"))) {
            handler.postDelayed(()->step(actions,index+1,ticket,priorityTicket),Integer.parseInt(action.optString("value","500"))); return;
        }
        try {
            service.perform(action,ok->{
                if(ticket!=generation)return;
                if(!ok){running=false;executionQueue.clear();addLog("Не удалось: "+action.optString("label"));return;}
                addLog("✓ "+action.optString("label"));
                handler.post(()->step(actions,index+1,ticket,priorityTicket));
            });
        } catch(Exception e){running=false;addLog("Ошибка действия: "+e.getClass().getSimpleName());}
    }
    public synchronized String state() {
        try {
            JSONArray entries=new JSONArray(); for(JSONObject row:log) entries.put(row);
            return new JSONObject().put("config",config).put("accessibility",service!=null).put("bridge",bridgeConnected).put("grabbed",grabbed)
                .put("folioPriority",priorityFence.blocked()).put("sharedBridge",bridgeConnected&&bridgeProtocol>=2).put("bridgeProtocol",bridgeProtocol).put("stopped",stopRequested).put("device",device).put("bridgeError",bridgeError).put("squeezes",squeezes).put("doubles",doubles).put("running",running)
                .put("swipes",swipes).put("gestureCount",gestureCount)
                .put("configRevision",configRevision).put("recording",recording).put("recordRevision",recordRevision).put("recordedSteps",recorded).put("recordedGapMs",Math.min(3000,Math.max(500,recordedGap+200)))
                .put("localAdb",LocalAdb.get(context).state())
                .put("lastSignal",lastSignal).put("log",entries).put("model",Build.MODEL).put("android",Build.VERSION.RELEASE).toString();
        }catch(JSONException e){return "{}";}
    }
}
