package ru.folio.notes;

import java.util.function.Consumer;

/** Event-time pairing; UI timer callbacks only resolve a pending single or hold. */
final class SqueezeGestures {
    static final int DOUBLE_MS=350,HOLD_MS=500;
    long downAt=-1,firstUp=-1;
    boolean pressed,consumed,holdEnabled,doubleEnabled;
    boolean down(long time,boolean hold,boolean pair,Consumer<String> emit){
        if(pressed)return false;
        pressed=true;downAt=time;consumed=false;holdEnabled=hold;doubleEnabled=pair;
        if(firstUp>=0){long gap=time-firstUp;firstUp=-1;
            if(pair&&gap>=0&&gap<=DOUBLE_MS){consumed=true;emit.accept("double_squeeze");return true;}
            emit.accept("squeeze");
        }
        if(!hold&&!pair){consumed=true;emit.accept("squeeze");}
        return true;
    }
    boolean up(long time,Consumer<String> emit){
        if(!pressed)return false;
        if(!consumed&&holdEnabled&&time-downAt>=HOLD_MS)holdTimeout(emit);
        pressed=false;downAt=-1;
        if(!consumed){if(doubleEnabled)firstUp=time;else emit.accept("squeeze");}
        return true;
    }
    void singleTimeout(Consumer<String> emit){if(firstUp>=0){firstUp=-1;emit.accept("squeeze");}}
    void holdTimeout(Consumer<String> emit){if(pressed&&!consumed&&holdEnabled){consumed=true;emit.accept("hold");}}
    boolean awaitingHold(){return pressed&&!consumed&&holdEnabled;}
    boolean awaitingSingle(){return firstUp>=0;}
    void cancel(){downAt=firstUp=-1;pressed=consumed=holdEnabled=doubleEnabled=false;}
}
