import ru.focusflow.pen.PenGestureDetector;
import java.util.*;
public class GestureTest {
    static class Clock implements PenGestureDetector.Scheduler {
        long now;List<Task> tasks=new ArrayList<>();
        static class Task {long at;Runnable run;boolean cancelled;}
        public PenGestureDetector.Ticket after(long delay,Runnable run){Task t=new Task();t.at=now+delay;t.run=run;tasks.add(t);return ()->t.cancelled=true;}
        void advance(long target){while(true){Task next=null;for(Task t:tasks)if(!t.cancelled&&t.at<=target&&(next==null||t.at<next.at))next=t;if(next==null)break;tasks.remove(next);now=next.at;next.run.run();}now=target;}
    }
    static int checks;
    static void equal(Object actual,Object expected){checks++;if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        Clock c=new Clock();List<String> seen=new ArrayList<>();PenGestureDetector d=new PenGestureDetector(c,seen::add);
        c.now=1000;d.pinch(true,1000,550);c.now=1100;d.pinch(false,1100,550);c.advance(1649);equal(seen.size(),0);c.advance(1650);equal(seen,Arrays.asList("squeeze_single"));
        seen.clear();d.reset();c.now=2000;d.pinch(true,2000,550);c.now=2100;d.pinch(false,2100,550);c.advance(2200);d.pinch(true,2200,550);c.now=2300;d.pinch(false,2300,550);c.advance(3000);equal(seen,Arrays.asList("squeeze_double"));
        for(String first:new String[]{"up","down"})for(String second:new String[]{"up","down"}){
            seen.clear();d.reset();c.now+=1000;long time=c.now;d.swipe(first,time,450);c.advance(time+200);d.swipe(second,time+200,450);c.advance(time+900);equal(seen,Arrays.asList("swipe_"+first+"_"+second));
        }
        for(String direction:new String[]{"up","down"}){seen.clear();d.reset();long time=c.now+1000;c.now=time;d.swipe(direction,time,450);c.advance(time+450);equal(seen,Arrays.asList("swipe_"+direction));}
        seen.clear();d.reset();c.now+=1000;long t=c.now;d.pinch(true,t,550);d.pinch(true,t+10,550);d.pinch(false,t+100,550);c.advance(t+650);equal(seen,Arrays.asList("squeeze_single"));
        seen.clear();d.reset();c.now+=1000;t=c.now;d.pinch(true,t,550);d.pinch(false,t+1100,550);c.advance(t+2000);equal(seen.size(),0);
        seen.clear();d.reset();c.now+=1000;t=c.now;d.swipe("up",t,450);d.reset();c.advance(t+1000);equal(seen.size(),0);
        seen.clear();d.reset();c.now+=1000;t=c.now;d.swipe("up",t,450);d.swipe("down",t+700,450);c.advance(c.now+450);equal(seen,Arrays.asList("swipe_up","swipe_down"));
        System.out.println("PASS: "+checks+" gesture assertions");
        // Real regression: combinations spanning different primitives, prefix priority,
        // and delivered-in-one-batch signals with original hardware timestamps.
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("single",Arrays.asList("up"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("pair",Arrays.asList("up","down"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("triple",Arrays.asList("up","down","pinch"),1000)));
        c.now+=2000;t=c.now;d.swipe("up",t,1000);c.advance(t+700);equal(seen.size(),0);d.swipe("down",t+700,1000);c.advance(t+1400);equal(seen.size(),0);d.pinch(true,t+1400,1000);d.pinch(false,t+1500,1000);equal(seen,Arrays.asList("triple"));c.advance(t+3000);equal(seen.size(),1);
        seen.clear();d.reset();c.now+=1000;t=c.now;d.swipe("up",t,1000);d.swipe("down",t+800,1000);c.advance(t+1000);equal(seen,Arrays.asList("pair"));
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("triple",Arrays.asList("pinch","pinch","pinch"),1200)));
        c.now+=2000;t=c.now;for(int i=0;i<3;i++){d.pinch(true,t+i*250,1200);d.pinch(false,t+i*250+100,1200);}equal(seen,Arrays.asList("triple"));
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("only-single",Arrays.asList("up"),1000)));d.swipe("up",t+3000,1000);equal(seen,Arrays.asList("only-single"));
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("pair",Arrays.asList("up","down"),1000)));d.swipe("up",t+4000,1000);d.swipe("down",t+5500,1000);c.advance(c.now+2000);equal(seen.size(),0);
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("up",Arrays.asList("up"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("down",Arrays.asList("down"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("long",Arrays.asList("up","pinch","down"),1000)));d.swipe("up",t+6000,1000);d.swipe("down",t+6100,1000);equal(seen,Arrays.asList("up","down"));
        System.out.println("PASS: "+checks+" total gesture assertions including mixed/triple sequences and delayed batches");
        // Fast response must fire on DOWN, suppress repeats/UP, and still choose
        // the longer configured gesture without firing its shorter prefix too.
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("single",Arrays.asList("pinch"),1000)),true,300);
        c.now+=10000;t=c.now;d.pinch(true,t,1000);equal(seen,Arrays.asList("single"));
        d.pinch(true,t+10,1000);d.pinch(false,t+1800,1000);c.advance(t+2000);equal(seen,Arrays.asList("single"));
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("single",Arrays.asList("pinch"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("double",Arrays.asList("pinch","pinch"),1000)),true,300);
        c.now+=1000;t=c.now;d.pinch(true,t,1000);d.pinch(false,t+80,1000);c.advance(t+299);equal(seen.size(),0);
        c.now=t+299;d.pinch(true,t+299,1000);equal(seen,Arrays.asList("double"));d.pinch(false,t+380,1000);c.advance(t+2000);equal(seen.size(),1);
        seen.clear();d.reset();c.now+=1000;t=c.now;d.pinch(true,t,1000);c.advance(t+299);equal(seen.size(),0);c.advance(t+300);equal(seen,Arrays.asList("single"));d.pinch(false,t+600,1000);equal(seen.size(),1);
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("up",Arrays.asList("up"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("up-down",Arrays.asList("up","down"),1000)),true,300);
        c.now+=1000;t=c.now;d.swipe("up",t,1000);c.advance(t+300);equal(seen,Arrays.asList("up"));
        seen.clear();d.reset();c.now+=1000;t=c.now;d.swipe("up",t,1000);c.advance(t+150);d.swipe("down",t+150,1000);equal(seen,Arrays.asList("up-down"));c.advance(t+1500);equal(seen.size(),1);
        // Without a shorter action, keep the user's slower sequence interval.
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("mixed",Arrays.asList("up","pinch","down"),1200)),true,300);
        c.now+=1000;t=c.now;d.swipe("up",t,1200);c.advance(t+1000);d.pinch(true,t+1000,1200);d.pinch(false,t+1100,1200);c.advance(t+2000);d.swipe("down",t+2000,1200);equal(seen,Arrays.asList("mixed"));
        // A smaller configured interval must not be increased by fast response.
        seen.clear();d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("up",Arrays.asList("up"),200),new ru.focusflow.pen.SequenceMatcher.Rule("pair",Arrays.asList("up","up"),200)),true,300);
        c.now+=1000;t=c.now;d.swipe("up",t,200);c.advance(t+199);equal(seen.size(),0);c.advance(t+200);equal(seen,Arrays.asList("up"));
        seen.clear();d.reset();c.now+=1000;t=c.now;d.swipe("up",t,200);d.reset();c.advance(t+1000);equal(seen.size(),0);
        // Turning fast response off restores release detection and long windows.
        d.configure(Arrays.asList(new ru.focusflow.pen.SequenceMatcher.Rule("single",Arrays.asList("pinch"),1000),new ru.focusflow.pen.SequenceMatcher.Rule("double",Arrays.asList("pinch","pinch"),1000)),false,0);
        c.now+=1000;t=c.now;d.pinch(true,t,1000);equal(seen.size(),0);c.now=t+100;d.pinch(false,t+100,1000);c.advance(t+1099);equal(seen.size(),0);c.advance(t+1100);equal(seen,Arrays.asList("single"));
        System.out.println("PASS: "+checks+" total assertions including immediate press, 300ms ambiguity, fast pairs and full custom gaps");
    }
}
