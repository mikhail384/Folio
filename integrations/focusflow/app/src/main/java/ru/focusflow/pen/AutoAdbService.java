package ru.focusflow.pen;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import ru.folio.penbridge.AutoAdbClient;

/** Bound by the visible app. No foreground service or boot receiver is needed. */
public final class AutoAdbService extends Service {
    private volatile boolean visibleClient;
    private final Binder binder=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){reply.writeString(AutoAdbClient.DESCRIPTOR);return true;}
            if(code!=AutoAdbClient.CHECK&&code!=AutoAdbClient.RETRY)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(AutoAdbClient.DESCRIPTOR);
            int uid=Binder.getCallingUid();if(uid!=android.os.Process.myUid()){
                String[] packages=getPackageManager().getPackagesForUid(uid);
                if(packages==null||!java.util.Arrays.asList(packages).contains("ru.folio.notes"))throw new SecurityException("Folio/FocusFlow startup only");
            }
            // No keys, shell commands, APK paths or pairing codes cross this interface.
            Bundle state=LocalAdb.get(AutoAdbService.this).automaticCheck(code==AutoAdbClient.RETRY,()->visibleClient);
            reply.writeNoException();reply.writeBundle(state);return true;
        }
    };
    @Override public IBinder onBind(Intent intent){visibleClient=true;return binder;}
    @Override public boolean onUnbind(Intent intent){visibleClient=false;LocalAdb.get(this).pauseAutomaticDiscovery();return super.onUnbind(intent);}
}
