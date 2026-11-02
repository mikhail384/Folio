package android.view;
public final class Window {
    public int flags;public boolean fits=true;public View decor;public WindowInsetsController controller=new WindowInsetsController();
    public void addFlags(int f){flags|=f;}public void clearFlags(int f){flags&=~f;}public void setDecorFitsSystemWindows(boolean fits){this.fits=fits;}
    public View getDecorView(){if(decor==null)decor=new View();decor.controller=controller;return decor;}
    // Match Android PhoneWindow: this accessor dereferences decor before it is created.
    public WindowInsetsController getInsetsController(){return decor.getWindowInsetsController();}
}
