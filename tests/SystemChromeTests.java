package ru.folio.notes;

import android.os.Build;
import android.graphics.Insets;
import android.view.*;
import java.util.Arrays;

/** Run the real bar/inset policy against a state-recording Android window surface. */
public final class SystemChromeTests {
    static int assertions;
    static void check(boolean yes,String label){assertions++;if(!yes)throw new AssertionError(label);}
    static void insets(int api){
        Build.VERSION.SDK_INT=api;
        WindowInsets hidden=new WindowInsets(new Insets(0,0,0,0),new Insets(0,0,0,0));
        WindowInsets transientBars=new WindowInsets(new Insets(0,32,0,48),new Insets(0,0,0,0));
        int[] normal=SystemChrome.contentInsets(hidden,false),duringMenu=SystemChrome.contentInsets(transientBars,false);
        check(Arrays.equals(normal,duringMenu),"API "+api+": transient bars cannot move toolbar/canvas");
        check(Arrays.equals(normal,new int[]{0,0,0,0}),"no hidden bar padding");
        check(Arrays.equals(SystemChrome.contentInsets(transientBars,true),new int[]{0,32,0,48}),"visible bars restore safe content padding");
        WindowInsets notch=new WindowInsets(new Insets(0,32,0,48),new Insets(25,12,0,0));
        check(Arrays.equals(SystemChrome.contentInsets(notch,false),api>=28?new int[]{25,12,0,0}:new int[]{0,0,0,0}),"display cutout retained in immersive mode");
        if(api>=30)check(Arrays.equals(SystemChrome.contentInsets(notch,true),new int[]{25,32,0,48}),"visible bars merge with cutout");
    }
    static void windows(int api){
        Build.VERSION.SDK_INT=api;Window owner=new Window(),inspector=new Window();owner.flags=inspector.flags=2;
        SystemChrome.prepare(inspector,false);check(inspector.decor==null,"constructor preparation must not create decor or request its controller");
        SystemChrome.apply(owner,false);SystemChrome.apply(inspector,false);
        check(owner.flags==inspector.flags&&(inspector.flags&0x400)!=0,"owner and focused inspector share fullscreen before attach");
        check((inspector.flags&2)!=0,"other window flags retained");
        if(api>=30){check(!owner.fits&&!inspector.fits,"transient bars overlay, never resize, both windows");check(owner.controller.hidden==3&&inspector.controller.hidden==3,"hide status/navigation only, not IME");check(inspector.controller.behavior==2,"swipe remains transient");}
        else {check(owner.decor.visibility==inspector.decor.visibility,"legacy dialogs inherit immersive flags");check((owner.decor.visibility&0x700)==0x700,"legacy layout remains stable under both bars");check((owner.decor.visibility&0x1006)==0x1006,"legacy bars hidden with immersive sticky");}
        // Focus transitions can reapply the policy without changing owner geometry.
        int flags=owner.flags;SystemChrome.apply(inspector,false);check(owner.flags==flags,"dialog focus does not mutate owner flags");
        SystemChrome.apply(owner,true);check((owner.flags&0x400)==0&&(owner.flags&2)!=0,"leaving app restores bars without changing other flags");
        if(api>=30){check(owner.controller.shown==3,"show restores status and navigation");Window pending=new Window();pending.controller=null;SystemChrome.apply(pending,false);check((pending.flags&0x400)!=0,"fullscreen prepared while insets controller is not attached");}
        else check(owner.decor.visibility==0x700,"leaving immersive mode clears hidden flags, retains stable layout");
    }
    public static void main(String[] args){for(int api:new int[]{26,28,29,30,35}){insets(api);windows(api);}SystemChrome.apply(null,false);System.out.println("PASS "+assertions+" production system-bar/inset assertions across API 26/28/29/30/35. Android window surface simulated; on-device menu validation pending.");}
}
