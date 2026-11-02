package android.os;import java.util.*;public class Handler {
 public static volatile long now;static long serial;static final List<Task> tasks=new ArrayList<>();
 static class Task {long at,id;Runnable body;Task(long at,Runnable body){this.at=at;this.body=body;id=serial++;}}
 public Handler(Looper looper){}public boolean post(Runnable body){return postDelayed(body,0);}
 public boolean postDelayed(Runnable body,long delay){synchronized(tasks){tasks.add(new Task(now+delay,body));}return true;}
 public void removeCallbacks(Runnable body){synchronized(tasks){tasks.removeIf(t->t.body==body);}}
 public static void advance(long target){while(true){Task next=null;synchronized(tasks){for(Task t:tasks)if(t.at<=target&&(next==null||t.at<next.at||t.at==next.at&&t.id<next.id))next=t;if(next!=null)tasks.remove(next);}if(next==null)break;now=next.at;next.body.run();}now=target;}
}
