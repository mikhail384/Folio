package ru.focusflow.pen;

import android.content.*;
import android.net.nsd.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import io.github.muntashirakon.adb.*;
import ru.folio.penbridge.AutoStartGate;
import ru.folio.penbridge.WirelessDebugging;

public final class LocalAdb {
    private static LocalAdb instance;
    public static synchronized LocalAdb get(Context context){if(instance==null)instance=new LocalAdb(context.getApplicationContext());return instance;}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timeout=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"adb-timeout");t.setDaemon(true);return t;});
    private final AutoStartGate gate=new AutoStartGate();
    private volatile Bundle automatic=new Bundle();
    private final NsdManager nsd;private final ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();
    private final HashMap<String,NsdManager.DiscoveryListener> discoveries=new HashMap<>();
    private long discoveryRetryAt;
    private boolean resolving,stopWhenIdle,restartRequested;private int discoveryEpoch,lastWifi=-1;private String connectService="";
    private volatile boolean busy,paired;private volatile int connectPort,pairingPort;
    private volatile String message="Проверяем подключение помощника…";
    private AbsAdbConnectionManager connection;
    private LocalAdb(Context context){this.context=context;nsd=(NsdManager)context.getSystemService(Context.NSD_SERVICE);paired=context.getSharedPreferences("local-adb",0).getBoolean("paired",false);}
    public JSONObject state()throws JSONException{return new JSONObject().put("busy",busy).put("paired",paired).put("message",message).put("connectPort",connectPort).put("pairingPort",pairingPort).put("automatic",automatic.getString("state","checking"));}
    private boolean helperRunning(){PenRuntime r=PenRuntime.get(context);return r.bridgeConnected&&r.bridgeProtocol>=2&&r.lastHeartbeat>0&&SystemClock.elapsedRealtime()-r.lastHeartbeat<2000;}
    /** Called only on the client's worker/Binder thread; no network or shell work here. */
    public Bundle automaticCheck(boolean force){return automaticCheck(force,()->true);}
    public Bundle automaticCheck(boolean force,java.util.function.BooleanSupplier foreground){
        final int wifi=WirelessDebugging.state(context);
        main.post(()->{if(foreground.getAsBoolean())ensureStarted(wifi,force);});
        Bundle state=new Bundle(automatic);state.putInt("autoProtocol",1);state.putInt("wireless",wifi);boolean running=helperRunning();
        if(running){Bundle folio=FolioPriority.status(context);if(folio!=null&&folio.containsKey("connected")&&(!folio.getBoolean("connected")||!folio.getBoolean("shared"))){running=false;if(!busy)state.putString("state","folio_disconnected");}}
        state.putBoolean("running",running);return state;
    }
    private void ensureStarted(int wifi,boolean force){
        if(stopWhenIdle)gate.retry();stopWhenIdle=false;if(force){restartRequested=true;gate.retry();if(!busy)stopDiscovery();}
        if(lastWifi!=wifi){if(wifi==0)stopDiscovery();else if(lastWifi==0)gate.retry();lastWifi=wifi;}
        boolean running=helperRunning()&&!restartRequested;
        if(!running&&wifi!=0&&Build.VERSION.SDK_INT>=30)discoverMain();
        AutoStartGate.Decision decision=gate.check(SystemClock.elapsedRealtime(),running,wifi,paired,connectPort,busy,Build.VERSION.SDK_INT>=30);
        Bundle state=new Bundle();state.putString("state",decision.state);state.putInt("autoProtocol",1);automatic=state;
        if(decision.launch)startMain(connectPort,false,restartRequested);
    }
    public void pauseAutomaticDiscovery(){main.post(()->{stopWhenIdle=true;if(!busy)stopDiscovery();});}
    public void discover(){main.post(this::discoverMain);}
    private void discoverMain(){
        if(Build.VERSION.SDK_INT<30){message="Нужен Android 11 или новее";return;}
        if(nsd==null||SystemClock.elapsedRealtime()<discoveryRetryAt)return;
        final int epoch=discoveryEpoch;
        for(String type:new String[]{"_adb-tls-connect._tcp.","_adb-tls-pairing._tcp."}){
            if(discoveries.containsKey(type))continue;
            NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
                public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}
                public void onServiceFound(NsdServiceInfo info){main.post(()->{if(epoch!=discoveryEpoch)return;queue.add(info);resolveNext();});}
                public void onServiceLost(NsdServiceInfo info){main.post(()->{if(epoch==discoveryEpoch&&!info.getServiceType().contains("pairing")&&info.getServiceName().equals(connectService)){connectPort=0;connectService="";}});}
                public void onStartDiscoveryFailed(String t,int code){main.post(()->{if(epoch!=discoveryEpoch)return;discoveries.remove(type);discoveryRetryAt=SystemClock.elapsedRealtime()+5000;message="Автопоиск недоступен. Порт можно ввести вручную.";});}
                public void onStopDiscoveryFailed(String t,int code){}
            };
            try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);discoveries.put(type,listener);}catch(Exception e){discoveryRetryAt=SystemClock.elapsedRealtime()+5000;message="Введите порты из настроек отладки вручную";}
        }
    }
    private void stopDiscovery(){
        discoveryEpoch++;discoveryRetryAt=0;for(NsdManager.DiscoveryListener l:discoveries.values())try{nsd.stopServiceDiscovery(l);}catch(Exception ignored){}
        discoveries.clear();queue.clear();resolving=false;connectPort=pairingPort=0;connectService="";
    }
    public void refresh(){main.post(()->{gate.retry();stopDiscovery();discoverMain();});}
    private void resolveNext(){
        if(resolving||queue.isEmpty())return;resolving=true;NsdServiceInfo service=queue.removeFirst();final int epoch=discoveryEpoch;
        try{nsd.resolveService(service,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo info,int code){main.post(()->{if(epoch!=discoveryEpoch)return;resolving=false;resolveNext();});}
            public void onServiceResolved(NsdServiceInfo info){main.post(()->{
                if(epoch!=discoveryEpoch)return;
                try{InetAddress host=info.getHost();if(host!=null&&(host.isLoopbackAddress()||NetworkInterface.getByInetAddress(host)!=null)&&info.getPort()>0&&info.getPort()<=65535){
                    if(info.getServiceType().contains("pairing"))pairingPort=info.getPort();else{connectPort=info.getPort();connectService=info.getServiceName();}
                    if(!busy)message=paired?"Планшет найден. Помощник запускается автоматически при открытии приложения.":"Планшет найден. Введите код из окна сопряжения.";
                }}catch(Exception ignored){}resolving=false;resolveNext();
            });}
        });}catch(Exception e){resolving=false;resolveNext();}
    }
    private AbsAdbConnectionManager manager()throws Exception{
        if(connection==null){AdbIdentity identity=new AdbIdentity(context.getFilesDir());connection=new AbsAdbConnectionManager(){
            protected PrivateKey getPrivateKey(){return identity.key;}
            protected Certificate getCertificate(){return identity.certificate;}
            protected String getDeviceName(){return "FocusFlow";}
        };connection.setApi(Build.VERSION.SDK_INT);connection.setHostAddress("127.0.0.1");connection.setTimeout(10,TimeUnit.SECONDS);}
        return connection;
    }
    public void pair(int port,String code){main.post(()->{
        if(busy)return;if(port<1||port>65535||code==null||!code.matches("[0-9]{6}")){message="Нужны порт сопряжения и шестизначный код";return;}
        busy=true;message="Выполняем сопряжение с этим планшетом…";
        worker.execute(()->{boolean ok=false;try{
            if(!manager().pair("127.0.0.1",port,code))throw new IOException("Pairing failed");
            paired=true;context.getSharedPreferences("local-adb",0).edit().putBoolean("paired",true).apply();ok=true;message="Сопряжение выполнено. Закройте окно кода: помощник запустится автоматически.";
        }catch(Throwable e){message="Сопряжение не выполнено. Откройте новый код и проверьте порт: "+brief(e);}
        finally{final boolean pairedOk=ok;main.post(()->{busy=false;if(pairedOk)gate.retry();if(stopWhenIdle)stopDiscovery();});}});
    });}
    public void start(int requestedPort){main.post(()->startMain(requestedPort,true,false));}
    private void startMain(int requestedPort,boolean resumeFocusFlow,boolean restart){
        if(busy)return;
        PenRuntime runtime=PenRuntime.get(context);
        if(resumeFocusFlow)runtime.stopRequested=false;
        if(helperRunning()&&!restart){message="Общий помощник уже работает для Folio и FocusFlow.";gate.success();return;}
        int port=requestedPort>0?requestedPort:connectPort;
        if(port<1||port>65535){message="Ищем порт подключения. Включите беспроводную отладку.";discoverMain();return;}
        busy=true;message="Подключаемся к локальной службе Android…";
        worker.execute(()->{String phase="connection";boolean success=false;try{
            AbsAdbConnectionManager adb=manager();if(!adb.isConnected()&&!adb.connect("127.0.0.1",port))throw new IOException("Connection failed");
            runtime.bridgeConnected=false;runtime.grabbed=false;runtime.handler.post(runtime::resetDetector);
            String command=ru.folio.penbridge.BridgeLaunch.command(context.getApplicationInfo().sourceDir,restart);
            phase="launch";String result;
            try(AdbStream stream=adb.openStream("exec:"+command)){
                ScheduledFuture<?> deadline=timeout.schedule(()->{try{stream.close();}catch(Exception ignored){}},15,TimeUnit.SECONDS);
                try(InputStream input=stream.openInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[4096];
                    while(true){int n;try{n=input.read(b);}catch(IOException e){if(stream.isClosed())break;throw e;}if(n<0)break;out.write(b,0,n);if(out.size()>32768)break;}
                    result=out.toString("UTF-8");
                }finally{deadline.cancel(false);}
            }
            if(!result.contains("FOLIO_FOCUSFLOW_STARTED"))throw new IOException("Shared helper process not confirmed");
            phase="heartbeat";for(int i=0;i<25;i++){Thread.sleep(200);if(helperRunning())break;}
            if(!helperRunning())throw new IOException("Shared helper heartbeat not confirmed");
            success=true;message="Общий помощник работает для Folio и FocusFlow. В открытом Folio его команды имеют приоритет.";
        }catch(Throwable e){android.util.Log.w("FocusFlowAdb","Start failed at "+phase,e);message="Не удалось подключить помощник. Проверьте беспроводную отладку и сопряжение.";}
        finally{
            try{if(connection!=null)connection.disconnect();}catch(Exception ignored){}
            final boolean connected=success;main.post(()->{busy=false;if(connected){restartRequested=false;gate.success();}else gate.failure(SystemClock.elapsedRealtime());if(stopWhenIdle)stopDiscovery();});
        }});
    }
    public void stop(){PenRuntime runtime=PenRuntime.get(context);runtime.stopRequested=true;runtime.handler.post(()->{runtime.cancel();runtime.resetDetector();});message="Команды FocusFlow остановлены. Общий помощник продолжает работать для Folio.";}
    private static String brief(Throwable error){Throwable e=error;while(e.getCause()!=null)e=e.getCause();return e.getClass().getSimpleName();}
}
