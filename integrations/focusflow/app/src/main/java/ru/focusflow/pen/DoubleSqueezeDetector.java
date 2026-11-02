package ru.focusflow.pen;

/** Counts complete press/release pairs; never turns key repeats or a hold into a double. */
public final class DoubleSqueezeDetector {
    private long downAt = -1, firstUp = -1, firedAt = -1;
    private boolean second;
    public void reset() { downAt = firstUp = firedAt = -1; second = false; }
    public boolean accept(boolean down, long time, int windowMs) {
        if (time < 0) return false;
        if (down) {
            if (downAt >= 0) return false;
            if (firedAt >= 0 && time - firedAt < 600) { firstUp = -1; return false; }
            second = firstUp >= 0 && time >= firstUp && time - firstUp <= windowMs;
            downAt = time;
            return false;
        }
        if (downAt < 0) return false;
        long duration = time - downAt;
        downAt = -1;
        if (duration < 0 || duration > 900) { firstUp = -1; second = false; return false; }
        if (second) { firstUp = -1; second = false; firedAt = time; return true; }
        firstUp = time;
        return false;
    }
}
