package ru.focusflow.pen;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.lang.ref.WeakReference;

/** Quick Settings toggles the same persisted enabled state as the app. */
public final class FocusFlowTileService extends TileService {
    private static WeakReference<FocusFlowTileService> current=new WeakReference<>(null);
    @Override public void onCreate(){super.onCreate();current=new WeakReference<>(this);}
    @Override public void onTileAdded(){super.onTileAdded();render();}
    @Override public void onStartListening(){super.onStartListening();render();}
    @Override public void onClick(){
        super.onClick();PenRuntime runtime=PenRuntime.get(this);
        try{
            boolean enabled=!runtime.config.optBoolean("enabled",true);
            runtime.setEnabled(enabled);runtime.addLog(enabled?"Быстрые настройки: жесты включены":"Быстрые настройки: жесты выключены");render();
            if(enabled&&(runtime.service==null||!runtime.bridgeConnected)){
                Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,42,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
                else startActivityAndCollapse(intent);
            }
        }catch(Exception e){runtime.addLog("Не удалось переключить FocusFlow: "+e.getClass().getSimpleName());render();}
    }
    private void render(){
        Tile tile=getQsTile();if(tile==null)return;
        PenRuntime runtime=PenRuntime.get(this);boolean enabled=runtime.config.optBoolean("enabled",true);
        String description=!enabled?"Выключено":runtime.priorityFence.blocked()?"Приоритет Folio":runtime.stopRequested?"Команды остановлены":runtime.service==null?"Нужна служба управления":!runtime.bridgeConnected?"Нужен запуск помощника":"Жесты включены";
        tile.setLabel("FocusFlow");tile.setIcon(Icon.createWithResource(this,R.drawable.ic_tile_pen));
        tile.setState(enabled?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);
        if(Build.VERSION.SDK_INT>=29)tile.setSubtitle(description);
        tile.setContentDescription("FocusFlow: "+description);tile.updateTile();
    }
    public static void refresh(Context context){
        FocusFlowTileService service=current.get();if(service!=null)service.render();
        try{requestListeningState(context,new ComponentName(context,FocusFlowTileService.class));}catch(RuntimeException ignored){}
    }
    @Override public void onDestroy(){if(current.get()==this)current.clear();super.onDestroy();}
}
