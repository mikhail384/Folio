package ru.folio.penbridge;

/** Foreground startup policy, independent of Android and the ADB transport. */
public final class AutoStartGate {
    public static final String READY="ready",OFF="wifi_off",PAIR="pairing",SEARCH="searching",PORT="port_missing",START="starting",ERROR="error",UNSUPPORTED="unsupported";
    private long retryAt,searchAt=-1;private int failures;
    public static final class Decision {public final String state;public final boolean launch;Decision(String state,boolean launch){this.state=state;this.launch=launch;}}
    public Decision check(long now,boolean running,int wifi,boolean paired,int port,boolean busy,boolean supported){
        if(running){success();return new Decision(READY,false);}
        if(!supported)return new Decision(UNSUPPORTED,false);
        if(wifi==0){searchAt=-1;return new Decision(OFF,false);}
        if(!paired)return new Decision(PAIR,false);
        if(busy)return new Decision(START,false);
        if(now<retryAt)return new Decision(ERROR,false);
        if(port>0&&port<=65535){retryAt=now+30000;searchAt=-1;return new Decision(START,true);}
        if(searchAt<0)searchAt=now;
        return new Decision(now-searchAt>=8000?PORT:SEARCH,false);
    }
    public void failure(long now){failures=Math.min(6,failures+1);retryAt=now+failures*10000L;}
    public void success(){retryAt=0;failures=0;searchAt=-1;}
    public void retry(){retryAt=0;searchAt=-1;}
}
