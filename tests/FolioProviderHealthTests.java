package ru.folio.notes;
import android.os.*;
public class FolioProviderHealthTests {
 static int checks;static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 public static void main(String[] args){Handler.now=1000;PenProvider p=new PenProvider();MainActivity activity=new MainActivity();PenProvider.activity=new java.lang.ref.WeakReference<>(activity);
  Binder.uid=20000;Bundle first=p.call("priority",null,null);ok(first.getBoolean("active")&&!first.getBoolean("connected"),"foreground priority does not imply bridge connection");
  Binder.uid=2000;Bundle extra=new Bundle();extra.putInt("sharedProtocol",2);p.call("control","grabbed",extra);
  Binder.uid=20000;Bundle shared=p.call("priority",null,null);ok(shared.getBoolean("connected")&&shared.getBoolean("shared"),"FocusFlow can read actual Folio heartbeat");
  boolean denied=false;try{p.call("events","1000,189,1",null);}catch(SecurityException expected){denied=true;}ok(denied&&activity.events==0,"read-only companion cannot inject shell gestures");
  Binder.uid=2000;p.call("events","1000,189,1",null);ok(activity.events==1,"shell events reach active Folio");
  Handler.now=4000;Binder.uid=20000;ok(!p.call("priority",null,null).getBoolean("connected"),"status reads never refresh an expired helper heartbeat");
  Binder.uid=55555;denied=false;try{p.call("priority",null,null);}catch(SecurityException expected){denied=true;}ok(denied,"unrelated apps cannot read priority/bridge status");
  System.out.println("PASS: "+checks+" production Folio provider heartbeat, delivery and read-only UID checks (JVM transport)");
 }
}
