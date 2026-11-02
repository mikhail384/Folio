package android.view;
public final class MotionEvent {
    public static final int TOOL_TYPE_FINGER=1,TOOL_TYPE_STYLUS=2,TOOL_TYPE_ERASER=4,AXIS_PRESSURE=2,ACTION_BUTTON_PRESS=11,ACTION_BUTTON_RELEASE=12;
    public int device,source=0x4002,action,button,state;public float pressure;public float[] history=new float[0];
    public MotionEvent(int device,float pressure){this.device=device;this.pressure=pressure;}
    public int getDeviceId(){return device;}public int getSource(){return source;}
    public InputDevice getDevice(){return InputDevice.getDevice(device);}
    public float getPressure(int pointer){return pressure;}
    public float getHistoricalPressure(int pointer,int position){return history[position];}
    public int getButtonState(){return state;}public int getActionMasked(){return action;}public int getActionButton(){return button;}
}
