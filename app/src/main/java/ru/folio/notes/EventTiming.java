package ru.folio.notes;

/** evdev can report wall-clock milliseconds; Android KeyEvent uses uptime. */
final class EventTiming {
    static long uptime(long event,long uptime,long wall){return event>100000000000L?uptime-(wall-event):event;}
    static long remaining(long start,int duration,long now){return Math.max(0,Math.min(duration,duration-(now-start)));}
}
