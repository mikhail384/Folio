package ru.folio.penbridge;

import android.app.Activity;
import android.content.*;
import android.provider.Settings;

public final class WirelessDebugging {
    /** -1 is unknown, never misreported as disabled just because mDNS is slow. */
    public static int state(Context context){try{int value=Settings.Global.getInt(context.getContentResolver(),"adb_wifi_enabled",-1);return value==0||value==1?value:-1;}catch(RuntimeException unavailable){return -1;}}
    public static void settings(Activity activity){
        for(String action:new String[]{"android.settings.WIRELESS_DEBUGGING_SETTINGS",Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,Settings.ACTION_SETTINGS})try{activity.startActivity(new Intent(action));return;}catch(ActivityNotFoundException|SecurityException ignored){}
    }
}
