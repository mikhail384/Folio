package ru.folio.notes;

/** Document pixels use 96 dpi. Measurements, the ruler and custom sizes share this scale. */
final class PaperSizes {
    static final float DPI=96,MM=DPI/25.4f,CM=MM*10;
    static final int[] COLORS={0xffffffff,0xfffffaf0,0xff000000};
    static final String[] NAMES={"Бесконечный","A4 · вертикально","A4 · горизонтально","16:9 · горизонтально","9:16 · вертикально","Квадрат 1:1","Свой размер"};
    static final float[][] SIZES={{1200,1640},{210*MM,297*MM},{297*MM,210*MM},{1920,1080},{1080,1920},{1200,1200}};
    static final float MIN=200,MAX=10000;
    static boolean valid(float w,float h){return Float.isFinite(w)&&Float.isFinite(h)&&w>=MIN&&h>=MIN&&w<=MAX&&h<=MAX;}
    static float pixels(float value,boolean mm){return mm?value*MM:value;}
    static int linesFor(int paper){int r=paper>>16&255,g=paper>>8&255,b=paper&255;return r*.2126f+g*.7152f+b*.0722f<100?0x4dffffff:0x4d000000;}
}
