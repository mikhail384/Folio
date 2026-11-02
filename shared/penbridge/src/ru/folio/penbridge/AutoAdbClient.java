package ru.folio.penbridge;

import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** A visible client binds to FocusFlow's existing ADB identity. All IPC stays off the UI thread. */
public final class AutoAdbClient {
    public static final String DESCRIPTOR="ru.focusflow.pen.AUTO_ADB";
    public static final int CHECK=IBinder.FIRST_CALL_TRANSACTION,RETRY=CHECK+1;
    private final Activity activity;private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Notice notice;
    private volatile IBinder remote;private boolean active,bound,inFlight,retry;private long generation,opened,bindAfter;
    public AutoAdbClient(Activity activity){this.activity=activity;notice=new Notice();}
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){if(active)remote=binder;}
        public void onServiceDisconnected(ComponentName name){remote=null;}
        public void onBindingDied(ComponentName name){remote=null;unbind();}
        public void onNullBinding(ComponentName name){remote=null;unbind();}
    };
    private void bind(){if(bound||!active||SystemClock.uptimeMillis()<bindAfter)return;bindAfter=SystemClock.uptimeMillis()+5000;try{bound=activity.bindService(new Intent().setComponent(new ComponentName("ru.focusflow.pen","ru.focusflow.pen.AutoAdbService")),connection,Context.BIND_AUTO_CREATE);}catch(RuntimeException unavailable){bound=false;}}
    private void unbind(){if(bound)try{activity.unbindService(connection);}catch(IllegalArgumentException ignored){}bound=false;remote=null;}
    public void resume(){if(active)return;active=true;generation++;opened=SystemClock.uptimeMillis();bindAfter=0;notice.dismissed="";bind();ui.post(tick);}
    public void pause(){active=false;generation++;ui.removeCallbacks(tick);notice.hide();unbind();}
    public void close(){pause();worker.shutdownNow();}
    private final Runnable tick=()->poll();
    private void poll(){
        if(!active)return;ui.postDelayed(tick,1000);bind();if(inFlight)return;
        inFlight=true;final long turn=generation;final boolean force=retry;retry=false;final IBinder binder=remote;
        worker.execute(()->{
            Bundle state=new Bundle();state.putInt("wireless",WirelessDebugging.state(activity));state.putString("state","companion");
            boolean failed=false;if(binder!=null){Parcel input=Parcel.obtain(),output=Parcel.obtain();try{
                input.writeInterfaceToken(DESCRIPTOR);
                if(binder.transact(force?RETRY:CHECK,input,output,0)){output.readException();Bundle result=output.readBundle(AutoAdbClient.class.getClassLoader());if(result!=null&&result.getInt("autoProtocol",0)==1)state=result;}
            }catch(RemoteException|RuntimeException unavailable){failed=true;}finally{input.recycle();output.recycle();}}
            final Bundle result=state;final boolean lost=failed;
            ui.post(()->{inFlight=false;if(lost&&remote==binder){unbind();bind();}if(active&&turn==generation&&!activity.isDestroyed()){
                if("companion".equals(result.getString("state"))&&SystemClock.uptimeMillis()-opened<2500&&result.getInt("wireless",-1)!=0)return;
                notice.show(result);
            }});
        });
    }
    private void retry(){retry=true;bindAfter=0;notice.dismissed="";ui.removeCallbacks(tick);poll();}
    private void setup(){
        if(activity.getPackageName().equals("ru.focusflow.pen")){activity.getIntent().putExtra("openLocalAdb",true);View web=activity.findViewById(android.R.id.content);if(web instanceof ViewGroup){findSetup((ViewGroup)web);return;}}
        try{activity.startActivity(new Intent().setComponent(new ComponentName("ru.focusflow.pen","ru.focusflow.pen.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("openLocalAdb",true));}catch(ActivityNotFoundException|SecurityException unavailable){Toast.makeText(activity,"Установите обновление FocusFlow для автоматического запуска",Toast.LENGTH_LONG).show();}
    }
    private boolean findSetup(ViewGroup group){for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);if(v instanceof android.webkit.WebView){((android.webkit.WebView)v).evaluateJavascript("showPage('start')",null);return true;}if(v instanceof ViewGroup&&findSetup((ViewGroup)v))return true;}return false;}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private final class Notice {
        LinearLayout view;TextView title,body,button,again;ProgressBar progress;String rendered="",dismissed="",presentation="";int baseBottom=24;
        final boolean dark=activity.getPackageName().equals("ru.focusflow.pen");
        TextView text(float size,int color){TextView v=new TextView(activity);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
        void make(){
            view=new LinearLayout(activity);view.setClickable(true);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(dp(18),dp(12),dp(10),dp(12));view.setElevation(dp(14));
            GradientDrawable bg=new GradientDrawable();bg.setColor(dark?0xff242937:0xfff8f9ff);bg.setCornerRadius(dp(22));bg.setStroke(dp(1),dark?0xff41485e:0xffdfe3f2);view.setBackground(bg);
            progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleSmall);progress.setIndeterminateTintList(ColorStateList.valueOf(0xff8995f5));LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(dp(25),dp(25));icon.rightMargin=dp(14);view.addView(progress,icon);
            LinearLayout copy=new LinearLayout(activity);copy.setOrientation(LinearLayout.VERTICAL);title=text(14,dark?0xfff3f5ff:0xff26324c);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));body=text(12,dark?0xffbec6dc:0xff67758e);body.setPadding(0,dp(3),0,0);copy.addView(title);copy.addView(body);view.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout actions=new LinearLayout(activity);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(dp(10),0,0,0);button=text(13,dark?0xffb7bfff:0xff5d69bc);button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setGravity(Gravity.CENTER);button.setMinHeight(dp(44));button.setPadding(dp(12),0,dp(12),0);actions.addView(button);again=text(12,dark?0xffb7bfff:0xff5d69bc);again.setGravity(Gravity.CENTER);again.setMinHeight(dp(36));again.setText("Повторить");again.setOnClickListener(v->retry());actions.addView(again);view.addView(actions,new LinearLayout.LayoutParams(-2,-2));
            TextView close=text(22,dark?0xffaeb8d0:0xff7f8ba4);close.setText("×");close.setContentDescription("Скрыть уведомление подключения пера");close.setGravity(Gravity.CENTER);close.setOnClickListener(v->{dismissed=rendered;hide();});view.addView(close,new LinearLayout.LayoutParams(dp(40),dp(44)));
            view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        }
        void show(Bundle status){
            String code=status.getString("state","checking");int wifi=status.getInt("wireless",-1);boolean running=status.getBoolean("running",false);
            if(wifi==0)code="wifi_off";
            if("ready".equals(code)||"checking".equals(code)){hide();return;}
            String noticeKey=code+":"+running;if(noticeKey.equals(dismissed)){hide();return;}
            String headline,detail,action="Настройки";boolean waiting=false,repeat=false;Runnable click=()->WirelessDebugging.settings(activity);
            switch(code){
                case "wifi_off":headline="Беспроводная отладка выключена";detail=running?"Жесты работают. Включите отладку для автоматического повторного запуска.":"Включите её, чтобы жесты пера запускались автоматически.";break;
                case "pairing":headline="Подключите помощник один раз";detail="Выполните сопряжение по коду Android в FocusFlow.";action="Подключить";click=()->setup();break;
                case "searching":headline="Ищем подключение пера";detail="Определяем порт беспроводной отладки…";action="";waiting=true;break;
                case "starting":headline="Подключаем жесты пера";detail="Запускаем общий помощник Folio и FocusFlow…";action="";waiting=true;break;
                case "port_missing":headline="Не найден порт отладки";detail="Проверьте Wi-Fi и беспроводную отладку. Поиск продолжится автоматически.";repeat=true;break;
                case "error":headline="Помощник пока не подключён";detail="Проверьте сопряжение и подключение к Wi-Fi.";action="Подключить";click=()->setup();repeat=true;break;
                case "folio_disconnected":headline="Мост Folio не подключён";detail="FocusFlow отвечает, но Folio ещё не получает сигналы пера.";action="Перезапустить";click=()->retry();break;
                case "unsupported":headline="Нужен Android 11 или новее";detail="На этой версии Android запустите помощник по USB с компьютера.";break;
                default:headline="Не удалось связаться с FocusFlow";detail="Откройте установленный FocusFlow 1.7 или новее и проверьте подключение.";action="FocusFlow";click=()->setup();repeat=true;break;
            }
            String next=headline+"\n"+detail+action;if(view!=null&&view.getParent()!=null&&next.equals(presentation))return;
            if(view==null)make();title.setText(headline);body.setText(detail);progress.setVisibility(waiting?View.VISIBLE:View.GONE);button.setText(action);button.setVisibility(action.isEmpty()?View.GONE:View.VISIBLE);final Runnable target=click;button.setOnClickListener(v->target.run());again.setVisibility(repeat?View.VISIBLE:View.GONE);
            boolean added=view.getParent()==null;if(added){FrameLayout container=activity.findViewById(android.R.id.content);int available=container.getWidth()>0?container.getWidth():activity.getResources().getDisplayMetrics().widthPixels;FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.min(dp(660),available-dp(32)),-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.leftMargin=lp.rightMargin=dp(16);int inset=0;WindowInsets insets=container.getRootWindowInsets();if(insets!=null)inset=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.systemBars()).bottom:insets.getStableInsetBottom();lp.bottomMargin=dp(baseBottom)+inset;container.addView(view,lp);view.setAlpha(0);view.setTranslationY(dp(14));view.animate().alpha(1).translationY(0).setDuration(220).start();}rendered=noticeKey;presentation=next;
        }
        void hide(){if(view!=null&&view.getParent() instanceof ViewGroup){view.animate().cancel();((ViewGroup)view.getParent()).removeView(view);}rendered="";presentation="";}
    }
}
