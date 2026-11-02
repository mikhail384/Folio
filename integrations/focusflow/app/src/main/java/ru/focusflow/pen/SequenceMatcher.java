package ru.focusflow.pen;
import java.util.*;

/** Longest configured sequence wins; unconfigured longer sequences never swallow singles. */
public final class SequenceMatcher {
    public static final class Rule {
        public final String id;public final List<String> steps;public final int gapMs;
        public Rule(String id,List<String> steps,int gapMs){this.id=id;this.steps=new ArrayList<>(steps);this.gapMs=gapMs;}
    }
    private static final class Signal {String token;long time;Signal(String s,long t){token=s;time=t;}}
    private final PenGestureDetector.Scheduler scheduler;private final PenGestureDetector.Listener listener;
    private final List<Signal> pending=new ArrayList<>();private List<Rule> rules=new ArrayList<>();
    private PenGestureDetector.Ticket timer;private int generation,decisionWindowMs;
    public SequenceMatcher(PenGestureDetector.Scheduler s,PenGestureDetector.Listener l){scheduler=s;listener=l;}
    public void configure(List<Rule> list){configure(list,0);}
    public void configure(List<Rule> list,int decisionWindow){reset();rules=new ArrayList<>(list);decisionWindowMs=Math.max(0,decisionWindow);}
    public void reset(){cancelTimer();pending.clear();}
    public void pause(){cancelTimer();}
    private void cancelTimer(){generation++;if(timer!=null)timer.cancel();timer=null;}
    private boolean matches(Rule rule,int length){
        if(length>rule.steps.size()||length>pending.size())return false;
        for(int i=0;i<length;i++){
            if(!rule.steps.get(i).equals(pending.get(i).token))return false;
            if(i>0){long delta=pending.get(i).time-pending.get(i-1).time;if(delta<0||delta>rule.gapMs)return false;}
        }return true;
    }
    private Rule completedPrefix(){Rule best=null;for(Rule r:rules)if(r.steps.size()<=pending.size()&&matches(r,r.steps.size())&&(best==null||r.steps.size()>best.steps.size()))best=r;return best;}
    private void consume(Rule rule){int n=rule==null?1:rule.steps.size();pending.subList(0,n).clear();if(rule!=null)listener.gesture(rule.id);}
    public void accept(String token,long time){
        if(time<0)return;cancelTimer();pending.add(new Signal(token,time));
        while(!pending.isEmpty()){
            Rule exact=null;int wait=0;
            for(Rule r:rules)if(matches(r,pending.size())){if(r.steps.size()==pending.size())exact=r;else wait=Math.max(wait,r.gapMs);}
            // Only a completed shorter gesture needs a fast decision. A sequence
            // with no shorter action keeps its full, user-configured input gap.
            if(wait>0){if(exact!=null&&decisionWindowMs>0)wait=Math.min(wait,decisionWindowMs);final int ticket=generation;timer=scheduler.after(wait,()->{if(ticket==generation)flush();});return;}
            if(exact!=null)consume(exact);else consume(completedPrefix());
        }
    }
    public void flush(){cancelTimer();while(!pending.isEmpty())consume(completedPrefix());}
}
