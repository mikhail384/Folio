package ru.focusflow.pen;

import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

final class FolioPriority {
    static Bundle status(Context context){
        try{context.getPackageManager().getPackageInfo("ru.folio.notes",0);}catch(PackageManager.NameNotFoundException absent){return null;}catch(RuntimeException unavailable){return new Bundle();}
        try{Bundle value=context.getContentResolver().call(Uri.parse("content://ru.folio.notes.pen"),"priority",null,null);return value==null?new Bundle():value;}
        catch(RuntimeException unavailable){return new Bundle();}
    }
    static boolean active(Context context){Bundle value=status(context);return value!=null&&(!value.containsKey("active")||value.getBoolean("active"));}
}
