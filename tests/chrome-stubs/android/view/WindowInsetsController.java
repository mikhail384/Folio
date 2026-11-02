package android.view;
public final class WindowInsetsController {
    public static final int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE=2;
    public int behavior,hidden,shown;public void setSystemBarsBehavior(int b){behavior=b;}public void hide(int types){hidden=types;}public void show(int types){shown=types;}
}
