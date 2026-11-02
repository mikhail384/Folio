package ru.folio.notes;

/** Uses event timestamps so IPC batching cannot turn two taps into a single tap. */
public final class PenGestures {
    public interface Actions {void single();void doubleTap();}
    private long pending=-1;
    private final int window;
    public PenGestures(int window){this.window=window;}
    public void tap(long time,Actions actions){
        if(pending>=0&&time>=pending&&time-pending<=window){pending=-1;actions.doubleTap();}
        else {if(pending>=0)actions.single();pending=time;}
    }
    public void flush(long time,Actions actions){if(pending>=0&&time-pending>window){pending=-1;actions.single();}}
    public long deadline(){return pending<0?-1:pending+window+1;}
    public void cancel(){pending=-1;}
}
