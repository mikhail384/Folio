package ru.folio.notes;

/** Exercise both temporal double-tap recognition and the production feedback gate. */
public final class PenFeedbackTests {
    static int assertions;static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        PenHapticPulse pulse=new PenHapticPulse();
        check(!pulse.accept(1000,false,true),"no background feedback");check(!pulse.accept(1000,true,false),"disabled feedback");check(pulse.accept(1000,true,true),"one double tap requests feedback");check(!pulse.accept(1000,true,true),"duplicate event ignored");check(!pulse.accept(1100,true,true),"repeat noise ignored");check(pulse.accept(1400,true,true),"next real double tap allowed");
        final int[] singles={0},doubles={0},feedback={0};final long[] now={2000};
        PenGestures.Actions actions=new PenGestures.Actions(){public void single(){singles[0]++;}public void doubleTap(){doubles[0]++;if(pulse.accept(now[0],true,true))feedback[0]++;}};
        PenGestures gestures=new PenGestures(300);gestures.tap(2000,actions);check(feedback[0]==0,"single tap has no double-tap feedback");now[0]=2200;gestures.tap(2200,actions);gestures.flush(3000,actions);check(doubles[0]==1&&feedback[0]==1&&singles[0]==0,"paired taps execute and pulse once");gestures.tap(3400,actions);gestures.flush(3750,actions);check(singles[0]==1&&feedback[0]==1,"single execution does not pulse");gestures.tap(4000,actions);gestures.cancel();gestures.flush(5000,actions);check(feedback[0]==1,"pause cancels pending gesture");
        check(PenHapticPulse.COMMAND==3002&&PenHapticPulse.DOUBLE_TAP==3&&PenHapticPulse.AMPLITUDE==128,"observed Xiaomi motor message");
        System.out.println("PASS "+assertions+" pen feedback assertions: gesture arbitration, one pulse, settings/background gating and Xiaomi command. Physical vibration not verified.");
    }
}
