package ru.folio.notes;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.File;
import java.util.ArrayList;
import org.json.*;

/** Bounded, restartable foreground processing of only individually opted-in notebooks. */
public final class AiLearningService extends Service {
    static final String ENABLED="ai_auto_enabled",STATUS="ai_learning_status",STOP="ru.folio.notes.STOP_AI_LEARNING";
    private static final String CHANNEL="handwriting_analysis";
    private static final int NOTIFICATION=6142;
    private static final Object PIPELINE_LOCK=new Object();
    private static volatile AiLearningService instance;
    private final Object signal=new Object();
    private final AiTrainingUpload upload=new AiTrainingUpload();
    private volatile AiOcrAnalyzer analyzer;
    private volatile boolean running;
    private volatile long generation;
    private volatile int latestStart;
    private Thread worker;
    private SharedPreferences prefs;
    private NotebookStore store;
    private AiLearningQueue queue;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{
        if(key==null)return;
        if(key.startsWith("ai_learning_doc_")||key.startsWith("trash_")||key.equals(ENABLED)||key.equals("agent_enabled")||key.equals("agent_cloud")){
            synchronized(signal){generation++;signal.notifyAll();}
            // Cancel an in-flight upload on revocation or a change of destination.
            upload.stop();
            if(!p.getBoolean(ENABLED,true)){cancel();stopSelf();}
        }
    };

    static void enableFromUser(MainActivity a){
        a.prefs.edit().putBoolean(ENABLED,true).apply();
        if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!a.prefs.getBoolean("ai_notice_asked",false)){
            a.prefs.edit().putBoolean("ai_notice_asked",true).apply();a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},6142);
        }
        kick(a);
    }
    static void kick(MainActivity a){
        if(!a.resumed||!a.prefs.getBoolean(ENABLED,true)||!AiLearningPermissions.anyAllowed(a.prefs))return;
        start(a);
    }
    static void cleanup(MainActivity a){if(a.resumed&&a.prefs.getBoolean(ENABLED,true))start(a);}
    private static void start(MainActivity a){
        try{a.startForegroundService(new Intent(a,AiLearningService.class));}
        catch(RuntimeException unavailable){a.prefs.edit().putString(STATUS,"Анализ продолжится при следующем открытии Folio").apply();android.util.Log.w("Folio","Cannot start handwriting analysis",unavailable);}
    }
    static void saved(){AiLearningService current=instance;if(current!=null)synchronized(current.signal){current.generation++;current.signal.notifyAll();}}

    @Override public void onCreate(){
        super.onCreate();prefs=getSharedPreferences("folio",MODE_PRIVATE);store=new NotebookStore(getApplicationContext());queue=new AiLearningQueue(new File(getFilesDir(),"ai-learning-queue.json"));
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Анализ почерка",NotificationManager.IMPORTANCE_LOW));
        instance=this;running=true;prefs.registerOnSharedPreferenceChangeListener(listener);
        try{if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification("Подготавливаем разрешённые тетради"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTIFICATION,notification("Подготавливаем разрешённые тетради"));}
        catch(RuntimeException unavailable){prefs.edit().putString(STATUS,"Анализ продолжится при следующем открытии Folio").apply();running=false;stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        latestStart=startId;
        if(!running){stopSelf(startId);return START_NOT_STICKY;}
        if(intent!=null&&STOP.equals(intent.getAction())){prefs.edit().putBoolean(ENABLED,false).putString(STATUS,"Анализ приостановлен").apply();cancel();stopSelf();return START_NOT_STICKY;}
        synchronized(signal){generation++;signal.notifyAll();if(worker==null){worker=new Thread(()->{synchronized(PIPELINE_LOCK){work();}},"folio-handwriting-analysis");worker.start();}}
        return START_NOT_STICKY;
    }
    private boolean active(){return running&&prefs.getBoolean(ENABLED,true);}
    private boolean allowed(String doc,long pass){return active()&&generation==pass&&AiLearningPermissions.allowed(prefs,doc);}
    private Notification notification(String status){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,AiLearningService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("Folio · анализ почерка").setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"Приостановить",pause).build()).build();
    }
    private void status(String value){prefs.edit().putString(STATUS,value).apply();if(running)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION,notification(value));}
    private JSONObject config(){if(!prefs.getBoolean("agent_enabled",false))return null;try{return AgentCloud.validate(prefs.getString("agent_cloud",""));}catch(Exception ignored){return null;}}
    private String target(JSONObject config)throws Exception{return config==null?"":config.getString("url")+"|"+config.getString("room");}
    private boolean flush(JSONObject config,long pass)throws Exception{
        if(config==null)return true;String destination=target(config);JSONArray pending=queue.pendingFor(destination);
        for(int i=0;i<pending.length();i++){
            JSONObject item=pending.getJSONObject(i);String doc=item.getString("document_id");if(!allowed(doc,pass))continue;
            JSONObject now=config();if(now==null||!destination.equals(target(now)))return false;
            status("Сохраняем образцы в вашем облачном профиле");
            try{upload.send(now,item);}catch(Exception offline){if(!active())return false;status("Образцы на планшете · ожидают связи");return false;}
            if(allowed(doc,pass)&&destination.equals(target(config())))queue.acknowledge(item.getString("id"));else return false;
        }return true;
    }
    private void scan(long pass)throws Exception{
        if(!active())return;
        queue.purgeRevoked(id->AiLearningPermissions.allowed(prefs,id));
        JSONObject cloud=config();flush(cloud,pass);
        ArrayList<String> docs=store.learningIds(prefs);int total=0;
        for(String id:docs){
            Ink.Doc doc=store.loadForLearning(id,prefs);if(doc==null)continue;
            if(!allowed(doc.id,pass))return;
            for(Ink.Page page:doc.pages){
                if(!allowed(doc.id,pass))return;String fingerprint=AiLearningCore.pageFingerprint(page);if(queue.analyzed(doc.id,page.id,fingerprint))continue;
                status("Анализируем разрешённые тетради · страниц: "+(++total));
                JSONArray samples=new JSONArray();
                if(!AiLearningCore.tiles(page).isEmpty()){
                    if(analyzer==null)analyzer=new AiOcrAnalyzer(getApplicationContext());
                    samples=analyzer.analyze(doc,page,()->allowed(doc.id,pass));
                }
                if(!allowed(doc.id,pass))return;
                cloud=config();boolean complete=queue.record(doc.id,page.id,fingerprint,samples,target(cloud));
                if(!complete){if(!flush(cloud,pass)){status("Очередь образцов заполнена · ожидаем связи");return;}if(!queue.record(doc.id,page.id,fingerprint,samples,target(cloud))){status("Очередь образцов заполнена · ожидаем связи или прежнего аккаунта");return;}}
            }
        }
        if(active()&&generation==pass){cloud=config();if(flush(cloud,pass))status(queue.size()>0?"Образцы на планшете · подключите облако":"Разрешённые тетради проверены");}
    }
    private void work(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        PowerManager.WakeLock wake=null;
        try{
            PowerManager manager=(PowerManager)getSystemService(POWER_SERVICE);wake=manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"folio:handwriting-analysis");
            while(active()){
                long pass; synchronized(signal){signal.wait(1200);pass=generation;}
                if(!active())break;if(!wake.isHeld())wake.acquire(10*60*1000L);
                try{scan(pass);}catch(InterruptedException interrupted){if(!active())break;}catch(Exception|LinkageError|OutOfMemoryError failure){android.util.Log.e("Folio","Handwriting analysis failed",failure);status("Не удалось продолжить анализ · откройте Folio снова");}
                if(wake.isHeld())wake.release();
                synchronized(signal){if(!active())break;signal.wait(3500);if(pass==generation){stopSelfResult(latestStart);break;}}
            }
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        finally{if(wake!=null&&wake.isHeld())wake.release();AiOcrAnalyzer a=analyzer;analyzer=null;if(a!=null)a.close();upload.stop();store.io.shutdown();}
    }
    private void cancel(){running=false;AiOcrAnalyzer a=analyzer;if(a!=null)a.stop();upload.stop();if(worker!=null)worker.interrupt();synchronized(signal){signal.notifyAll();}}
    @Override public void onTimeout(int startId,int fgsType){prefs.edit().putString(STATUS,"Анализ продолжится после открытия Folio").apply();cancel();stopSelf(startId);}
    @Override public void onDestroy(){cancel();prefs.unregisterOnSharedPreferenceChangeListener(listener);if(instance==this)instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
