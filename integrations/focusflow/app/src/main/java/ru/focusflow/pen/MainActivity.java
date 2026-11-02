package ru.focusflow.pen;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.graphics.Color;
import org.json.*;
import java.util.*;

public final class MainActivity extends Activity {
    private WebView web;
    private PenRuntime runtime;
    private ru.folio.penbridge.AutoAdbClient autoAdb;private boolean loaded;
    private String targetGesture="squeeze_double";private int targetIndex=-1;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);runtime=PenRuntime.get(this);if(state!=null){targetGesture=state.getString("targetGesture","squeeze_double");targetIndex=state.getInt("targetIndex",-1);}
        web=new WebView(this);web.setBackgroundColor(Color.rgb(16,18,23));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){loaded=true;showRequestedSetup();}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return !r.getUrl().toString().equals("file:///android_asset/index.html");}
        });
        web.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(web);web.loadUrl("file:///android_asset/index.html");LocalAdb.get(this).discover();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        InputDevice d=event.getDevice();
        if(runtime.service==null&&d!=null&&d.getName().toLowerCase(Locale.ROOT).contains("focus pen pro")&&event.getScanCode()==189&&event.getRepeatCount()==0){
            runtime.event(event.getAction()==KeyEvent.ACTION_DOWN,event.getEventTime(),"Android");
        }
        return super.dispatchKeyEvent(event);
    }
    public final class Bridge {
        @JavascriptInterface public String getState(){return runtime.state();}
        @JavascriptInterface public void save(String json){runOnUiThread(()->{
            try{runtime.update(new JSONObject(json));syncInputMode();web.evaluateJavascript("window.saved(true)",null);}
            catch(Exception e){web.evaluateJavascript("window.saved(false,"+JSONObject.quote(e.getMessage())+")",null);}
        });}
        @JavascriptInterface public void test(String gesture){runOnUiThread(()->runtime.execute(gesture));}
        @JavascriptInterface public void beginRecording(){runOnUiThread(runtime::beginRecording);}
        @JavascriptInterface public void endRecording(){runOnUiThread(runtime::endRecording);}
        @JavascriptInterface public void pickTarget(String kind,String gesture,int index){runOnUiThread(()->{targetGesture=gesture;targetIndex=index;startActivityForResult(new Intent(MainActivity.this,kind.equals("widget")?WidgetActivity.class:AppTargetsActivity.class),60);});}
        @JavascriptInterface public void recordScreen(String kind,String gesture,int index,int duration){runOnUiThread(()->{
            if(runtime.service==null){runtime.addLog("Для записи траектории включите службу FocusFlow");return;}
            if(!kind.equals("screen_path")&&!kind.equals("swipe_custom"))return;moveTaskToBack(true);runtime.handler.postDelayed(()->{if(runtime.service!=null)runtime.service.recordScreen(kind,gesture,index,duration);},400);
        });}
        @JavascriptInterface public void discoverAdb(){LocalAdb.get(MainActivity.this).refresh();}
        @JavascriptInterface public void pairAdb(int port,String code){LocalAdb.get(MainActivity.this).pair(port,code);}
        @JavascriptInterface public void startLocalAdb(int port){LocalAdb.get(MainActivity.this).start(port);}
        @JavascriptInterface public void addQuickTile(){runOnUiThread(()->{
            if(Build.VERSION.SDK_INT<33){tileMessage("Откройте шторку, нажмите изменение плиток и добавьте FocusFlow");return;}
            try{android.app.StatusBarManager manager=getSystemService(android.app.StatusBarManager.class);
                manager.requestAddTileService(new ComponentName(MainActivity.this,FocusFlowTileService.class),"FocusFlow",android.graphics.drawable.Icon.createWithResource(MainActivity.this,R.drawable.ic_tile_pen),getMainExecutor(),result->{
                    if(result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED||result==android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)tileMessage("Плитка FocusFlow добавлена в быстрые настройки");
                    else tileMessage("Плитку можно добавить вручную через изменение быстрых настроек");
                });
            }catch(RuntimeException e){tileMessage("Добавьте FocusFlow через изменение плиток в шторке");}
        });}
        @JavascriptInterface public void stopLocalAdb(){LocalAdb.get(MainActivity.this).stop();}
        @JavascriptInterface public void wirelessSettings(){runOnUiThread(()->{
            try{startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}
        });}
        @JavascriptInterface public void stop(){runOnUiThread(()->{runtime.cancel();runtime.addLog("Последовательность остановлена");});}
        @JavascriptInterface public void accessibility(){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));}
        @JavascriptInterface public void appSettings(){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));}
        @JavascriptInterface public String apps(){
            JSONArray list=new JSONArray();
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps=getPackageManager().queryIntentActivities(query,0);
            apps.sort((a,b)->String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager()))));
            Set<String> seen=new HashSet<>();
            for(ResolveInfo app:apps)try{
                String pkg=app.activityInfo.packageName;if(!seen.add(pkg))continue;
                list.put(new JSONObject().put("package",pkg).put("label",String.valueOf(app.loadLabel(getPackageManager()))));
            }catch(JSONException ignored){}
            return list.toString();
        }
        @JavascriptInterface public void exportConfig(){runOnUiThread(()->{
            Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,runtime.config.toString());
            startActivity(Intent.createChooser(intent,"Сохранить настройки FocusFlow"));
        });}
    }
    private void tileMessage(String message){runtime.addLog(message);if(!isFinishing()&&!isDestroyed())web.evaluateJavascript("toast("+JSONObject.quote(message)+")",null);}
    private void showRequestedSetup(){if(loaded&&getIntent().getBooleanExtra("openLocalAdb",false)){getIntent().removeExtra("openLocalAdb");web.evaluateJavascript("showPage('start')",null);}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);showRequestedSetup();}
    void syncInputMode(){if(runtime.config.optBoolean("nativePenOnly",true)){if(autoAdb!=null)autoAdb.pause();return;}if(autoAdb==null)autoAdb=new ru.folio.penbridge.AutoAdbClient(this);autoAdb.resume();}
    @Override protected void onResume(){super.onResume();syncInputMode();}
    @Override protected void onPause(){if(autoAdb!=null)autoAdb.pause();super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==60&&result==RESULT_OK&&data!=null)try{runtime.putAction(targetGesture,targetIndex,new JSONObject().put("type",data.getStringExtra("type")).put("label",data.getStringExtra("label")).put("value",data.getStringExtra("value")));}catch(Exception e){runtime.addLog("Не удалось сохранить: "+e.getMessage());}}
    @Override protected void onSaveInstanceState(Bundle b){b.putString("targetGesture",targetGesture);b.putInt("targetIndex",targetIndex);super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){if(autoAdb!=null)autoAdb.close();web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
}
