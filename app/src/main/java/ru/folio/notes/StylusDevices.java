package ru.folio.notes;

import android.os.Build;
import android.view.*;
import java.util.Locale;

/** Standard Android input is the default; Xiaomi services are an optional extension. */
final class StylusDevices {
    static boolean pen(int type){return type==MotionEvent.TOOL_TYPE_STYLUS||type==MotionEvent.TOOL_TYPE_ERASER;}
    static boolean focus(InputDevice device){return device!=null&&device.getName().toLowerCase(Locale.ROOT).contains("focus pen");}
    static boolean xiaomi(){String vendor=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);return vendor.contains("xiaomi")||vendor.contains("redmi")||vendor.contains("poco");}
    static boolean bridge(){if(xiaomi())return true;for(int id:InputDevice.getDeviceIds())if(focus(InputDevice.getDevice(id)))return true;return false;}
    static boolean scanCode(InputDevice device){return focus(device);}
    static final class Input {
        int deviceId=Integer.MIN_VALUE,source,buttons;boolean pressureRange;float min,max;
        String name="Перо ещё не касалось холста";
        void observe(MotionEvent event){
            if(deviceId==event.getDeviceId()&&source==event.getSource())return;
            if(deviceId!=event.getDeviceId())buttons=0;
            deviceId=event.getDeviceId();source=event.getSource();pressureRange=false;min=0;max=1;
            InputDevice device=event.getDevice();if(device==null){name="Стандартное перо Android";return;}
            name=device.getName();InputDevice.MotionRange range=device.getMotionRange(MotionEvent.AXIS_PRESSURE,source);
            if(range==null)range=device.getMotionRange(MotionEvent.AXIS_PRESSURE);
            if(range!=null){min=range.getMin();max=range.getMax();pressureRange=Float.isFinite(min)&&Float.isFinite(max)&&max>min;}
        }
        float pressure(MotionEvent e,int i,int h){observe(e);return StylusSamples.pressure(h<0?e.getPressure(i):e.getHistoricalPressure(i,h),min,max,pressureRange);}
        int pressed(MotionEvent e){observe(e);int before=buttons;buttons=e.getButtonState();
            if(e.getActionMasked()==MotionEvent.ACTION_BUTTON_PRESS)buttons|=e.getActionButton();
            if(e.getActionMasked()==MotionEvent.ACTION_BUTTON_RELEASE)buttons&=~e.getActionButton();
            return buttons&~before;}
        String summary(){return name+"\nНажим: "+(pressureRange?"диапазон устройства":"стандартный ввод / резерв");}
    }
}
