package android.view;
import android.graphics.Insets;
public final class WindowInsets {
    public static final class Type {public static int systemBars(){return 3;}public static int displayCutout(){return 4;}}
    public Insets bars,cutout;public WindowInsets(Insets bars,Insets cutout){this.bars=bars;this.cutout=cutout;}
    public Insets getInsets(int types){boolean b=(types&3)!=0,c=(types&4)!=0;return new Insets(Math.max(b?bars.left:0,c?cutout.left:0),Math.max(b?bars.top:0,c?cutout.top:0),Math.max(b?bars.right:0,c?cutout.right:0),Math.max(b?bars.bottom:0,c?cutout.bottom:0));}
    public int getSystemWindowInsetLeft(){return bars.left;}public int getSystemWindowInsetTop(){return bars.top;}public int getSystemWindowInsetRight(){return bars.right;}public int getSystemWindowInsetBottom(){return bars.bottom;}
    public DisplayCutout getDisplayCutout(){return new DisplayCutout(cutout.left,cutout.top,cutout.right,cutout.bottom);}
}
