package ru.folio.notes;

import java.util.*;

/** Platform-free color state shared by the gradient, swatches, preview and HEX input. */
final class ColorModel {
    float hue,saturation,lightness,shadowS;
    ColorModel(int color){set(color);}
    void set(int color){float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),delta=max-min;if(max>0)shadowS=(max-min)/max;lightness=(max+min)/2;saturation=delta==0?0:delta/(1-Math.abs(2*lightness-1));if(delta!=0){hue=max==r?60*((g-b)/delta%6):max==g?60*((b-r)/delta+2):60*((r-g)/delta+4);if(hue<0)hue+=360;}}
    static float clamp(float value,float low,float high){return Float.isFinite(value)?Math.max(low,Math.min(high,value)):low;}
    void slider(int index,float value){if(index==0)hue=clamp(value,0,360);else if(index==1)saturation=clamp(value,0,1);else if(index==2)lightness=clamp(value,0,1);}
    int color(){return rgb(hue,saturation,lightness);}
    float value(){return lightness+saturation*Math.min(lightness,1-lightness);}
    float vividness(){float v=value();return v==0?shadowS:2*(1-lightness/v);}
    void gradient(float s,float v){s=clamp(s,0,1);v=clamp(v,0,1);shadowS=s;lightness=v*(1-s/2);saturation=lightness==0||lightness==1?0:(v-lightness)/Math.min(lightness,1-lightness);}
    static int rgb(float h,float s,float l){h=clamp(h,0,360)%360;s=clamp(s,0,1);l=clamp(l,0,1);float c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs((h/60)%2-1)),m=l-c/2,r=0,g=0,b=0;if(h<60){r=c;g=x;}else if(h<120){r=x;g=c;}else if(h<180){g=c;b=x;}else if(h<240){g=x;b=c;}else if(h<300){r=x;b=c;}else {r=c;b=x;}return 0xff000000|(Math.round((r+m)*255)<<16)|(Math.round((g+m)*255)<<8)|Math.round((b+m)*255);}
    static String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
    static int parse(String text){String value=text.trim();if(value.startsWith("#"))value=value.substring(1);if(value.matches("[0-9a-fA-F]{3}"))value=""+value.charAt(0)+value.charAt(0)+value.charAt(1)+value.charAt(1)+value.charAt(2)+value.charAt(2);if(!value.matches("[0-9a-fA-F]{6}"))throw new IllegalArgumentException("HEX");return 0xff000000|Integer.parseInt(value,16);}
    static int[] recent(int[] previous,int color,int max){ArrayList<Integer> result=new ArrayList<>();result.add(color|0xff000000);for(int item:previous)if(!result.contains(item|0xff000000)&&result.size()<max)result.add(item|0xff000000);int[] out=new int[Math.min(max,result.size())];for(int i=0;i<out.length;i++)out[i]=result.get(i);return out;}
}
