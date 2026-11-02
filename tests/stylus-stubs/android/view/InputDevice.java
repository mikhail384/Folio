package android.view;
import java.util.*;
public final class InputDevice {
    public static final Map<Integer,InputDevice> DEVICES=new LinkedHashMap<>();
    final String name;final MotionRange range;
    public InputDevice(String name,Float min,Float max){this.name=name;range=min==null?null:new MotionRange(min,max);}
    public static int[] getDeviceIds(){return DEVICES.keySet().stream().mapToInt(Integer::intValue).toArray();}
    public static InputDevice getDevice(int id){return DEVICES.get(id);}
    public String getName(){return name;}
    public MotionRange getMotionRange(int axis){return axis==MotionEvent.AXIS_PRESSURE?range:null;}
    public MotionRange getMotionRange(int axis,int source){return getMotionRange(axis);}
    public static final class MotionRange {final float min,max;MotionRange(float min,float max){this.min=min;this.max=max;}public float getMin(){return min;}public float getMax(){return max;}}
}
