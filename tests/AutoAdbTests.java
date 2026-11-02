package ru.focusflow.pen;
import android.content.*;import android.app.Activity;import android.os.*;import android.provider.Settings;import io.github.muntashirakon.adb.AbsAdbConnectionManager;import java.util.concurrent.*;import ru.folio.penbridge.WirelessDebugging;
/** Real LocalAdb, gate, runtime, identity and settings navigator; only platform/network replaced. */
public class AutoAdbTests {
 static int checks;static Context context=new Context();static LocalAdb adb;static PenRuntime runtime;
 static void ok(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 static void pump(){Handler.advance(Handler.now);}
 static Bundle check(){adb.automaticCheck(false);pump();Bundle result=adb.automaticCheck(false);pump();return result;}
 static void idle()throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(adb.state().getBoolean("busy")){pump();if(System.nanoTime()>until)throw new AssertionError("worker did not finish");Thread.sleep(10);}pump();}
 static void dead(){runtime.bridgeConnected=false;runtime.lastHeartbeat=0;}
 static void shutdown()throws Exception{for(String name:new String[]{"worker","timeout"}){java.lang.reflect.Field field=LocalAdb.class.getDeclaredField(name);field.setAccessible(true);((ExecutorService)field.get(adb)).shutdownNow();}}
 public static void main(String[] args)throws Exception{
  Handler.now=2000;runtime=PenRuntime.get(context);adb=LocalAdb.get(context);AbsAdbConnectionManager.heartbeat=()->{runtime.lastHeartbeat=Handler.now;runtime.bridgeProtocol=2;runtime.bridgeConnected=true;};
  try{
   runtime.bridgeConnected=true;runtime.lastHeartbeat=Handler.now;Bundle legacy=adb.automaticCheck(false,()->false);pump();ok(!legacy.getBoolean("running"),"legacy FocusFlow-only heartbeat must not count as the shared Folio helper");dead();
   Settings.Global.wifi=0;ok(check().getString("state").equals("wifi_off"),"disabled wireless setting reported");ok(AbsAdbConnectionManager.connects==0&&context.nsd.active.isEmpty(),"disabled wireless does not connect or discover");
   Settings.Global.wifi=1;adb.automaticCheck(false,()->false);pump();ok(context.nsd.active.isEmpty(),"queued check cannot start discovery after the last visible client left");
   Settings.Global.wifi=1;ok(check().getString("state").equals("pairing"),"new install requests pairing without attempting unauthorized launch");
   adb.pair(37500,"123456");pump();idle();ok(context.getSharedPreferences("local-adb",0).getBoolean("paired",false),"pairing saved under the original preference key");
   runtime.stopRequested=true;check();idle();Bundle ready=check();ok(ready.getBoolean("running")&&ready.getString("state").equals("ready"),"automatic launch requires actual heartbeat");ok(AbsAdbConnectionManager.launches==1,"one helper launch");ok(runtime.stopRequested,"Folio automatic launch does not reenable stopped FocusFlow commands");
   int count=AbsAdbConnectionManager.connects;for(int i=0;i<5;i++)check();ok(AbsAdbConnectionManager.connects==count,"repeated foreground checks do not reconnect or relaunch");
   runtime.bridgeProtocol=0;check();idle();ok(AbsAdbConnectionManager.connects==count+1&&runtime.bridgeProtocol==2,"live legacy helper is migrated instead of suppressing shared startup");count=AbsAdbConnectionManager.connects;
   context.resolver.bridgeKnown=true;context.resolver.bridgeConnected=false;context.resolver.bridgeShared=false;Bundle disconnected=check();ok(!disconnected.getBoolean("running")&&disconnected.getString("state").equals("folio_disconnected"),"FocusFlow heartbeat alone cannot claim Folio is connected");
   context.resolver.bridgeConnected=true;context.resolver.bridgeShared=true;ok(check().getBoolean("running"),"both provider connections establish shared readiness");
   adb.automaticCheck(true);pump();check();idle();ok(AbsAdbConnectionManager.connects==count+1&&AbsAdbConnectionManager.lastCommand.contains("'$args' = 'folio-pen-shared'".replace("'$args'","\"$args\"")),"retry explicitly replaces a named shell helper after a lost Folio connection");count=AbsAdbConnectionManager.connects;context.resolver.bridgeKnown=false;
   adb.start(0);pump();ok(!runtime.stopRequested&&AbsAdbConnectionManager.connects==count,"manual button resumes FocusFlow without relaunching the healthy helper");
   Settings.Global.wifi=0;ok(check().getInt("wireless",-1)==0&&check().getBoolean("running"),"disabled Wi-Fi notice can coexist with an already running helper");ok(AbsAdbConnectionManager.connects==count,"Wi-Fi toggle never kills working helper");
   dead();Settings.Global.wifi=-1;Settings.Global.denied=true;context.nsd.advertise=false;Bundle unknown=check();ok(unknown.getInt("wireless",7)==-1&&!unknown.getString("state").equals("wifi_off"),"restricted settings are not falsely reported as disabled");Settings.Global.denied=false;
   Handler.now+=9000;ok(check().getString("state").equals("port_missing"),"no advertised port distinguished from disabled Wi-Fi");
   adb.pauseAutomaticDiscovery();pump();ok(check().getString("state").equals("searching"),"returning to the app starts a fresh discovery grace period");
   Settings.Global.wifi=1;context.nsd.advertise=true;context.nsd.holdResolve=true;adb.refresh();pump();ok(!context.nsd.held.isEmpty(),"resolution is in flight");adb.pauseAutomaticDiscovery();pump();for(Runnable stale:context.nsd.held)stale.run();context.nsd.held.clear();pump();ok(adb.state().getInt("connectPort")==0,"stale resolution cannot resurrect a port after pause");
   context.nsd.holdResolve=false;context.nsd.port=38977;AbsAdbConnectionManager.failConnect=true;adb.refresh();pump();check();idle();int failedCount=AbsAdbConnectionManager.connects;ok(check().getString("state").equals("error"),"failed authentication/connection remains an error");check();ok(AbsAdbConnectionManager.connects==failedCount,"no rapid retry storm");
   AbsAdbConnectionManager.failConnect=false;AbsAdbConnectionManager.noHeartbeat=true;adb.automaticCheck(true);pump();check();idle();ok(!check().getBoolean("running")&&check().getString("state").equals("error"),"shell marker without heartbeat is not success");
   AbsAdbConnectionManager.noHeartbeat=false;AbsAdbConnectionManager.entered=new CountDownLatch(1);AbsAdbConnectionManager.release=new CountDownLatch(1);adb.automaticCheck(true);pump();check();ok(AbsAdbConnectionManager.entered.await(2,TimeUnit.SECONDS),"connection started on worker");adb.pauseAutomaticDiscovery();pump();AbsAdbConnectionManager.release.countDown();idle();ok(context.nsd.active.isEmpty(),"discovery stops after an in-flight start finishes in background");ok(check().getBoolean("running"),"healthy helper available after returning");
   Activity screen=new Activity();WirelessDebugging.settings(screen);ok(screen.opened.get(0).equals("android.settings.WIRELESS_DEBUGGING_SETTINGS"),"notice opens wireless settings directly");screen=new Activity();screen.reject=2;WirelessDebugging.settings(screen);ok(screen.opened.size()==3&&screen.opened.get(2).equals("settings"),"settings button falls back on OEM firmware");
   System.out.println("PASS: "+checks+" production LocalAdb startup/heartbeat/pairing/port/settings checks (JVM; Android and ADB transport substituted)");
  }finally{shutdown();}
 }
}
