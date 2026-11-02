package ru.folio.penbridge;
public class AutoStartGateTests {
 static int checks;static void ok(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 public static void main(String[] args){
  AutoStartGate gate=new AutoStartGate();
  ok(gate.check(1,true,0,false,0,false,true).state.equals("ready"),"existing helper needs no wireless launch or pairing");
  ok(gate.check(1,false,0,true,5500,false,true).state.equals("wifi_off"),"disabled setting wins over stale port");
  ok(gate.check(1,false,1,false,5500,false,true).state.equals("pairing"),"first pairing is required");
  ok(gate.check(1,false,-1,true,0,false,true).state.equals("searching"),"unknown setting is not reported disabled");
  ok(gate.check(8001,false,1,true,0,false,true).state.equals("port_missing"),"discovery timeout distinguished from disabled setting");
  AutoStartGate.Decision attempt=gate.check(8002,false,1,true,38555,false,true);ok(attempt.launch,"new advertised port starts helper");
  ok(!gate.check(8003,false,1,true,38555,true,true).launch,"concurrent clients do not start a second attempt");
  gate.failure(8100);ok(!gate.check(8101,false,1,true,38555,false,true).launch,"failed launch backs off");
  ok(gate.check(18100,false,1,true,38555,false,true).launch,"retry after cooldown");
  gate.retry();ok(gate.check(18101,false,1,true,38555,false,true).launch,"explicit retry resets cooldown");
  ok(gate.check(18102,true,1,true,38555,true,true).state.equals("ready"),"heartbeat is authoritative");
  ok(gate.check(18103,false,1,true,0,false,false).state.equals("unsupported"),"legacy Android explained");
  System.out.println("PASS: "+checks+" automatic startup policy checks");
 }
}
