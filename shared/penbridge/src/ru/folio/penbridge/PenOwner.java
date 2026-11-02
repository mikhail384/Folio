package ru.folio.penbridge;

/** The only routing authority. A lost lease drops input instead of replaying it. */
public final class PenOwner {
    public static final int NONE=0,FOLIO=1,FOCUSFLOW=2;public static final long LEASE_MS=700;
    private int owner;private long epoch,updated=-1;private boolean grab;
    private final long[] pressed=new long[768];private final int[] pressedOwner=new int[768];
    public synchronized void update(boolean folioPriority,boolean folioActive,boolean folioGrab,boolean focusReady,boolean focusGrab,long now){
        int next=folioPriority?(folioActive?FOLIO:NONE):focusReady?FOCUSFLOW:NONE;
        boolean nextGrab=folioPriority?folioActive&&folioGrab:focusReady&&focusGrab;
        if(next!=owner||updated<0||now<updated||now-updated>LEASE_MS){epoch++;owner=next;}grab=nextGrab;updated=now;
    }
    public synchronized int owner(long now){return updated>=0&&now>=updated&&now-updated<=LEASE_MS?owner:NONE;}
    public synchronized long epoch(){return epoch;}
    public synchronized boolean grab(long now){return owner(now)!=NONE&&grab;}
    public synchronized Ticket key(int code,int value,long now){if(code<0||code>=pressed.length||value<0||value>1)return null;int current=owner(now);if(value==1){pressed[code]=epoch;pressedOwner[code]=current;return current==NONE?null:new Ticket(current,epoch);}boolean matched=current!=NONE&&pressedOwner[code]==current&&pressed[code]==epoch;pressedOwner[code]=NONE;return matched?new Ticket(current,epoch):null;}
    public synchronized Ticket rotation(long now){return owner(now)==FOLIO?new Ticket(FOLIO,epoch):null;}
    public synchronized boolean valid(Ticket ticket,long now){return ticket!=null&&ticket.owner==owner(now)&&ticket.epoch==epoch;}
    public static final class Ticket {public final int owner;public final long epoch;Ticket(int owner,long epoch){this.owner=owner;this.epoch=epoch;}}
}
