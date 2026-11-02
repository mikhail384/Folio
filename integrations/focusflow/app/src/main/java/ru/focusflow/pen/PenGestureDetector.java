package ru.focusflow.pen;
import java.util.*;

/** Converts complete physical squeezes and slides into configurable sequence tokens. */
public final class PenGestureDetector {
    public interface Ticket {void cancel();}
    public interface Scheduler {Ticket after(long delay,Runnable run);}
    public interface Listener {void gesture(String id);}
    private final SequenceMatcher matcher;private long downAt=-1;private boolean custom,pressOnDown;private int pinchWindow=-1,swipeWindow=-1;
    public PenGestureDetector(Scheduler scheduler,Listener listener){matcher=new SequenceMatcher(scheduler,listener);}
    public void configure(List<SequenceMatcher.Rule> rules){configure(rules,false,0);}
    public void configure(List<SequenceMatcher.Rule> rules,boolean immediatePress,int decisionWindow){custom=true;downAt=-1;pressOnDown=immediatePress;matcher.configure(rules,decisionWindow);}
    public void reset(){downAt=-1;matcher.reset();}
    private void defaults(int pinch,int slide){
        if(custom)return;if(pinch<0)pinch=pinchWindow>0?pinchWindow:550;if(slide<0)slide=swipeWindow>0?swipeWindow:450;
        if(pinch==pinchWindow&&slide==swipeWindow)return;pinchWindow=pinch;swipeWindow=slide;
        List<SequenceMatcher.Rule> r=new ArrayList<>();
        r.add(new SequenceMatcher.Rule("squeeze_single",Arrays.asList("pinch"),pinch));r.add(new SequenceMatcher.Rule("squeeze_double",Arrays.asList("pinch","pinch"),pinch));
        for(String a:new String[]{"up","down"}){r.add(new SequenceMatcher.Rule("swipe_"+a,Arrays.asList(a),slide));for(String b:new String[]{"up","down"})r.add(new SequenceMatcher.Rule("swipe_"+a+"_"+b,Arrays.asList(a,b),slide));}matcher.configure(r);
    }
    public void pinch(boolean down,long time,int window){
        defaults(window,-1);if(time<0)return;
        if(down){if(downAt<0){downAt=time;if(pressOnDown)matcher.accept("pinch",time);else matcher.pause();}return;}
        if(downAt<0)return;long held=time-downAt;downAt=-1;
        if(pressOnDown)return;
        if(held<0||held>900){matcher.flush();return;}matcher.accept("pinch",time);
    }
    public void swipe(String direction,long time,int window){defaults(-1,window);if(direction.equals("up")||direction.equals("down"))matcher.accept(direction,time);}
}
