package ru.focusflow.pen;
import android.os.*;import android.content.*;
public class FocusProviderHealthTests {
 static int checks;static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args){Handler.now=1000;PenProvider p=new PenProvider();p.onCreate();PenRuntime runtime=PenRuntime.get(ContentProvider.context);
  p.call("control","grabbed",null);Handler.advance(Handler.now);ok(runtime.bridgeConnected&&runtime.bridgeProtocol==0,"legacy control remains connected but unversioned");ok(!new org.json.JSONObject(runtime.state()).getBoolean("sharedBridge"),"legacy heartbeat never claims shared status");ok(p.call("diagnostics",null,null).getInt("sharedProtocol",-1)==0,"diagnostics report received protocol instead of a constant");
  Bundle extra=new Bundle();extra.putInt("sharedProtocol",2);p.call("control","observe",extra);Handler.advance(Handler.now);ok(runtime.bridgeProtocol==2&&new org.json.JSONObject(runtime.state()).getBoolean("sharedBridge"),"shared control publishes real protocol");ok(p.call("diagnostics",null,null).getInt("sharedProtocol",-1)==2,"shared diagnostics agree");
  p.call("control","observe",null);Handler.advance(Handler.now);ok(runtime.bridgeProtocol==0,"later legacy traffic cannot inherit shared identity");
  Binder.uid=55555;boolean denied=false;try{p.call("control","grabbed",extra);}catch(SecurityException expected){denied=true;}ok(denied,"foreign callers cannot forge bridge protocol");
  System.out.println("PASS: "+checks+" production FocusFlow provider legacy/shared protocol and UID checks (JVM transport)");
 }
}
