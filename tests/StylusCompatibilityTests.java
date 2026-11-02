package ru.folio.notes;
import android.os.Build;
import android.view.*;
import java.util.*;
public final class StylusCompatibilityTests {
    static int checks;static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
    static boolean near(float a,float b){return Math.abs(a-b)<.0001f;}
    public static void main(String[] args){
        for(float max:new float[]{1,1.4f,4095,8191}){check(near(StylusSamples.pressure(max/2,0,max,true),.5f),"pressure uses reported hardware range");check(StylusSamples.pressure(max*2,0,max,true)==1,"pressure bounded above");check(StylusSamples.pressure(-max,0,max,true)==0,"pressure bounded below");}
        check(near(StylusSamples.pressure(50,10,90,true),.5f),"pressure minimum is respected");
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){check(StylusSamples.pressure(bad,0,1,true)==1,"invalid pressure has a visible safe fallback");check(StylusSamples.tilt(bad)==0&&StylusSamples.orientation(bad)==0,"invalid tilt/orientation cannot poison the canvas");}
        check(StylusSamples.pressure(0,0,0,false)==1,"pressureless pen keeps full line width");check(near(StylusSamples.pressure(.4f,0,1,false),.4f),"valid samples are retained when range metadata is missing");
        check(StylusSamples.tilt(-2)==0&&near(StylusSamples.tilt(9),(float)Math.PI/2),"tilt remains a valid angle");
        StylusButtons buttons=new StylusButtons();check(buttons.accept(0,9,1000,StylusButtons.KEY),"native key press accepted");check(!buttons.accept(0,9,1010,StylusButtons.MOTION),"duplicate motion report suppressed");check(buttons.accept(0,9,1030,StylusButtons.KEY),"rapid separate press on same channel retained");check(!buttons.accept(0,9,1040,StylusButtons.MOTION),"duplicate of rapid press suppressed");check(buttons.accept(1,9,1040,StylusButtons.MOTION),"secondary button independent");check(buttons.accept(0,10,1040,StylusButtons.MOTION),"another pen independent");check(buttons.accept(0,10,1200,StylusButtons.KEY),"later press through other channel accepted");check(buttons.accept(0,10,20,StylusButtons.MOTION),"clock reset cannot lock buttons");
        check(StylusDevices.pen(MotionEvent.TOOL_TYPE_STYLUS)&&StylusDevices.pen(MotionEvent.TOOL_TYPE_ERASER)&&!StylusDevices.pen(MotionEvent.TOOL_TYPE_FINGER),"finger and reversed pen stay distinct");
        for(String vendor:Arrays.asList("Samsung","HUAWEI","HONOR","Lenovo","OPPO","vivo","Google","OnePlus")){Build.MANUFACTURER=Build.BRAND=vendor;check(!StylusDevices.bridge(),"standard Android input does not start Xiaomi ADB extension: "+vendor);}
        for(String vendor:Arrays.asList("Xiaomi","Redmi","POCO")){Build.MANUFACTURER=Build.BRAND=vendor;check(StylusDevices.bridge(),"existing Xiaomi helper remains available: "+vendor);}
        Build.MANUFACTURER=Build.BRAND="Lenovo";InputDevice ordinary=new InputDevice("Bluetooth Keyboard",null,null);InputDevice focus=new InputDevice("Xiaomi Focus Pen Pro Keyboard",null,null);
        check(!StylusDevices.scanCode(ordinary)&&StylusDevices.scanCode(focus),"ordinary keyboard scan codes are not mistaken for Focus Pen gestures");
        InputDevice.DEVICES.put(3,focus);check(StylusDevices.bridge(),"explicit Focus Pen device enables its extension");InputDevice.DEVICES.clear();
        InputDevice.DEVICES.put(8,new InputDevice("USB pen",0f,4095f));StylusDevices.Input input=new StylusDevices.Input();MotionEvent e=new MotionEvent(8,2047.5f);e.history=new float[]{1023.75f,4095};
        check(near(input.pressure(e,0,-1),.5f)&&near(input.pressure(e,0,0),.25f)&&input.pressure(e,0,1)==1,"current and historical hardware samples share normalization");
        e.action=11;e.button=32;e.state=32;check(input.pressed(e)==32,"generic barrel press detected");e.action=7;check(input.pressed(e)==0,"held button across hover/move is not repeated");e.action=12;e.button=32;e.state=0;check(input.pressed(e)==0,"release does not fire an action");e.action=0;e.state=32;check(input.pressed(e)==32,"older driver button-state edge detected on touch");
        InputDevice.DEVICES.put(9,new InputDevice("Pressureless stylus",null,null));MotionEvent noPressure=new MotionEvent(9,0);check(input.pressure(noPressure,0,-1)==1&&!input.pressureRange,"hotplug switches range and safe fallback");
        noPressure.state=32;check(input.pressed(noPressure)==32,"button state resets for another input device");
        System.out.println("PASS "+checks+" production Android stylus compatibility checks: normalized ranges/history, invalid/missing axes, generic buttons/dedup, finger/eraser distinction, keyboard isolation and optional Xiaomi services. Android transport substituted; not physical device certification.");
    }
}
