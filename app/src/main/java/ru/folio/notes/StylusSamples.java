package ru.folio.notes;

/** Hardware ranges vary; the document format always receives finite normalized samples. */
final class StylusSamples {
    static float pressure(float raw,float min,float max,boolean range){
        if(!Float.isFinite(raw))return 1;
        if(range&&Float.isFinite(min)&&Float.isFinite(max)&&max>min){
            float normalized=(raw-min)/(max-min);return Math.max(0,Math.min(1,normalized));
        }
        return raw<=0?1:Math.min(1,raw);
    }
    static float tilt(float raw){return Float.isFinite(raw)?Math.max(0,Math.min((float)Math.PI/2,raw)):0;}
    static float orientation(float raw){return Float.isFinite(raw)?raw%(float)(Math.PI*2):0;}
}
