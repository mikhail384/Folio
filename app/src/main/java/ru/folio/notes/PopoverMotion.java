package ru.folio.notes;

/** Motion measured from the supplied gallery popover: a rounded seed, fast expansion, soft settle. */
public final class PopoverMotion {
    public static final int OPEN_MS=360,CLOSE_MS=150;
    private PopoverMotion(){}
    public static float clamp(float t){return Math.max(0,Math.min(1,t));}
    public static float expansion(float t){if(t<=0)return 0;if(t>=1)return 1;return (float)(1-Math.exp(-9*t)*(Math.cos(10*t)+.9*Math.sin(10*t)));}
    public static float opacity(float t){return clamp(t/.13f);}
    public static float content(float t){float k=clamp((t-.10f)/.28f);return k*k*(3-2*k);}
    public static float corner(float t){float k=clamp(t/.32f);return k*k*(3-2*k);}
    public static float closing(float t){t=clamp(t);return t*t*(3-2*t);}
    public static float lerp(float a,float b,float t){return a+(b-a)*t;}
}
