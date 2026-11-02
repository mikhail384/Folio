package ru.folio.notes;

public final class CommandTimingTests {
    static int checks;
    static void check(boolean yes,String text){if(!yes)throw new AssertionError(text);checks++;}
    public static void main(String[] args){
        long wall=1792600000000L,up=6512300L;
        check(EventTiming.uptime(wall-80,up,wall)==up-80,"wall clock converted without adding IPC delay");
        check(EventTiming.uptime(up-80,up,wall)==up-80,"native monotonic event unchanged");
        check(EventTiming.remaining(up-80,350,up)==270,"double window subtracts transit time");
        check(EventTiming.remaining(up-600,500,up)==0,"delayed hold resolves immediately");
        check(EventTiming.remaining(up+600,350,up)==350,"future clock cannot create an unbounded delay");
        int[] singles={0},doubles={0};PenGestures.Actions action=new PenGestures.Actions(){public void single(){singles[0]++;}public void doubleTap(){doubles[0]++;}};
        PenGestures tap=new PenGestures(300);tap.tap(up,action);check(tap.deadline()==up+301,"one exact single deadline");tap.flush(up+300,action);check(singles[0]==0,"inclusive double window preserved");tap.tap(up+300,action);check(doubles[0]==1&&tap.deadline()==-1,"double cancels pending single");tap.flush(up+900,action);check(singles[0]==0,"double never emits single later");tap.tap(up+1000,action);tap.flush(up+1301,action);check(singles[0]==1,"single resolves once at exact deadline");
        float[] result=new float[4];int n=0;for(int hz:new int[]{60,90,120,144}){SmoothSpring spring=new SmoothSpring(0,22);spring.target(1);for(int i=0;i<hz;i++)spring.step(1f/hz);check(Math.abs(spring.value-1)<.0005f,"spring settles across refresh rates");result[n++]=spring.value;}
        SmoothSpring stalled=new SmoothSpring(0,22);stalled.target(1);stalled.step(.5f);check(stalled.value>.999f,"stalled frame catches up without a slow tail");stalled.target(0);float velocity=stalled.velocity;stalled.step(Float.NaN);check(Float.isFinite(stalled.value)&&stalled.velocity==velocity,"invalid dt leaves spring finite");float before=stalled.value;stalled.target(.4f);check(stalled.value==before&&stalled.velocity==velocity,"retarget preserves position and velocity");
        System.out.println("PASS "+checks+" event-clock, command deadline and refresh-rate checks");
    }
}
