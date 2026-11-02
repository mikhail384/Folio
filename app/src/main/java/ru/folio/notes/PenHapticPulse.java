package ru.folio.notes;

/** Xiaomi's observed one-shot BLE motor command. No persistent pen or system settings. */
final class PenHapticPulse {
    static final int COMMAND=3002,DOUBLE_TAP=3,AMPLITUDE=128;
    private long last=-1;
    synchronized boolean accept(long now,boolean active,boolean enabled){
        if(!active||!enabled||now<0||last>=0&&now-last<120)return false;
        last=now;return true;
    }
}
