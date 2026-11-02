package ru.folio.notes;

import android.content.*;
import android.os.*;
import android.view.InputDevice;
import java.util.concurrent.*;

/** Foreground-only pen feedback; never uses the tablet's default vibrator. */
final class PenHaptics {
    final MainActivity a;
    final PenHapticPulse pulse=new PenHapticPulse();
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    volatile Messenger motor;
    volatile boolean active,closed;
    volatile int epoch;
    boolean bound;
    long retryAt;
    volatile String state="Ожидание холста";
    volatile long requests,sent;
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            if(!active||closed)return;
            motor=new Messenger(binder);state="Канал Xiaomi подключён · проверка на пере ожидается";
        }
        public void onServiceDisconnected(ComponentName name){motor=null;state="Связь с пером потеряна";}
        public void onBindingDied(ComponentName name){release();retryAt=SystemClock.uptimeMillis()+5000;}
        public void onNullBinding(ComponentName name){release();state="Служба отклика недоступна";retryAt=SystemClock.uptimeMillis()+30000;}
    };
    PenHaptics(MainActivity activity){a=activity;}
    void update(){
        if(closed)return;
        boolean wanted=a.penActive()&&a.prefs.getBoolean("pen_haptic",true);
        if(wanted!=active){active=wanted;epoch++;}
        if(!active){release();retryAt=0;state=a.prefs.getBoolean("pen_haptic",true)?"Ожидание холста":"Выключен";return;}
        if(!StylusDevices.xiaomi()){state="Отклик через устройство ввода";return;}
        if(bound||SystemClock.uptimeMillis()<retryAt)return;
        retryAt=SystemClock.uptimeMillis()+30000;
        try{
            Intent intent=new Intent().setComponent(new ComponentName("com.xiaomi.bluetooth","com.android.bluetooth.ble.app.oobhelper.MiuiBleOobHelperService"));
            bound=a.bindService(intent,connection,Context.BIND_AUTO_CREATE);
            state=bound?"Подключение к мотору пера":"Канал Xiaomi недоступен; резерв через устройство ввода";
        }catch(SecurityException e){state="Прошивка ограничила канал Xiaomi; резерв через устройство ввода";}
        catch(RuntimeException e){state="Служба отклика недоступна";}
    }
    void doubleTap(){
        update();long now=SystemClock.uptimeMillis();
        if(closed||!pulse.accept(now,active,a.prefs.getBoolean("pen_haptic",true)))return;
        requests++;int generation=epoch;
        // Expired feedback must not be replayed after Binder/service or executor delays.
        worker.execute(()->{if(!active||closed||generation!=epoch||SystemClock.uptimeMillis()-now>160)return;
            Messenger target=motor;
            if(target!=null)try{
                Message message=Message.obtain(null,PenHapticPulse.COMMAND,PenHapticPulse.DOUBLE_TAP,PenHapticPulse.AMPLITUDE);
                target.send(message);sent++;state="Команда отклика отправлена · Xiaomi";return;
            }catch(RemoteException|RuntimeException e){motor=null;}
            try{
                for(int id:InputDevice.getDeviceIds()){
                    InputDevice device=InputDevice.getDevice(id);if(device==null||device.isVirtual())continue;
                    String name=device.getName().toLowerCase(java.util.Locale.ROOT);
                    if(!device.isExternal()&&!name.contains("focus pen"))continue;
                    if(!device.supportsSource(InputDevice.SOURCE_STYLUS)&&!name.contains("focus pen")&&!name.contains("stylus"))continue;
                    Vibrator vibrator=device.getVibrator();
                    if(vibrator!=null&&vibrator.hasVibrator()&&active&&!closed&&generation==epoch&&SystemClock.uptimeMillis()-now<=160){vibrator.vibrate(VibrationEffect.createOneShot(18,VibrationEffect.DEFAULT_AMPLITUDE));sent++;state="Импульс отправлен в перо · устройство ввода";return;}
                }
                state="Прошивка не предоставила доступ к мотору пера";
            }catch(RuntimeException e){state="Отклик пера недоступен: "+e.getClass().getSimpleName();}
        });
    }
    void release(){motor=null;if(bound){try{a.unbindService(connection);}catch(RuntimeException ignored){}bound=false;}}
    void close(){closed=true;active=false;release();worker.shutdownNow();}
}
