package ru.folio.notes;

import android.animation.*;
import android.app.Dialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

/** Folio's small native design system. Motion never runs on the ink surface. */
final class Studio {
    static final int BG=0xff15171c,SURFACE=0xff202329,CARD=0xff292d35,LINE=0xff383d47,TEXT=0xfff4f2ed,MUTED=0xff999eab,ACCENT=0xffb5b3ff,MINT=0xffa8d8c7,CREAM=0xfff3efdf;
    static boolean motion(){return ValueAnimator.areAnimatorsEnabled();}
    static GradientDrawable surface(int color,float radius,float density){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius*density);d.setStroke(Math.max(1,(int)density),color==BG?0xff252830:LINE);return d;}
    static Drawable ripple(MainActivity a,int color,float radius){GradientDrawable shape=surface(color,radius,a.getResources().getDisplayMetrics().density);if(color==Color.TRANSPARENT)shape.setStroke(0,Color.TRANSPARENT);return new RippleDrawable(ColorStateList.valueOf(0x24c2c0ff),shape,null);}
    static void press(View v){v.setOnTouchListener((view,event)->{if(!motion())return false;int action=event.getActionMasked();if(action==0)view.animate().scaleX(.965f).scaleY(.965f).setDuration(100).start();else if(action==1||action==3)view.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new OvershootInterpolator(.8f)).start();return false;});}
    static void enter(View view,int delay){if(!motion())return;view.setAlpha(0);view.setTranslationY(14*view.getResources().getDisplayMetrics().density);view.animate().alpha(1).translationY(0).setStartDelay(Math.min(delay,180)).setDuration(340).setInterpolator(new DecelerateInterpolator(1.8f)).start();}
    static TextView button(MainActivity a,String label,boolean primary,Runnable action){TextView v=a.text(label,14,primary?BG:TEXT,true);v.setGravity(Gravity.CENTER);a.pad(v,19,14);v.setBackground(ripple(a,primary?ACCENT:CARD,18));v.setMinHeight(a.dp(48));v.setOnClickListener(x->action.run());press(v);return v;}
    static TextView eyebrow(MainActivity a,String text){TextView t=a.text(text,10,ACCENT,true);t.setLetterSpacing(.15f);a.pad(t,0,12);return t;}
    static EditText input(MainActivity a,String value,String hint){EditText e=new EditText(a);e.setText(value);e.setHint(hint);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(16);e.setPadding(a.dp(15),a.dp(12),a.dp(15),a.dp(12));e.setBackground(ripple(a,CARD,15));e.setSingleLine(true);return e;}
    static LinearLayout row(MainActivity a,String icon,String title,String detail,Runnable action){LinearLayout row=a.row();a.pad(row,14,13);row.setBackground(ripple(a,CARD,18));FrameLayout badge=new FrameLayout(a);badge.setBackground(a.bg(0xff363a48,13));Icons glyph=new Icons(a,icon);glyph.tint=ACCENT;badge.addView(glyph,new FrameLayout.LayoutParams(a.dp(38),a.dp(38),Gravity.CENTER));row.addView(badge,new LinearLayout.LayoutParams(a.dp(42),a.dp(42)));a.space(row,13);LinearLayout labels=a.column();TextView h=a.text(title,14,TEXT,true);h.setMaxLines(2);labels.addView(h);if(detail!=null&&!detail.isEmpty()){TextView d=a.text(detail,11,MUTED,false);d.setLineSpacing(a.dp(3),1);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2);dp.topMargin=a.dp(5);labels.addView(d,dp);}row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(action!=null){Icons arrow=new Icons(a,"next");arrow.tint=MUTED;row.addView(arrow,new LinearLayout.LayoutParams(a.dp(17),a.dp(22)));row.setOnClickListener(v->action.run());press(row);}return row;}
    static void addCard(MainActivity a,LinearLayout parent,View card){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(9);parent.addView(card,lp);}
    static String glyph(String text){String t=text.toLowerCase(Locale.ROOT);if(t.contains("ластик")||t.contains("стер"))return "eraser";if(t.contains("пер")||t.contains("инструмент")||t.contains("пенал"))return "pen";if(t.contains("циркуль"))return "compass";if(t.contains("папк"))return "folder";if(t.contains("цвет"))return "rainbow";if(t.contains("удал"))return "trash";if(t.contains("экспорт")||t.contains("pdf")||t.contains("png")||t.contains("folio"))return "export";if(t.contains("импорт"))return "import";if(t.contains("отмен"))return "undo";if(t.contains("повтор"))return "redo";if(t.contains("страниц")||t.contains("бумаг"))return "paper";if(t.contains("созда")||t.contains("добав"))return "plus";if(t.contains("масштаб"))return "search";if(t.contains("фокус"))return "hand";return "grid";}
    static final class Builder {
        final MainActivity a;String title="Folio",message;CharSequence[] items;DialogInterface.OnClickListener pick;
        View content,anchor;boolean compact;int widthDp,contentHeightDp;String positive,negative,neutral;DialogInterface.OnClickListener onPositive,onNegative,onNeutral;
        Builder(Context c){a=(MainActivity)c;}
        Builder setTitle(String title){this.title=title;return this;}Builder setMessage(String message){this.message=message;return this;}
        Builder setView(View content){this.content=content;return this;}Builder setItems(CharSequence[] items,DialogInterface.OnClickListener pick){this.items=items;this.pick=pick;return this;}
        Builder setPositiveButton(String text,DialogInterface.OnClickListener action){positive=text;onPositive=action;return this;}
        Builder setNegativeButton(String text,DialogInterface.OnClickListener action){negative=text;onNegative=action;return this;}
        Builder setNeutralButton(String text,DialogInterface.OnClickListener action){neutral=text;onNeutral=action;return this;}
        Builder anchor(View anchor){this.anchor=anchor;compact=anchor!=null;return this;}
        Builder width(int dp){widthDp=Math.max(240,Math.min(510,dp));return this;}Builder contentHeight(int dp){contentHeightDp=Math.max(180,Math.min(480,dp));return this;}
        Sheet create(){return new Sheet(this);}Sheet show(){Sheet d=create();d.show();return d;}
    }
    static final class Sheet extends Dialog {
        final MainActivity a;final Builder spec;final HashMap<Integer,TextView> buttons=new HashMap<>();LinearLayout card,content;FrameLayout popupLayer;final FrameClock clock=new FrameClock();GlassSurface glass;ViewTreeObserver.OnPreDrawListener firstDraw;boolean leaving,positioned,openingScheduled;Runnable afterDismiss;int popupX,popupY,targetWidth,targetHeight,openingFrames;float sourceScaleX,sourceScaleY,sourceDx,sourceDy,entranceFraction,firstOpeningScale;
        Sheet(Builder b){super(b.a);a=b.a;spec=b;requestWindowFeature(Window.FEATURE_NO_TITLE);setCanceledOnTouchOutside(true);SystemChrome.prepare(getWindow(),a.systemBarsVisible);}
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&a!=null&&a.resumed&&card!=null)SystemChrome.apply(getWindow(),a.systemBarsVisible);}
        protected void onCreate(Bundle state){super.onCreate(state);card=a.column();a.pad(card,spec.compact?14:22,spec.compact?13:18);if(spec.compact){glass=new GlassSurface(a.dp(26));card.setBackground(glass);}else card.setBackground(surface(SURFACE,30,a.getResources().getDisplayMetrics().density));card.setClipToOutline(true);
            LinearLayout head=a.row();LinearLayout titles=a.column();TextView brand=eyebrow(a,"FOLIO  /  STUDIO");brand.setTextSize(9);brand.setPadding(0,0,0,a.dp(8));if(!spec.compact)titles.addView(brand);TextView title=a.text(spec.title,spec.compact?18:25,TEXT,true);title.setLetterSpacing(-.025f);titles.addView(title);head.addView(titles,new LinearLayout.LayoutParams(0,-2,1));Icons close=a.icon("close","Закрыть",this::dismiss);close.tint=MUTED;close.setBackground(ripple(a,CARD,20));head.addView(close,new LinearLayout.LayoutParams(a.dp(spec.compact?28:37),a.dp(spec.compact?28:37)));card.addView(head);View gap=new View(a);card.addView(gap,new LinearLayout.LayoutParams(1,a.dp(spec.compact?10:22)));
            content=a.column();if(spec.message!=null){TextView text=a.text(spec.message,14,MUTED,false);text.setLineSpacing(a.dp(6),1);content.addView(text);}
            if(spec.items!=null)for(int i=0;i<spec.items.length;i++){final int index=i;String label=spec.items[i].toString();String[] split=label.split(" · ",2);LinearLayout item=row(a,glyph(label),split[0],split.length>1?split[1]:"",()->{if(spec.pick!=null)spec.pick.onClick(this,index);dismiss();});addCard(a,content,item);}
            if(spec.content!=null)content.addView(spec.content,new LinearLayout.LayoutParams(-1,-2));LimitScroll scroll=new LimitScroll(a,spec.compact,spec.contentHeightDp);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.addView(content);card.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout footer=a.row();if(spec.neutral!=null)addButton(footer,-3,spec.neutral,spec.onNeutral,false);a.stretch(footer);if(spec.negative!=null)addButton(footer,-2,spec.negative,spec.onNegative,false);if(spec.positive!=null)addButton(footer,-1,spec.positive,spec.onPositive,true);if(buttons.size()>1){for(int i=footer.getChildCount()-1;i>=0;i--){View child=footer.getChildAt(i);if(!(child instanceof TextView))footer.removeViewAt(i);else {TextView button=(TextView)child;button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setPadding(a.dp(10),a.dp(14),a.dp(10),a.dp(14));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1);bp.leftMargin=a.dp(4);bp.rightMargin=a.dp(4);button.setLayoutParams(bp);}}}if(buttons.size()>0){LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=a.dp(spec.compact?10:22);card.addView(footer,fp);}if(spec.compact){popupLayer=new FrameLayout(a);popupLayer.setClipChildren(false);popupLayer.setClipToPadding(false);popupLayer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r>l&&b>t){if(!positioned)positionPopover(true);else if(r-l!=or-ol||b-t!=ob-ot)positionPopover(false);}});popupLayer.setOnClickListener(v->dismiss());card.setClickable(true);card.setElevation(a.dp(18));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(a.dp(spec.widthDp>0?spec.widthDp:302),-2);popupLayer.addView(card,pp);setContentView(popupLayer);}else setContentView(card);
            Window w=getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=spec.compact?0:.46f;lp.windowAnimations=0;w.setAttributes(lp);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);SystemChrome.apply(w,a.systemBarsVisible);
        }
        void positionPopover(boolean first){
            if(leaving||!isShowing()||popupLayer.getWidth()<=a.dp(20)||popupLayer.getHeight()<=a.dp(20))return;
            Rect bounds=new Rect();boolean visible=spec.anchor.getGlobalVisibleRect(bounds);int[] origin=new int[2],source=new int[2];popupLayer.getLocationOnScreen(origin);
            if(visible){spec.anchor.getLocationOnScreen(source);bounds.set(source[0]-origin[0],source[1]-origin[1],source[0]-origin[0]+spec.anchor.getWidth(),source[1]-origin[1]+spec.anchor.getHeight());}
            else bounds.set(a.dp(20),a.dp(50),a.dp(70),a.dp(95));
            int width=Math.min(a.dp(spec.widthDp>0?spec.widthDp:302),popupLayer.getWidth()-a.dp(20));card.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(popupLayer.getHeight()-a.dp(20),View.MeasureSpec.AT_MOST));
            int[] place=PopoverLayout.place(0,0,popupLayer.getWidth(),popupLayer.getHeight(),bounds.left,bounds.top,bounds.right,bounds.bottom,width,card.getMeasuredHeight(),a.dp(10));
            boolean moved=popupX!=place[0]||popupY!=place[1]||targetWidth!=place[2]||targetHeight!=place[3];popupX=place[0];popupY=place[1];targetWidth=place[2];targetHeight=place[3];
            if(moved||!positioned){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(targetWidth,-2);lp.leftMargin=popupX;lp.topMargin=popupY;card.setLayoutParams(lp);}
            card.setPivotX(0);card.setPivotY(0);float seed=Math.min(bounds.width(),bounds.height());sourceScaleX=Math.max(.08f,Math.min(.36f,seed/targetWidth));sourceScaleY=Math.max(.07f,Math.min(.28f,seed/Math.max(1,targetHeight)));
            sourceDx=bounds.exactCenterX()-popupX-targetWidth*sourceScaleX/2;sourceDy=bounds.exactCenterY()-popupY-targetHeight*sourceScaleY/2;positioned=true;
            if(first&&!openingScheduled)waitForFirstDraw();else if(openingScheduled){if(moved)captureBackdrop();applyOpening(entranceFraction);}
        }
        void captureBackdrop(){if(glass==null)return;int[] origin=new int[2];popupLayer.getLocationOnScreen(origin);glass.capture(a,origin[0]+popupX,origin[1]+popupY,targetWidth,targetHeight);}
        void waitForFirstDraw(){
            openingScheduled=true;firstDraw=new ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){
                if(leaving||!isShowing()){removeFirstDraw();return true;}
                if(spec.compact&&(card.getLeft()!=popupX||card.getTop()!=popupY||card.getWidth()!=targetWidth)){card.requestLayout();return false;}
                removeFirstDraw();captureBackdrop();card.setVisibility(View.VISIBLE);
                if(!a.prefs.getBoolean("card_motion",true)){applyOpening(1);return true;}
                card.setLayerType(View.LAYER_TYPE_HARDWARE,null);
                applyOpening(0);firstOpeningScale=card.getScaleX();clock.start(PopoverMotion.OPEN_MS,t->{openingFrames++;applyOpening(t);},()->card.setLayerType(View.LAYER_TYPE_NONE,null));return true;
            }};card.getViewTreeObserver().addOnPreDrawListener(firstDraw);
        }
        void removeFirstDraw(){if(firstDraw!=null&&card!=null&&card.getViewTreeObserver().isAlive())card.getViewTreeObserver().removeOnPreDrawListener(firstDraw);firstDraw=null;}
        void childOpacity(float alpha){for(int i=0;i<card.getChildCount();i++)card.getChildAt(i).setAlpha(alpha);}
        void applyOpening(float t){
            entranceFraction=t;float expand=PopoverMotion.expansion(t);
            if(spec.compact){card.setScaleX(PopoverMotion.lerp(sourceScaleX,1,expand));card.setScaleY(PopoverMotion.lerp(sourceScaleY,1,expand));card.setTranslationX(sourceDx*(1-expand));card.setTranslationY(sourceDy*(1-expand));if(glass!=null){glass.radius(PopoverMotion.lerp(Math.min(targetWidth,targetHeight)/2f,a.dp(26),PopoverMotion.corner(t)));card.invalidateOutline();}}
            else {card.setPivotX(card.getWidth()/2f);card.setPivotY(card.getHeight()*.65f);card.setScaleX(.88f+.12f*expand);card.setScaleY(.88f+.12f*expand);card.setTranslationY(a.dp(24)*(1-expand));}
            card.setAlpha(PopoverMotion.opacity(t));childOpacity(PopoverMotion.content(t));
        }
        void addButton(LinearLayout row,int id,String title,DialogInterface.OnClickListener callback,boolean primary){TextView v=button(a,title,primary,()->{if(callback!=null)callback.onClick(this,id);dismiss();});buttons.put(id,v);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=a.dp(6);row.addView(v,lp);}
        TextView getButton(int which){return buttons.get(which);}
        protected void onStart(){super.onStart();card.setVisibility(View.INVISIBLE);if(spec.compact){getWindow().setLayout(-1,-1);popupLayer.post(()->{if(!positioned)positionPopover(true);});}else {getWindow().setLayout(Math.min(a.dp(spec.widthDp>0?spec.widthDp:510),a.getResources().getDisplayMetrics().widthPixels-a.dp(28)),-2);waitForFirstDraw();}}
        public void dismiss(){
            if(leaving)return;leaving=true;clock.cancel();removeFirstDraw();
            if(card==null||card.getVisibility()!=View.VISIBLE||!a.prefs.getBoolean("card_motion",true)||a.isFinishing()||a.isDestroyed()){finishDismiss();return;}
            card.setLayerType(View.LAYER_TYPE_HARDWARE,null);float sx=card.getScaleX(),sy=card.getScaleY(),dx=card.getTranslationX(),dy=card.getTranslationY(),alpha=card.getAlpha(),child=card.getChildCount()>0?card.getChildAt(0).getAlpha():1,radius=glass==null?0:glass.radius;
            clock.start(PopoverMotion.CLOSE_MS,t->{float k=PopoverMotion.closing(t);card.setAlpha(alpha*(1-k));childOpacity(child*(1-Math.min(1,t*2.7f)));card.setScaleX(PopoverMotion.lerp(sx,spec.compact?sourceScaleX:.9f,k));card.setScaleY(PopoverMotion.lerp(sy,spec.compact?sourceScaleY:.9f,k));card.setTranslationX(PopoverMotion.lerp(dx,spec.compact?sourceDx:0,k));card.setTranslationY(PopoverMotion.lerp(dy,spec.compact?sourceDy:a.dp(22),k));if(glass!=null){glass.radius(PopoverMotion.lerp(radius,Math.min(targetWidth,targetHeight)/2f,k));card.invalidateOutline();}},this::finishDismiss);
        }
        void dismissThen(Runnable action){if(leaving)return;afterDismiss=action;dismiss();}
        void finishDismiss(){if(card!=null)card.setLayerType(View.LAYER_TYPE_NONE,null);super.dismiss();Runnable next=afterDismiss;afterDismiss=null;if(next!=null&&a.resumed&&!a.isFinishing()&&!a.isDestroyed())next.run();}
        void dismissNow(){afterDismiss=null;leaving=true;clock.cancel();removeFirstDraw();if(card!=null)card.animate().cancel();finishDismiss();}
        protected void onStop(){clock.cancel();removeFirstDraw();if(glass!=null)glass.release();super.onStop();}
    }
    static final class LimitScroll extends ScrollView {final MainActivity a;final boolean compact;final int limit;LimitScroll(MainActivity a,boolean compact,int limit){super(a);this.a=a;this.compact=compact;this.limit=limit;}protected void onMeasure(int w,int h){int max=limit>0?Math.min(a.dp(limit),Math.max(a.dp(100),a.getResources().getDisplayMetrics().heightPixels-a.dp(180))):compact?Math.min(a.dp(286),a.getResources().getDisplayMetrics().heightPixels-a.dp(135)):Math.max(a.dp(150),a.getResources().getDisplayMetrics().heightPixels-a.dp(215));super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(h)>0?MeasureSpec.getSize(h):max,max),MeasureSpec.AT_MOST));}}
    interface FloatChange{void value(float value);}
    static final class Slider extends View {
        final MainActivity a;final Paint p=new Paint(3);float min,max,value;FloatChange change;String unit="pt";
        Slider(MainActivity a,float min,float max,float value,FloatChange change){super(a);this.a=a;this.min=min;this.max=max;this.value=value;this.change=change;setFocusable(true);setContentDescription("Размер: "+value);}
        protected void onDraw(Canvas c){float l=a.dp(14),r=getWidth()-l,y=getHeight()*.58f,f=(value-min)/(max-min);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(a.dp(5));p.setColor(LINE);c.drawLine(l,y,r,y,p);p.setColor(ACCENT);c.drawLine(l,y,l+(r-l)*f,y,p);for(int i=0;i<5;i++){p.setColor(0x558e94a3);c.drawCircle(l+(r-l)*i/4,y,a.dp(1),p);}float x=l+(r-l)*f;p.setColor(0x28b5b3ff);c.drawCircle(x,y,a.dp(14),p);p.setColor(TEXT);c.drawCircle(x,y,a.dp(7),p);p.setColor(BG);c.drawCircle(x,y,a.dp(2),p);}
        public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==0)getParent().requestDisallowInterceptTouchEvent(true);if(e.getActionMasked()==0||e.getActionMasked()==2||e.getActionMasked()==1){set(Ink.clamp((e.getX()-a.dp(14))/(getWidth()-a.dp(28)),0,1)*(max-min)+min);if(e.getActionMasked()==1)performClick();return true;}return true;}
        void set(float next){value=Ink.clamp(next,min,max);if(change!=null)change.value(value);setContentDescription("Размер: "+String.format(Locale.ROOT,"%.1f",value)+" "+unit);invalidate();}
        public boolean performClick(){super.performClick();return true;}
        public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.SeekBar");n.setRangeInfo(android.view.accessibility.AccessibilityNodeInfo.RangeInfo.obtain(1,min,max,value));n.addAction(4096);n.addAction(8192);}
        public boolean performAccessibilityAction(int action,Bundle args){if(action==4096||action==8192){set(value+(action==4096?1:-1)*(max-min)/20);return true;}return super.performAccessibilityAction(action,args);}
    }
    interface BoolChange{void value(boolean on);}
    static final class Toggle extends View {
        final MainActivity a;final Paint p=new Paint(3);boolean checked;float fraction;ValueAnimator animator;BoolChange change;
        Toggle(MainActivity a,boolean on,BoolChange change){super(a);this.a=a;checked=on;fraction=on?1:0;this.change=change;setFocusable(true);setClickable(true);setOnClickListener(v->{checked=!checked;if(animator!=null)animator.cancel();if(motion()){animator=ValueAnimator.ofFloat(fraction,checked?1:0);animator.setDuration(240);animator.setInterpolator(new DecelerateInterpolator());animator.addUpdateListener(t->{fraction=(float)t.getAnimatedValue();invalidate();});animator.start();}else {fraction=checked?1:0;invalidate();}change.value(checked);announceForAccessibility(checked?"Включено":"Выключено");});}
        protected void onDraw(Canvas c){float w=a.dp(40),h=a.dp(24),x=(getWidth()-w)/2,y=(getHeight()-h)/2;p.setColor(blend(LINE,ACCENT,fraction));c.drawRoundRect(new RectF(x,y,x+w,y+h),h/2,h/2,p);p.setColor(blend(MUTED,BG,fraction));c.drawCircle(x+a.dp(12)+a.dp(16)*fraction,y+h/2,a.dp(8),p);}
        public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.Switch");n.setCheckable(true);n.setChecked(checked);}
        protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }
    static int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    static Sheet busy(MainActivity a,String title,String description){LinearLayout box=a.column();FeatureDemo demo=new FeatureDemo(a,7);box.addView(demo,new LinearLayout.LayoutParams(-1,a.dp(160)));box.addView(a.text(description,14,MUTED,false));Sheet sheet=new Builder(a).setTitle(title).setView(box).create();sheet.setCancelable(false);sheet.show();return sheet;}
}
