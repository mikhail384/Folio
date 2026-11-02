package ru.folio.notes;

import android.view.*;

final class ToolbarMotion {
    static void press(MainActivity a,View view){if(view.getTag(R.id.toolbar_press)!=null)return;Press effect=new Press(a,view);view.setTag(R.id.toolbar_press,effect);view.setOnTouchListener(effect);}
    static final class Press implements View.OnTouchListener {
        final MainActivity a;final View view;final SmoothSpring scale=new SmoothSpring(1,30);final SpringDriver driver;
        Press(MainActivity a,View view){this.a=a;this.view=view;driver=new SpringDriver(view,()->{view.setScaleX(scale.value);view.setScaleY(scale.value);},scale);}
        public boolean onTouch(View v,android.view.MotionEvent event){int action=event.getActionMasked();if(action==0)driver.to(a.prefs.getBoolean("toolbar_motion",true),.95f);else if(action==1||action==3)driver.to(a.prefs.getBoolean("toolbar_motion",true),1);return false;}
    }
    static void enter(MainActivity a,View view){SmoothSpring phase=new SmoothSpring(0,24);SpringDriver driver=new SpringDriver(view,()->{view.setAlpha(Ink.clamp(phase.value,0,1));view.setTranslationY(a.dp(12)*(1-phase.value));},phase);view.setAlpha(0);view.setTranslationY(a.dp(12));view.post(()->driver.to(a.prefs.getBoolean("toolbar_motion",true),1));}
}
