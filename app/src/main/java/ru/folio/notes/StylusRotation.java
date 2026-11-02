package ru.folio.notes;

import android.content.*;
import android.os.*;
import android.view.*;
import java.util.concurrent.*;

/** Connects to the installed PencilEngine service; no vendor code or assets are bundled. */
final class StylusRotation {
    static final String ENGINE="com.xiaomi.touchservice.pencilengine.IPencilEngine";
    static final String CALLBACK="com.xiaomi.touchservice.pencilengine.IPencilEngineCallback";
    final MainActivity a;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    volatile IBinder remote;
    boolean bound;volatile boolean requested,closed;
    volatile String state="Ожидание холста";
    volatile long events;
    final Binder callback=new Binder(){protected boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException{
        if(code==INTERFACE_TRANSACTION){if(out!=null)out.writeString(CALLBACK);return true;}
        if(code==1){in.enforceInterface(CALLBACK);int type=in.readInt(),first=in.readInt(),second=in.readInt();if(out!=null)out.writeNoException();return true;}return super.onTransact(code,in,out,flags);
    }};
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;remote=binder;worker.execute(()->{try{
            if(!ENGINE.equals(binder.getInterfaceDescriptor()))throw new SecurityException("Unexpected PencilEngine interface");
            Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInterfaceToken(ENGINE);in.writeStrongBinder(callback);in.writeString(a.getPackageName());if(!binder.transact(4,in,out,0))throw new RemoteException("Registration unavailable");out.readException();}finally{in.recycle();out.recycle();}
            state="Служба пера подключена";setEnabled(requested);
        }catch(Exception e){state="Служба пера: "+e.getClass().getSimpleName();android.util.Log.w("FolioRotation",state,e);}});}
        public void onServiceDisconnected(ComponentName name){remote=null;state="Связь со службой потеряна";}
    };
    StylusRotation(MainActivity activity){a=activity;}
    void update(){if(closed)return;boolean want=StylusDevices.xiaomi()&&a.penActive()&&a.canvas.rotationEnabled;if(want&&!bound){
        try{Intent intent=new Intent().setComponent(new ComponentName("com.xiaomi.touchservice","com.xiaomi.touchservice.pencilengine.PencilEngineManagerService"));bound=a.bindService(intent,connection,Context.BIND_AUTO_CREATE);state=bound?"Подключение к службе пера":"Служба пера недоступна";}catch(Exception e){state="Служба пера недоступна";} }
        if(want!=requested){requested=want;worker.execute(()->setEnabled(want));}
    }
    void setEnabled(boolean enable){IBinder service=remote;if(service==null)return;Parcel in=Parcel.obtain(),out=Parcel.obtain();try{
        in.writeInterfaceToken(ENGINE);in.writeInt(1);in.writeInt(enable?1:0);
        if(!service.transact(3,in,out,0))throw new RemoteException("Rotation control unavailable");out.readException();int result=out.readInt();state=(enable?"Вращение подключено":"Вращение приостановлено")+" · "+result;android.util.Log.i("FolioRotation",state);
    }catch(Exception e){state="Вращение: "+e.getClass().getSimpleName();android.util.Log.w("FolioRotation",state,e);}finally{in.recycle();out.recycle();}}
    boolean event(MotionEvent event){if(!a.penActive()||!a.canvas.rotationEnabled||!bound||!event.isFromSource(InputDevice.SOURCE_JOYSTICK)||event.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
        float value=event.getAxisValue(MotionEvent.AXIS_LTRIGGER);if(!Float.isFinite(value)||value<-1||value>1)return false;
        for(int h=0;h<event.getHistorySize();h++){float sample=event.getHistoricalAxisValue(MotionEvent.AXIS_LTRIGGER,h);if(Float.isFinite(sample)&&sample>=-1&&sample<=1)a.canvas.rotation(sample*360,event.getHistoricalEventTime(h));}
        float degrees=value*360;events++;PenProvider.nativeRotationAt=SystemClock.uptimeMillis();PenProvider.rotationSource="PencilEngine / MotionEvent";PenProvider.rotationDegrees=(degrees%360+360)%360;PenProvider.rotationAt=SystemClock.uptimeMillis();PenProvider.rotationEvents++;a.canvas.rotation(degrees,event.getEventTime());return true;
    }
    void close(){closed=true;requested=false;worker.execute(()->{setEnabled(false);a.runOnUiThread(()->{if(bound){try{a.unbindService(connection);}catch(Exception ignored){}bound=false;remote=null;}});});worker.shutdown();}
}
