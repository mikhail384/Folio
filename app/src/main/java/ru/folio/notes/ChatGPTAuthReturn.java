package ru.folio.notes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Returns the user's existing Folio task to the foreground after loopback OAuth succeeds. */
public final class ChatGPTAuthReturn extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(open);
        finish();
    }
}
