package ru.folio.notes;

/** Exact critically damped solution. Retargeting preserves velocity, including at high refresh rates. */
public final class SmoothSpring {
    public float value,velocity,target;public final float frequency;
    public SmoothSpring(float value,float frequency){this.value=target=value;this.frequency=frequency;}
    public void target(float value){if(Float.isFinite(value))target=value;}
    public void snap(float value){this.value=target=value;velocity=0;}
    public boolean step(float seconds){float dt=Float.isFinite(seconds)?Math.max(0,Math.min(1,seconds)):0,d=value-target,a=velocity+frequency*d,e=(float)Math.exp(-frequency*dt);value=target+(d+a*dt)*e;velocity=(velocity-frequency*a*dt)*e;if(Math.abs(value-target)<.0005f&&Math.abs(velocity)<.008f){snap(target);return false;}return true;}
}
