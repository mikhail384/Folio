package ru.focusflow.pen;
import android.content.Context;import android.os.Handler;import java.util.*;import org.json.*;
/** Real PenRuntime, Config and gesture logic; only Android transport is substituted. */
public class SharedRuntimeTests {
 static int checks;static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Context context=new Context();static PenRuntime runtime=PenRuntime.get(context);static PenService service=new PenService();
 static void mapping(String gesture,String actions)throws Exception{JSONObject config=Config.defaults();config.put("haptic",false);config.put("nativePenOnly",false);config.getJSONObject("mappings").put(gesture,new JSONArray(actions));runtime.update(config);}
 static void foreground(boolean value){context.resolver.active=value;runtime.refreshFolioPriority();}
 static void tick(long delta){Handler.advance(Handler.now+delta);}
 public static void main(String[] args)throws Exception{
  runtime.service=service;
  mapping("swipe_up","[{type:wait,value:500},{type:home,label:Home}]");runtime.execute("swipe_up");ok(runtime.running,"wait started");
  foreground(true);ok(!runtime.running,"foreground handover cancels running sequence");foreground(false);tick(600);ok(service.performed.isEmpty(),"old wait cannot resume after leaving Folio");
  runtime.execute("swipe_up");context.resolver.active=true;tick(600);ok(service.performed.isEmpty(),"preflight blocks even before broadcast/daemon poll");ok(!runtime.running,"preflight cancels active work");foreground(false);
  runtime.execute("swipe_up");runtime.execute("swipe_up");foreground(true);foreground(false);tick(600);ok(service.performed.isEmpty(),"queued sequence was discarded");
  runtime.execute("swipe_up");tick(600);ok(service.performed.equals(Arrays.asList("home")),"fresh command works after leaving Folio");service.performed.clear();
  JSONObject c=Config.defaults();c.put("haptic",false);c.put("nativePenOnly",false);c.getJSONObject("mappings").put("squeeze_single",new JSONArray("[{type:home}]")).put("squeeze_double",new JSONArray("[{type:back}]"));runtime.update(c);
  runtime.input(189,true,Handler.now,"ADB");foreground(true);tick(400);foreground(false);runtime.input(189,false,Handler.now,"ADB");tick(400);ok(service.performed.isEmpty(),"pending single/double detector cleared at handover");
  foreground(true);runtime.execute("squeeze_double");runtime.input(189,true,Handler.now,"Android");runtime.input(PenRuntime.SWIPE_UP_CODE,true,Handler.now,"ADB");tick(400);ok(service.performed.isEmpty(),"manual, Android and bridge commands blocked in Folio");foreground(false);
  mapping("swipe_down","[{type:back}]");runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"ADB");tick(1);ok(service.performed.equals(Arrays.asList("back")),"fresh swipe delivered outside Folio");service.performed.clear();
  context.resolver.unavailable=true;runtime.execute("swipe_down");ok(service.performed.isEmpty(),"installed Folio with unavailable provider fails closed");context.resolver.unavailable=false;
  context.resolver.missing=true;runtime.execute("swipe_down");ok(service.performed.isEmpty(),"old/missing priority protocol fails closed");context.resolver.missing=false;
  context.packages.installed=false;runtime.execute("swipe_down");tick(1);ok(service.performed.equals(Arrays.asList("back")),"FocusFlow standalone remains available when Folio absent");service.performed.clear();context.packages.installed=true;
  String stored=context.getSharedPreferences("focusflow",0).getString("config","");foreground(true);foreground(false);ok(context.getSharedPreferences("focusflow",0).getString("config","").equals(stored),"priority never rewrites actions/preferences");
  c=Config.validate(new JSONObject(stored));ok(c.getJSONObject("mappings").getJSONArray("swipe_down").getJSONObject(0).getString("type").equals("back"),"user mapping retained");
  runtime.stopRequested=true;runtime.execute("swipe_down");runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"Android");tick(1);ok(service.performed.isEmpty(),"stopping FocusFlow also blocks fallback Android commands");runtime.stopRequested=false;
  mapping("swipe_down","[{type:back}]");runtime.bridgeConnected=true;
  runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"Xiaomi SDK");tick(1);ok(service.performed.isEmpty(),"SDK duplicate suppressed while global bridge owns input");
  runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"ADB");tick(1);ok(service.performed.equals(Arrays.asList("back")),"global raw swipe delivered once");service.performed.clear();runtime.bridgeConnected=false;
  runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"Xiaomi SDK");tick(1);ok(service.performed.equals(Arrays.asList("back")),"SDK fallback works without bridge");service.performed.clear();
  c=new JSONObject(runtime.config.toString());c.put("nativePenOnly",true);runtime.update(c);
  runtime.input(PenRuntime.SWIPE_DOWN_CODE,true,Handler.now,"ADB");tick(1);ok(service.performed.isEmpty(),"SDK-only mode rejects shell input");
  System.out.println("PASS: "+checks+" production FocusFlow runtime handover checks (JVM; Android transport substituted)");
 }
}
