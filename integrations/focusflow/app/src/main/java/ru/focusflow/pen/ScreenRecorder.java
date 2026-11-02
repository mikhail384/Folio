package ru.focusflow.pen;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** User-visible, temporary accessibility overlay. Never captures the screen or other apps' text. */
public final class ScreenRecorder {
    private final PenService service;private final PenRuntime runtime;private final WindowManager windows;
    private FrameLayout root;private DrawView drawing;private String target,mode;private int edit,duration;
    private final JSONArray strokes=new JSONArray();private ArrayList<float[]> current;private long firstDown,strokeDown;private boolean capturing;
    public ScreenRecorder(PenService service){this.service=service;runtime=PenRuntime.get(service);windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);}
    public void open(String kind,String gesture,int index,int durationMs){
        close();runtime.cancel();runtime.endRecording();runtime.resetDetector();mode=kind;target=gesture;edit=index;duration=Math.max(50,Math.min(30000,durationMs));firstDown=0;
        while(strokes.length()>0)strokes.remove(0);
        root=new FrameLayout(service);drawing=new DrawView(service);drawing.setVisibility(View.GONE);root.addView(drawing,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(service);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(10),dp(14),dp(10));panel.setBackgroundColor(0xf5222532);
        TextView title=new TextView(service);title.setText("Откройте нужное приложение и нажмите «Запись»");title.setTextColor(Color.WHITE);title.setTextSize(15);panel.addView(title);
        TextView hint=new TextView(service);hint.setText("Пока запись не началась, экран работает как обычно.");hint.setTextColor(0xffb7bfd1);hint.setTextSize(12);panel.addView(hint);
        LinearLayout buttons=new LinearLayout(service);panel.addView(buttons);
        button(buttons,"Запись",()->{if(capturing)return;WindowManager.LayoutParams full=(WindowManager.LayoutParams)root.getLayoutParams();full.height=-1;drawing.setVisibility(View.VISIBLE);windows.updateViewLayout(root,full);capturing=true;title.setText(kind.equals("swipe_custom")?"Свайп: отметьте начало и конец двумя касаниями":"Нарисуйте траекторию. Можно несколько линий.");hint.setText("Касания записываются. Панель можно перетащить за заголовок.");});
        button(buttons,"Очистить",()->{while(strokes.length()>0)strokes.remove(0);drawing.straight.clear();current=null;firstDown=0;drawing.invalidate();});
        button(buttons,"Сохранить",()->save());button(buttons,"Отмена",()->{close();returnToApp();});
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);p.setMargins(dp(16),dp(45),dp(16),0);root.addView(panel,p);
        final float[] drag=new float[2];title.setOnTouchListener((v,e)->{if(!capturing)return false;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){drag[0]=e.getRawY();drag[1]=p.topMargin;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){p.topMargin=(int)Math.max(0,Math.min(root.getHeight()-panel.getHeight(),drag[1]+e.getRawY()-drag[0]));panel.setLayoutParams(p);return true;}return true;});
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("FocusFlow · запись траектории");
        try{windows.addView(root,params);runtime.screenRecording=true;}catch(Exception e){close();runtime.addLog("Не удалось открыть запись траектории");returnToApp();}
    }
    private int dp(int v){return (int)(v*service.getResources().getDisplayMetrics().density+.5f);}
    private void button(LinearLayout group,String label,Runnable action){Button b=new Button(service);b.setText(label);b.setTextSize(12);b.setOnClickListener(v->action.run());group.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));}
    public void close(){capturing=false;runtime.screenRecording=false;runtime.resetDetector();if(root!=null){try{windows.removeView(root);}catch(Exception ignored){}root=null;}current=null;}
    private void returnToApp(){service.startActivity(new Intent(service,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}
    private void save(){
        if(strokes.length()==0){Toast.makeText(service,"Сначала нарисуйте линию или отметьте обе точки",Toast.LENGTH_SHORT).show();return;}
        try{JSONObject value=ScreenPath.validate(new JSONObject().put("strokes",strokes));runtime.putAction(target,edit,new JSONObject().put("type",mode).put("label",mode.equals("swipe_custom")?"Свайп между точками":"Нарисованная траектория").put("value",value.toString()));close();returnToApp();}
        catch(Exception e){Toast.makeText(service,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private final class DrawView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final DisplayMetrics metrics=new DisplayMetrics();
        private final List<float[]> straight=new ArrayList<>();
        DrawView(Context c){super(c);setBackgroundColor(0x08000000);windows.getDefaultDisplay().getRealMetrics(metrics);}
        private float[] point(MotionEvent event){return new float[]{Math.max(0,Math.min(100,event.getRawX()*100f/(metrics.widthPixels-1))),Math.max(0,Math.min(100,event.getRawY()*100f/(metrics.heightPixels-1)))};}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(!capturing)return true;if(e.getPointerCount()>1)return true;
            long now=SystemClock.uptimeMillis();
            if(mode.equals("swipe_custom")){
                if(e.getActionMasked()==MotionEvent.ACTION_UP){if(straight.size()>=2){straight.clear();while(strokes.length()>0)strokes.remove(0);}straight.add(point(e));if(straight.size()==2)store(straight,0,duration);invalidate();}return true;
            }
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:if(strokes.length()>=10){Toast.makeText(service,"Можно записать до 10 линий",0).show();return true;}if(firstDown==0)firstDown=now;strokeDown=now;current=new ArrayList<>();current.add(point(e));break;
                case MotionEvent.ACTION_MOVE:if(current!=null&&current.size()<255){float[] p=point(e),last=current.get(current.size()-1);if(Math.abs(p[0]-last[0])+Math.abs(p[1]-last[1])>.25f)current.add(p);}break;
                case MotionEvent.ACTION_UP:if(current!=null){current.add(point(e));int start=(int)(strokeDown-firstDown),length=(int)Math.max(50,now-strokeDown);if(start+length<=30000)store(current,start,length);else Toast.makeText(service,"Запись ограничена 30 секундами",0).show();current=null;}break;
                case MotionEvent.ACTION_CANCEL:current=null;break;
            }invalidate();return true;
        }
        private void store(List<float[]> points,int start,int ms){try{JSONArray p=new JSONArray();for(float[] xy:points)p.put(new JSONArray().put(Math.round(xy[0]*100)/100d).put(Math.round(xy[1]*100)/100d));strokes.put(new JSONObject().put("points",p).put("startMs",start).put("durationMs",ms));}catch(JSONException ignored){}}
        private void line(Canvas c,List<float[]> points){if(points.isEmpty())return;int[] at=new int[2];getLocationOnScreen(at);Path path=new Path();float x=0,y=0;for(int i=0;i<points.size();i++){float[] p=points.get(i);x=p[0]*(metrics.widthPixels-1)/100-at[0];y=p[1]*(metrics.heightPixels-1)/100-at[1];if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setColor(0xffb8a2ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);float[] a=points.get(0);paint.setColor(0xff80d9ba);c.drawCircle(a[0]*(metrics.widthPixels-1)/100-at[0],a[1]*(metrics.heightPixels-1)/100-at[1],dp(7),paint);paint.setColor(0xffffb076);c.drawCircle(x,y,dp(7),paint);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);for(int i=0;i<strokes.length();i++){JSONArray p=strokes.optJSONObject(i).optJSONArray("points");List<float[]> list=new ArrayList<>();for(int j=0;j<p.length();j++){JSONArray xy=p.optJSONArray(j);list.add(new float[]{(float)xy.optDouble(0),(float)xy.optDouble(1)});}line(c,list);}if(current!=null)line(c,current);if(mode.equals("swipe_custom")&&strokes.length()==0)line(c,straight);}
    }
}
