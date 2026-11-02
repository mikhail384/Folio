package ru.folio.notes;

import android.graphics.Insets;
import android.os.Build;
import android.view.*;

/** Apply the same bar policy to the activity and every focused inspector window. */
final class SystemChrome {
    static void prepare(Window window,boolean visible){
        if(window==null)return;
        if(visible)window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    static void apply(Window window,boolean visible){
        if(window==null)return;
        prepare(window,visible);
        if(Build.VERSION.SDK_INT>=30){
            // Content handles its own safe insets; transient bars must overlay it, not resize it.
            window.setDecorFitsSystemWindows(false);
            // PhoneWindow.getInsetsController() dereferences an uncreated decor on Android 15.
            WindowInsetsController controller=window.getDecorView().getWindowInsetsController();
            if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(visible)controller.show(WindowInsets.Type.systemBars());else controller.hide(WindowInsets.Type.systemBars());}
        }else {
            int stable=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            window.getDecorView().setSystemUiVisibility(stable|(visible?0:View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
        }
    }
    static int[] contentInsets(WindowInsets insets,boolean barsVisible){
        if(Build.VERSION.SDK_INT>=30){Insets safe=insets.getInsets(WindowInsets.Type.displayCutout()|(barsVisible?WindowInsets.Type.systemBars():0));return new int[]{safe.left,safe.top,safe.right,safe.bottom};}
        if(barsVisible)return new int[]{insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()};
        if(Build.VERSION.SDK_INT>=28){DisplayCutout cutout=insets.getDisplayCutout();if(cutout!=null)return new int[]{cutout.getSafeInsetLeft(),cutout.getSafeInsetTop(),cutout.getSafeInsetRight(),cutout.getSafeInsetBottom()};}
        return new int[]{0,0,0,0};
    }
}
