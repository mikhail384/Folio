package ru.folio.penbridge;

/** The same shell launcher is used by USB and FocusFlow's existing wireless ADB. */
public final class BridgeLaunch {
    public static final String NAME="folio-pen-shared",LOG="/data/local/tmp/folio-pen-shared.log";
    public static String quote(String value){return "'"+value.replace("'","'\"'\"'")+"'";}
    public static String stopLegacy(){
        // Only the two known shell-owned legacy daemons; never an Android app process.
        return "ps -A -o USER,PID | while read user pid; do [ \"$user\" = shell ] || continue; "
            +"args=$(tr '\\000' ' ' < /proc/$pid/cmdline 2>/dev/null | sed 's/ *$//'); case \"$args\" in "
            +"'focusflow-bridge'|'app_process /system/bin ru.focusflow.pen.bridge.ProviderBridgeMain'|"
            +"'app_process /system/bin --nice-name=focusflow-bridge ru.focusflow.pen.bridge.ProviderBridgeMain'|"
            +"'app_process /system/bin ru.folio.notes.bridge.PenBridge') kill \"$pid\";; esac; done";
    }
    public static String command(String apk){
        return command(apk,false);
    }
    public static String command(String apk,boolean restart){
        // A unique copy avoids overwriting a jar mapped by another launch attempt.
        String stopShared="ps -A -o USER,PID | while read user pid; do [ \"$user\" = shell ] || continue; args=$(tr '\\000' ' ' < /proc/$pid/cmdline 2>/dev/null | sed 's/ *$//'); if [ \"$args\" = 'folio-pen-shared' ]; then kill \"$pid\"; fi; done; sleep 0.4; ";
        return (restart?stopShared:"")+stopLegacy()+"; if ! pidof "+NAME+" >/dev/null; then sleep 0.4; "
            +"jar=/data/local/tmp/folio-pen-shared-$$.apk; cp "+quote(apk)+" \"$jar\" || exit 11; "
            +"(CLASSPATH=\"$jar\" setsid nohup app_process /system/bin --nice-name="+NAME
            +" ru.folio.penbridge.SharedPenBridge > "+LOG+" 2>&1 < /dev/null &); sleep 2; fi; "
            +"if pidof "+NAME+" >/dev/null; then echo FOLIO_FOCUSFLOW_STARTED; else cat "+LOG+"; exit 12; fi";
    }
}
