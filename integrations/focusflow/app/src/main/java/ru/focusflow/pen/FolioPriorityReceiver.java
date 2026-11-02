package ru.focusflow.pen;

import android.content.*;

/** The broadcast is only a wake-up hint. The boolean is read from Folio's provider. */
public final class FolioPriorityReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){PenRuntime.get(context).refreshFolioPriority();}
}
