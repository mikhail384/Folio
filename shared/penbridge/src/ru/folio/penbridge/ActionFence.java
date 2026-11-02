package ru.folio.penbridge;

/** Invalidates pending work on both sides of a foreground handover. */
public final class ActionFence {
    private boolean blocked;private long epoch;
    public synchronized boolean update(boolean value){if(blocked==value)return false;blocked=value;epoch++;return true;}
    public synchronized boolean blocked(){return blocked;}
    public synchronized long ticket(){return epoch;}
    public synchronized boolean valid(long ticket){return !blocked&&ticket==epoch;}
}
