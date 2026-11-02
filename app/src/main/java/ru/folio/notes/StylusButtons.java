package ru.folio.notes;

/** Some drivers report one barrel press through both KeyEvent and MotionEvent. */
final class StylusButtons {
    static final int KEY=1,MOTION=2;
    final int[] devices={Integer.MIN_VALUE,Integer.MIN_VALUE},sources=new int[2];
    final long[] times={Long.MIN_VALUE,Long.MIN_VALUE};
    boolean accept(int button,int device,long time,int source){
        if(button<0||button>1)return false;
        long elapsed=time-times[button];
        if(devices[button]==device&&sources[button]!=source&&time>=times[button]&&elapsed>=0&&elapsed<=60)return false;
        devices[button]=device;sources[button]=source;times[button]=time;return true;
    }
}
