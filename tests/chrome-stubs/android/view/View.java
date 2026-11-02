package android.view;
public final class View {
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE=0x100,SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=0x400,SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=0x200,SYSTEM_UI_FLAG_FULLSCREEN=4,SYSTEM_UI_FLAG_HIDE_NAVIGATION=2,SYSTEM_UI_FLAG_IMMERSIVE_STICKY=0x1000;
    public WindowInsetsController controller;
    public int visibility;public void setSystemUiVisibility(int flags){visibility=flags;}
    public WindowInsetsController getWindowInsetsController(){return controller;}
}
