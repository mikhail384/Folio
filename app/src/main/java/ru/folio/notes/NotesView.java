package ru.folio.notes;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;

/** Native ink surface. Fingers navigate, pen draws; hover never mutates a document. */
public final class NotesView extends View {
    interface Listener {void changed();void toolChanged();void page(int direction);void text(float x,float y);void telemetry(String s);void gesture(String name);}
    final NotebookStore store;
    final StylusDevices.Input input=new StylusDevices.Input();
    String invertedReturnTool;boolean invertedHover;
    Listener listener;
    Ink.Doc doc;
    Ink.Page page;
    final HashMap<String,Ink.History> histories=new HashMap<>();
    public String tool="pen",previous="pen";
    public int color=0xff28344b;
    final ToolColors toolColors=new ToolColors();final InkPresets presets=new InkPresets();
    public float width=3,eraserWidth=42;
    final HashMap<String,Float> widths=new HashMap<>();
    public boolean pressure=true,showHover=true,fingerDraw=false,edgeSwipe=true;
    public String eraserMode="area";int tapeTexture;
    public boolean rotationEnabled=true;
    public float barrelAngle=Float.NaN;
    public long barrelAt;
    final RulerGeometry ruler=new RulerGeometry();
    final RulerManipulation rulerManipulation=new RulerManipulation();
    String guideMode="none";int guideStroke;float guideEdge=Float.NaN;
    float compassX=600,compassY=800,compassRadius=180,compassStart,compassLast,compassSweep;
    public boolean compassCenterNext,compassPinned;
    final CompassTrace compassTrace=new CompassTrace();
    final LaserEffect laser=new LaserEffect();
    final LaserPainter laserPainter=new LaserPainter();
    public boolean autoShapes=true;
    ShapeRecognizer.Result snappedShape;Ink.Stroke shapeSource;float shapeHoldX,shapeHoldY;boolean shapeAnchorSet,shapeAttempted;
    final FrameClock shapeClock=new FrameClock();
    final Runnable shapeHold=()->recognizeHeldShape();
    Bitmap cache,background,preview;
    final HashSet<Ink.Stroke> previewHidden=new HashSet<>();
    final SpatialIndex spatial=new SpatialIndex();
    final CanvasTiles tiles=new CanvasTiles();
    final InkRenderer.Prepared liveInk=new InkRenderer.Prepared();
    final LiveInkRaster longInk=new LiveInkRaster();
    final RotationHistory rollHistory=new RotationHistory();
    final RadialPainter radialPainter=new RadialPainter();
    final Paint framePaint=new Paint(3);
    final Runnable saveViewport=()->{if(page!=null&&page.infinite&&listener!=null)listener.changed();};
    float cacheScale=1,fit=1,zoom=1,tx,ty,hoverX=-1,hoverY=-1,hoverTilt,hoverAngle,hoverAzimuth;
    long lastPen=-10000,downTime;
    Ink.Stroke draft;final ArrayList<Ink.Stroke> draftUpper=new ArrayList<>(),moveStack=new ArrayList<>();
    final Ink.Stroke hoverNib=new Ink.Stroke();
    final HashSet<Ink.Stroke> hoverHidden=new HashSet<>(),selection=new HashSet<>();
    final ArrayList<Ink.Pt> polygon=new ArrayList<>();
    ArrayList<Ink.Stroke> gestureBefore;
    final HashSet<Ink.Stroke> eraserWorking=new HashSet<>();
    final EraserDamage eraseDamage=new EraserDamage();
    boolean eraseDirty,eraseRebuild;
    float downX,downY,lastX,lastY,maxTravel,pinchDistance,panX,panY,initialPinch,initialPanX,initialPanY,moveDx,moveDy;
    int penId=-1,maxFingers;
    boolean twoTap,moving,erased,edge,gestureNav,ignoreTouch,stylusStream;
    float radialX,radialY;
    boolean radial,radialClosing;float radialProgress;
    final SmoothSpring radialValue=new SmoothSpring(0,24);
    final SpringDriver radialMotion=new SpringDriver(this,()->{radialProgress=radialValue.value;invalidate();},radialValue);
    final RadialPalette paletteState=new RadialPalette();
    final SmoothSpring[] paletteHighlights=RadialPalette.highlights();
    final SpringDriver paletteFeedback=new SpringDriver(this,this::invalidate,paletteHighlights);
    final RadialTools radialOrder=new RadialTools();
    final String[] radialTools=new String[8];
    final int[] colors={0xff28344b,0xff6979de,0xffe67f91,0xffe8b94d,0xff68a497,0xff9c7dc1,0xff4e9ec4,0xfff5f5f2};
    final Handler ui=new Handler(Looper.getMainLooper());
    final Runnable hideHover=()->{if(penId==-1){hoverX=-1;invertedHover=false;hoverHidden.clear();barrelAngle=Float.NaN;rollHistory.clear();invalidate();}};
    long telemetryAt;
    public NotesView(Context c,NotebookStore s){super(c);store=s;paletteTools(Arrays.asList("hand","pen","fountain","brush","pencil","marker","eraser","lasso"));radialMotion.settled=()->{if(radialValue.target==0){radialClosing=false;resetPaletteFeedback();invalidate();}};setFocusable(true);setContentDescription("Холст. Перо рисует, пальцы перемещают. Два пальца — отмена.");setBackgroundColor(0xffedf0f5);setDefaultFocusHighlightEnabled(false);}
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    public void open(Ink.Doc d){finishGesture(true);stashViewport();ui.removeCallbacks(saveViewport);doc=d;page=d.page();compassPinned=false;compassCenterNext=false;clearLaser();selection.clear();polygon.clear();hoverHidden.clear();hoverX=-1;invertedHover=false;closePalette();zoom=1;loadBackground();fitPage();indexAll();rebuild();}
    long cell(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    void commitStroke(Ink.Stroke s){page.strokes.add(s);int rank=page.rank(s.layer);if(rank==page.layers.size()-1){indexStroke(s);append(s);}else {page.order();indexAll();rebuild();}}
    void layersChanged(){selection.clear();hoverHidden.clear();page.order();indexAll();rebuild();changed();}
    void indexStroke(Ink.Stroke s){indexStroke(s,page.strokes.size()-1);}
    void indexStroke(Ink.Stroke s,int order){spatial.add(s,order);}
    void indexAll(){spatial.clear();if(page!=null)for(int i=0;i<page.strokes.size();i++)indexStroke(page.strokes.get(i),i);previewHidden.clear();}
    HashSet<Ink.Stroke> hits(float x,float y,float radius){HashSet<Ink.Stroke> found=new HashSet<>();for(Ink.Stroke s:spatial.query(x-radius,y-radius,x+radius,y+radius)){SpatialIndex.Entry entry=spatial.entries.get(s);if(page.editable(s)&&entry.geometry.hit(s,x,y,radius))found.add(s);}return found;}
    void loadBackground(){if(background!=null){background.recycle();background=null;}if(page!=null&&!page.background.isEmpty())try{background=store.bitmap(page.background,2400);}catch(Exception e){if(listener!=null)listener.telemetry("Изображение страницы недоступно");}}
    public void setTool(String name){if(GuideTools.auxiliary(name)){setGuide(GuideTools.mode(name));return;}if(!Arrays.asList(Ink.TOOLS).contains(name))return;finishGesture(false);if(!tool.equals(name))clearLaser();rememberColor();if(!tool.equals("eraser"))widths.put(tool,width);if(!tool.equals("eraser")&&!name.equals("eraser"))previous=name;tool=name;if(!name.equals("laser")&&ToolColors.enabled(name))color=toolColors.get(name,ColorDefaults.first(name));width=widths.containsKey(name)?widths.get(name):Ink.defaultWidth(name);if(name.equals("laser"))width=Ink.clamp(width,2,18);selection.clear();polygon.clear();hoverHidden.clear();if(listener!=null)listener.toolChanged();invalidate();}
    public void setGuide(String mode){if(!Arrays.asList("none","ruler","compass").contains(mode))return;finishGesture(false);rulerManipulation.end();guideMode=mode;hoverHidden.clear();if(listener!=null)listener.toolChanged();invalidate();}
    public void toggleEraser(){if(tool.equals("eraser"))setTool(previous);else {previous=tool;setTool("eraser");}}
    public String[] cycleTools={"pen","fountain","pencil","brush","marker"};
    public void cycle(){setTool(GuideTools.cycle(tool,cycleTools));}
    public void rotation(float degrees,long timestamp){
        long now=SystemClock.uptimeMillis();if(!rollHistory.add(degrees,timestamp>0?timestamp:now))return;
        barrelAngle=(float)Math.toRadians((degrees%360+360)%360);barrelAt=now;
        if(!rotationEnabled)return;hoverAngle=barrelAngle;
        if(!tool.equals("brush")&&!tool.equals("marker"))return;
        if(draft!=null&&snappedShape==null&&penId!=-1&&!draft.points.isEmpty()){Ink.Pt last=draft.points.get(draft.points.size()-1);if(Math.abs(InkGeometry.delta(last.angle,barrelAngle))>.01f&&draft.points.size()<300000)draft.points.add(new Ink.Pt(last.x,last.y,last.p,hoverTilt,barrelAngle,hoverAzimuth));}
        if(hoverX>=0||draft!=null)postInvalidateOnAnimation();
    }
    float nibAngle(MotionEvent e,int i){return rotationEnabled&&Float.isFinite(barrelAngle)?barrelAngle:0;}
    void observeStylus(MotionEvent e,int i){
        if(!StylusDevices.pen(e.getToolType(i)))return;
        boolean inverted=e.getToolType(i)==MotionEvent.TOOL_TYPE_ERASER;if(inverted&&!invertedHover)laser.clear();invertedHover=inverted;int old=input.deviceId,buttons=input.pressed(e);
        if(old!=Integer.MIN_VALUE&&old!=input.deviceId){barrelAngle=Float.NaN;rollHistory.clear();}
        if((buttons&MotionEvent.BUTTON_STYLUS_PRIMARY)!=0)penButton(0,e);
        if((buttons&MotionEvent.BUTTON_STYLUS_SECONDARY)!=0)penButton(1,e);
    }
    void penButton(int button,MotionEvent e){if(getContext() instanceof MainActivity)((MainActivity)getContext()).stylusButton(button,e.getDeviceId(),e.getEventTime(),StylusButtons.MOTION);else if(listener!=null)listener.gesture(button==0?"button_primary":"button_secondary");}
    boolean rotatingNib(){return tool.equals("brush");}
    Ink.History history(){return histories.computeIfAbsent(page.id,k->new Ink.History());}
    public void undo(){finishGesture(true);if(page==null)return;Ink.Page p=history().undo(page);replace(p);}
    public void redo(){finishGesture(true);if(page==null)return;Ink.Page p=history().redo(page);replace(p);}
    void replace(Ink.Page p){if(p==page)return;stashViewport();boolean modeChanged=p.infinite!=page.infinite||p.width!=page.width||p.height!=page.height;if(!modeChanged){p.hasViewport=page.hasViewport;p.viewX=page.viewX;p.viewY=page.viewY;p.viewZoom=page.viewZoom;}page=p;doc.pages.set(doc.current,p);selection.clear();hoverHidden.clear();loadBackground();indexAll();if(modeChanged){zoom=1;fitPage();}rebuild();changed();}
    public void paperChanged(){hoverHidden.clear();rebuild();changed();}
    public void checkpoint(){finishGesture(false);history().before(page);}
    void changed(){if(listener!=null)listener.changed();invalidate();}
    void stashViewport(){ui.removeCallbacks(saveViewport);if(page==null||!page.infinite||getWidth()==0||getHeight()==0||scale()<=0)return;page.viewX=Ink.clamp(px(getWidth()/2f),-CanvasViewport.LIMIT,CanvasViewport.LIMIT);page.viewY=Ink.clamp(py(getHeight()/2f),-CanvasViewport.LIMIT,CanvasViewport.LIMIT);page.viewZoom=Ink.clamp(zoom,CanvasViewport.MIN_ZOOM,CanvasViewport.MAX_ZOOM);page.hasViewport=true;}
    void rememberViewport(){stashViewport();ui.removeCallbacks(saveViewport);ui.postDelayed(saveViewport,500);}
    public void resetView(){zoom=1;if(page!=null&&page.infinite)page.hasViewport=false;fitPage();rememberViewport();invalidate();}
    void fitPage(){if(page==null||getWidth()==0||getHeight()==0)return;if(page.infinite){fit=dp(1);if(!page.hasViewport){float[] b=CanvasViewport.content(page);page.viewX=(b[0]+b[2])/2;page.viewY=(b[1]+b[3])/2;page.viewZoom=page.strokes.isEmpty()?1:Ink.clamp(Math.min(1,Math.min(Math.max(dp(100),getWidth()-dp(64))/((b[2]-b[0]+80)*fit),Math.max(dp(100),getHeight()-dp(200))/((b[3]-b[1]+80)*fit))),CanvasViewport.MIN_ZOOM,CanvasViewport.MAX_ZOOM);page.hasViewport=true;}zoom=page.viewZoom;tx=getWidth()/2f-page.viewX*fit*zoom;ty=getHeight()/2f-page.viewY*fit*zoom;}else {fit=Math.min((getWidth()-dp(40))/page.width,(getHeight()-dp(36))/page.height);fit=Math.max(.02f,fit);tx=(getWidth()-page.width*fit*zoom)/2;ty=(getHeight()-page.height*fit*zoom)/2;}}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){if(page!=null&&page.infinite&&oldw>0&&oldh>0){page.viewX=px(oldw/2f);page.viewY=py(oldh/2f);page.viewZoom=zoom;page.hasViewport=true;}fitPage();RulerOverlay.keepVisible(this);rebuild();}
    float scale(){return fit*zoom;}
    float px(float x){return (x-tx)/scale();}
    float py(float y){return (y-ty)/scale();}
    boolean onPaper(float x,float y){return page.infinite?Float.isFinite(px(x))&&Float.isFinite(py(y))&&Math.abs(px(x))<CanvasViewport.LIMIT&&Math.abs(py(y))<CanvasViewport.LIMIT:px(x)>=0&&py(y)>=0&&px(x)<=page.width&&py(y)<=page.height;}
    void rebuild(){tiles.clear();previewHidden.clear();if(page==null||getWidth()==0||getHeight()==0)return;if(page.infinite){if(cache!=null){cache.recycle();cache=null;}if(preview!=null){preview.recycle();preview=null;}invalidate();return;}float desired=Math.min(1.7f,Math.min(2200f/page.width,2500f/page.height));int w=Math.max(1,(int)(page.width*desired)),h=Math.max(1,(int)(page.height*desired));try{if(cache==null||cache.getWidth()!=w||cache.getHeight()!=h){if(cache!=null)cache.recycle();cache=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);}cacheScale=desired;Canvas c=new Canvas(cache);c.scale(desired,desired);InkRenderer.page(c,page,background,Collections.emptySet());}catch(OutOfMemoryError e){if(cache!=null){cache.recycle();cache=null;}if(listener!=null)listener.telemetry("Прямая отрисовка холста");}invalidate();}
    void append(Ink.Stroke stroke){if(page.infinite){tiles.append(stroke);invalidate();return;}if(cache==null){rebuild();return;}Canvas c=new Canvas(cache);c.scale(cacheScale,cacheScale);c.clipRect(0,0,page.width,page.height);InkRenderer.stroke(c,stroke);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(page==null)return;flushErasure();Paint p=framePaint;p.reset();p.setAntiAlias(true);p.setFilterBitmap(true);float s=scale();
        canvas.save();canvas.translate(tx,ty);canvas.scale(s,s);if(!page.infinite){p.setColor(0x12000000);canvas.drawRoundRect(new RectF(-3,4,page.width+3,page.height+8),5,5,p);p.setAlpha(255);}canvas.save();
        if(page.infinite){float l=px(0),t=py(0),r=px(getWidth()),bottom=py(getHeight());canvas.clipRect(l,t,r,bottom);HashSet<Ink.Stroke> hidden=new HashSet<>(hoverHidden);hidden.addAll(draftUpper);if(moving)hidden.addAll(moveStack);tiles.draw(this,canvas,page,background,spatial,hidden,eraserWorking,l,t,r,bottom,s);}
        else {canvas.clipRect(0,0,page.width,page.height);
        if(hoverHidden.isEmpty()&&!moving&&draftUpper.isEmpty()){if(cache!=null)canvas.drawBitmap(cache,null,new RectF(0,0,page.width,page.height),p);else InkRenderer.page(canvas,page,background,Collections.emptySet());}
        else {HashSet<Ink.Stroke> hidden=new HashSet<>(hoverHidden);hidden.addAll(draftUpper);if(moving)hidden.addAll(moveStack);if(cache!=null){if(preview==null||preview.getWidth()!=cache.getWidth()||preview.getHeight()!=cache.getHeight()){if(preview!=null)preview.recycle();preview=Bitmap.createBitmap(cache.getWidth(),cache.getHeight(),Bitmap.Config.ARGB_8888);previewHidden.clear();}if(!hidden.equals(previewHidden)){Canvas pc=new Canvas(preview);pc.scale(cacheScale,cacheScale);InkRenderer.page(pc,page,background,hidden);previewHidden.clear();previewHidden.addAll(hidden);}canvas.drawBitmap(preview,null,new RectF(0,0,page.width,page.height),p);}else InkRenderer.page(canvas,page,background,hidden);}
        }
        if(moving){for(Ink.Stroke stroke:moveStack){canvas.save();if(selection.contains(stroke))canvas.translate(moveDx,moveDy);InkRenderer.stroke(canvas,stroke);canvas.restore();}}
        if(draft!=null&&!longInk.draw(canvas,this,draft))InkRenderer.stroke(canvas,draft,liveInk);for(Ink.Stroke upper:draftUpper)InkRenderer.stroke(canvas,upper);
        long laserNow=SystemClock.uptimeMillis();if(tool.equals("laser"))laser.diameter=width;if(laser.hovering&&hoverX>=0){laser.x=px(hoverX);laser.y=py(hoverY);}laserPainter.draw(canvas,laser,laserNow,getResources().getDisplayMetrics().density/s);if(laser.needsFrame(laserNow)&&hasWindowFocus()&&isShown())postInvalidateOnAnimation();
        if(!polygon.isEmpty()){Path path=new Path();path.moveTo(polygon.get(0).x,polygon.get(0).y);for(Ink.Pt v:polygon)path.lineTo(v.x,v.y);p.setColor(0xff6979de);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f/s);p.setPathEffect(new DashPathEffect(new float[]{7/s,5/s},0));canvas.drawPath(path,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}
        if(!selection.isEmpty()){float[] b=selectionBounds();p.setColor(0xff6979de);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f/s);p.setPathEffect(new DashPathEffect(new float[]{6/s,4/s},0));canvas.drawRect(b[0]+moveDx-7,b[1]+moveDy-7,b[2]+moveDx+7,b[3]+moveDy+7,p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}
        if(guideMode.equals("compass"))drawCompass(canvas,p,s);
        if(tool.equals("protractor"))drawProtractor(canvas,p,s);
        boolean eraserCursor=tool.equals("eraser")||invertedHover;
        if((!tool.equals("laser")||eraserCursor)&&(showHover||eraserCursor&&penId!=-1)&&hoverX>=0&&!radial&&!radialClosing&&(penId==-1||eraserCursor)){float x=px(hoverX),y=py(hoverY);if(guideMode.equals("ruler")&&GuideTools.ink(tool)&&RulerOverlay.nearEdge(this,hoverX,hoverY)){Ink.Pt snap=RulerOverlay.project(this,new Ink.Pt(x,y,1,hoverTilt,hoverAngle,hoverAzimuth));x=snap.x;y=snap.y;}
            if(eraserCursor&&eraserMode.equals("area")&&penId==-1){canvas.save();Path circle=new Path();circle.addCircle(x,y,eraserWidth/2,Path.Direction.CW);canvas.clipPath(circle);if(page.infinite)InkRenderer.paperRegion(canvas,page,background,x-eraserWidth/2,y-eraserWidth/2,x+eraserWidth/2,y+eraserWidth/2,tiles.unit>0?tiles.unit:s);else InkRenderer.paperRegion(canvas,page,background,x-eraserWidth/2,y-eraserWidth/2,x+eraserWidth/2,y+eraserWidth/2,s);for(Ink.Stroke protectedInk:spatial.query(x-eraserWidth/2,y-eraserWidth/2,x+eraserWidth/2,y+eraserWidth/2))if(page.visible(protectedInk)&&!page.editable(protectedInk))InkRenderer.stroke(canvas,protectedInk);canvas.restore();}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f/s);p.setColor(eraserCursor?0xff8799b2:color);
            if(!eraserCursor&&rotatingNib()){hoverNib.tool="brush";hoverNib.width=width;InkGeometry.Tip tip=InkGeometry.tip(hoverNib,new Ink.Pt(x,y,1,hoverTilt,hoverAngle,hoverAzimuth));canvas.save();canvas.rotate((float)Math.toDegrees(tip.angle),x,y);canvas.drawOval(new RectF(x-tip.rx,y-tip.ry,x+tip.rx,y+tip.ry),p);canvas.restore();}
            else if(!eraserCursor&&tool.equals("marker")){hoverNib.tool="marker";hoverNib.width=width;InkGeometry.Tip tip=InkGeometry.tip(hoverNib,new Ink.Pt(x,y,1,hoverTilt,hoverAngle,hoverAzimuth));canvas.save();canvas.rotate((float)Math.toDegrees(tip.angle),x,y);canvas.drawRect(new RectF(x-tip.rx,y-tip.ry,x+tip.rx,y+tip.ry),p);canvas.restore();}
            else canvas.drawCircle(x,y,Math.max(2/s,(eraserCursor?eraserWidth:width)/2),p);
            p.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,1/s,p);
        }
        canvas.restore();canvas.restore();if(guideMode.equals("ruler"))RulerOverlay.draw(canvas,this);if(radial||radialClosing)drawRadial(canvas);
    }
    float[] selectionBounds(){float l=Float.MAX_VALUE,t=l,r=-l,b=-l;for(Ink.Stroke s:selection){float[] a=s.bounds();l=Math.min(l,a[0]);t=Math.min(t,a[1]);r=Math.max(r,a[2]);b=Math.max(b,a[3]);}return new float[]{l,t,r,b};}
    public void deleteSelection(){if(selection.isEmpty())return;history().before(page);page.strokes.removeAll(selection);selection.clear();indexAll();rebuild();changed();}
    public void copySelection(){if(selection.isEmpty())return;history().before(page);ArrayList<Ink.Stroke> copied=new ArrayList<>();HashMap<String,String> actions=new HashMap<>();for(Ink.Stroke s:page.strokes)if(selection.contains(s)){Ink.Stroke copy=s.translated(24,24);copy.action=actions.computeIfAbsent(s.action,k->Ink.id());copied.add(copy);}page.strokes.addAll(copied);page.order();selection.clear();selection.addAll(copied);indexAll();rebuild();changed();}
    public void transformSelection(float factor,float degrees){if(selection.isEmpty())return;history().before(page);float[] b=selectionBounds();HashSet<Ink.Stroke> next=new HashSet<>();for(int i=0;i<page.strokes.size();i++)if(selection.contains(page.strokes.get(i))){Ink.Stroke copy=page.strokes.get(i).transformed((b[0]+b[2])/2,(b[1]+b[3])/2,factor,(float)Math.toRadians(degrees));page.strokes.set(i,copy);next.add(copy);}selection.clear();selection.addAll(next);indexAll();rebuild();changed();}
    void revealTape(float x,float y){for(int i=page.strokes.size()-1;i>=0;i--){Ink.Stroke stroke=page.strokes.get(i);if(page.editable(stroke)&&stroke.tool.equals("tape")&&stroke.hit(x,y,2)){history().before(page);Ink.Stroke next=stroke.copy();next.revealed=!next.revealed;page.strokes.set(i,next);indexAll();rebuild();changed();return;}}}
    public void addText(String text,float x,float y){if(text.trim().isEmpty()||!page.writable())return;history().before(page);Ink.Stroke st=new Ink.Stroke();st.tool="text";st.layer=page.activeLayer;st.color=color;st.width=Math.max(22,width*5);st.text=text;st.points.add(new Ink.Pt(x,y,1,0,0));commitStroke(st);changed();}
    boolean toolbarMotion(){return !(getContext() instanceof MainActivity)||((MainActivity)getContext()).prefs.getBoolean("toolbar_motion",true);}
    void paletteTools(List<String> tools){if(!radialOrder.set(tools))return;closePalette();radialOrder.copyTo(radialTools);}
    void nextPalettePage(){radialOrder.next();radialOrder.copyTo(radialTools);resetPaletteFeedback();invalidate();}
    public void showPalette(){finishGesture(false);clearLaser();hoverHidden.clear();if(radial){hidePalette();return;}boolean reopening=radialClosing;radial=true;radialClosing=false;if(!reopening){radialOrder.first();radialOrder.copyTo(radialTools);resetPaletteFeedback();float x=hoverX>=0?hoverX:getWidth()/2f,y=hoverY>=0?hoverY:getHeight()/2f;radialX=Ink.clamp(x,dp(118),getWidth()-dp(118));radialY=Ink.clamp(y,dp(118),getHeight()-dp(118));radialValue.snap(0);radialProgress=0;}performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);radialMotion.to(toolbarMotion(),1);invalidate();}
    public void hidePalette(){radial=false;radialClosing=true;radialMotion.to(toolbarMotion(),0);invalidate();}
    public void closePalette(){radial=radialClosing=false;radialMotion.stop();radialValue.snap(0);radialProgress=0;resetPaletteFeedback();invalidate();}
    void resetPaletteFeedback(){paletteState.clear();paletteFeedback.stop();for(SmoothSpring spring:paletteHighlights)spring.snap(0);}
    int paletteHit(float x,float y){float reveal=.84f+.16f*Ink.clamp(radialProgress,0,1),dx=(x-radialX)/dp(reveal),dy=(y-radialY)/dp(reveal);if(radialOrder.pages()>1&&Math.hypot(dx,dy)<14)return RadialTools.PAGER;int hit=RadialPalette.hit(dx,dy);if(hit>=0&&hit<8&&radialTools[hit]==null)return -1;return !canColor()&&hit>=8?-1:hit;}
    void hoverPalette(float x,float y){int next=paletteHit(x,y);if(next==RadialTools.PAGER)next=-1;if(paletteState.hovered==next)return;paletteState.hovered=next;paletteFeedback.to(toolbarMotion(),paletteState.targets());}
    void drawRadial(Canvas c){radialPainter.draw(c,this);}
    boolean paletteTouch(MotionEvent e){if(radialClosing)return true;int action=e.getActionMasked();if(action==MotionEvent.ACTION_CANCEL){hidePalette();return true;}int slot=paletteHit(e.getX(),e.getY());hoverPalette(e.getX(),e.getY());if(action==MotionEvent.ACTION_UP){if(slot==RadialTools.PAGER){nextPalettePage();return true;}if(slot>=0&&slot<8)setTool(radialTools[slot]);else if(slot>=8){activeColor(colors[slot-8]);if(listener!=null)listener.toolChanged();}hidePalette();}return true;}
    @Override public boolean onHoverEvent(MotionEvent e){if(page==null)return true;int i=e.getActionIndex(),type=e.getToolType(i);if(!StylusDevices.pen(type))return false;observeStylus(e,i);
        if(radial||radialClosing){lastPen=SystemClock.uptimeMillis();ui.removeCallbacks(hideHover);hoverHidden.clear();if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){paletteState.clear();paletteFeedback.to(toolbarMotion(),paletteState.targets());ui.postDelayed(hideHover,100);}else {hoverX=e.getX(i);hoverY=e.getY(i);if(radial)hoverPalette(hoverX,hoverY);}return true;}
        lastPen=SystemClock.uptimeMillis();if(e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){if(tool.equals("laser")){laser.leave(SystemClock.uptimeMillis());invalidate();}ui.removeCallbacks(hideHover);ui.postDelayed(hideHover,100);return true;}ui.removeCallbacks(hideHover);hoverX=e.getX(i);hoverY=e.getY(i);hoverTilt=0;hoverAngle=nibAngle(e,i);hoverAzimuth=0;hoverHidden.clear();if(tool.equals("laser")&&!invertedHover&&showHover)laser.hover(px(hoverX),py(hoverY),SystemClock.uptimeMillis());
        if(showHover&&(tool.equals("eraser")||invertedHover)&&eraserMode.equals("stroke")){HashSet<Ink.Stroke> hit=hits(px(hoverX),py(hoverY),eraserWidth/2);HashSet<String> actions=new HashSet<>();for(Ink.Stroke stroke:hit)actions.add(stroke.action);for(String action:actions){Set<Ink.Stroke> group=spatial.actions.get(action);if(group!=null)for(Ink.Stroke member:group)if(page.editable(member))hoverHidden.add(member);}}report(e,i);invalidate();return true;
    }
    void report(MotionEvent e,int i){long now=SystemClock.uptimeMillis();if(listener!=null&&now-telemetryAt>500){telemetryAt=now;listener.telemetry(String.format(java.util.Locale.ROOT,"Перо · нажим %.2f · наклон %d°",e.getPressure(i),(int)Math.toDegrees(e.getAxisValue(MotionEvent.AXIS_TILT,i))));}}
    @Override public boolean onGenericMotionEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_BUTTON_PRESS||e.getActionMasked()==MotionEvent.ACTION_BUTTON_RELEASE){int i=e.getActionIndex();if(StylusDevices.pen(e.getToolType(i))){observeStylus(e,i);return true;}}return super.onGenericMotionEvent(e);}
    @Override public boolean onTouchEvent(MotionEvent e){if(page==null)return true;int action=e.getActionMasked(),ai=e.getActionIndex();int observed=penId==-1?ai:e.findPointerIndex(penId);if(observed>=0)observeStylus(e,observed);if(radial||radialClosing)return paletteTouch(e);
        if(action==MotionEvent.ACTION_POINTER_DOWN&&penId==-1&&StylusDevices.pen(e.getToolType(ai))){
            finishGesture(true);ignoreTouch=false;stylusStream=true;penId=e.getPointerId(ai);lastPen=SystemClock.uptimeMillis();ui.removeCallbacks(hideHover);hoverHidden.clear();
            downTime=e.getEventTime();downX=lastX=e.getX(ai);downY=lastY=e.getY(ai);maxFingers=1;maxTravel=0;twoTap=moving=edge=gestureNav=false;
            gestureNav=tool.equals("hand");if(e.getToolType(ai)==MotionEvent.TOOL_TYPE_ERASER&&!tool.equals("eraser")){String restore=tool;setTool("eraser");invertedReturnTool=restore;penId=e.getPointerId(ai);gestureNav=edge=false;}
            if(onPaper(downX,downY)&&!gestureNav)startPen(e,ai);else if(!gestureNav)ignoreTouch=true;return true;
        }
        if(RulerOverlay.touch(this,e))return true;
        if(action==MotionEvent.ACTION_DOWN){finishShapeAssist(true);ignoreTouch=false;gestureBefore=null;erased=false;moving=false;draft=null;guideStroke=0;polygon.clear();maxTravel=0;downTime=e.getEventTime();downX=lastX=e.getX();downY=lastY=e.getY();maxFingers=1;twoTap=false;edge=false;gestureNav=false;pinchDistance=0;
            int type=e.getToolType(0);stylusStream=StylusDevices.pen(type);
            if(stylusStream){penId=e.getPointerId(0);lastPen=SystemClock.uptimeMillis();ui.removeCallbacks(hideHover);hoverHidden.clear();
                edge=!page.infinite&&edgeSwipe&&(e.getX()<dp(30)||e.getX()>getWidth()-dp(30));gestureNav=tool.equals("hand")||edge;
                if(!onPaper(downX,downY)&&!gestureNav){ignoreTouch=true;return true;}
                if(type==MotionEvent.TOOL_TYPE_ERASER&&!tool.equals("eraser")){String restore=tool;setTool("eraser");invertedReturnTool=restore;penId=e.getPointerId(0);gestureNav=edge=false;}
                if(!gestureNav)startPen(e,0);
            }else {penId=-1;if(e.getSize()>0.4f){ignoreTouch=true;return true;}if(fingerDraw&&SystemClock.uptimeMillis()-lastPen>600&&!tool.equals("hand")){startPen(e,0);} }
            return true;
        }
        if(ignoreTouch)return true;
        if(penId!=-1){int i=e.findPointerIndex(penId);if(i<0){finishGesture(true);return true;}
            if(action==MotionEvent.ACTION_CANCEL||((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(ai)==penId)&&(e.getFlags()&MotionEvent.FLAG_CANCELED)!=0)){finishGesture(true);return true;}
            if(action==MotionEvent.ACTION_MOVE){lastPen=SystemClock.uptimeMillis();movePen(e,i);report(e,i);}
            if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(ai)==penId)){movePen(e,i);endPen(e,i);penId=-1;ignoreTouch=true;}
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN){
            if(stylusStream)return true;
            if(draft!=null||gestureBefore!=null||laser.touching)finishGesture(true);
            maxFingers=Math.max(maxFingers,e.getPointerCount());twoTap=maxFingers==2&&e.getEventTime()-downTime<250;initialPinch=pinchDistance=distance(e);initialPanX=panX=(e.getX(0)+e.getX(1))/2;initialPanY=panY=(e.getY(0)+e.getY(1))/2;return true;
        }
        if(action==MotionEvent.ACTION_MOVE){maxTravel=Math.max(maxTravel,Ink.dist(downX,downY,e.getX(),e.getY()));
            if(maxFingers>1){if(e.getPointerCount()>=2){float d=distance(e),cx=(e.getX(0)+e.getX(1))/2,cy=(e.getY(0)+e.getY(1))/2;if(Math.abs(d-initialPinch)>dp(5)||Ink.dist(cx,cy,initialPanX,initialPanY)>dp(7))twoTap=false;
                    if(!twoTap){float next=pinchDistance>0?Ink.clamp(zoom*d/pinchDistance,page.infinite?CanvasViewport.MIN_ZOOM:.5f,page.infinite?CanvasViewport.MAX_ZOOM:6):zoom;float[] origin=CanvasViewport.zoom(tx,ty,fit,zoom,next,panX,panY,cx,cy);zoom=next;tx=origin[0];ty=origin[1];invalidate();}pinchDistance=d;panX=cx;panY=cy;}}
            else if(fingerDraw&&draft!=null)movePen(e,0);
            else if(SystemClock.uptimeMillis()-lastPen>450){tx+=e.getX()-lastX;ty+=e.getY()-lastY;invalidate();}lastX=e.getX();lastY=e.getY();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP&&(e.getFlags()&MotionEvent.FLAG_CANCELED)!=0){finishGesture(true);twoTap=false;return true;}
        if(action==MotionEvent.ACTION_UP){if(maxFingers==2&&twoTap&&e.getEventTime()-downTime<350&&maxTravel<dp(20))undo();else if(maxFingers==1&&fingerDraw&&(draft!=null||gestureBefore!=null||moving||laser.touching||!polygon.isEmpty()))endPen(e,0);else if(maxFingers==1&&tool.equals("hand")&&maxTravel<dp(8))revealTape(px(e.getX()),py(e.getY()));if(page.infinite)rememberViewport();return true;}
        return true;
    }
    float distance(MotionEvent e){return e.getPointerCount()<2?0:Ink.dist(e.getX(0),e.getY(0),e.getX(1),e.getY(1));}
    Ink.Pt point(MotionEvent e,int i,int h){float x=h<0?e.getX(i):e.getHistoricalX(i,h),y=h<0?e.getY(i):e.getHistoricalY(i,h);
        float tilt=0;
        float azimuth=0;
        float angle=h<0?nibAngle(e,i):rotationEnabled&&Float.isFinite(barrelAngle)?rollHistory.at(e.getHistoricalEventTime(h),barrelAngle):azimuth;
        return new Ink.Pt(Ink.clamp(px(x),-CanvasViewport.LIMIT,CanvasViewport.LIMIT),Ink.clamp(py(y),-CanvasViewport.LIMIT,CanvasViewport.LIMIT),pressure?input.pressure(e,i,h):1,tilt,angle,azimuth);}

    Ink.Pt guidePoint(MotionEvent e,int i,int h){Ink.Pt sample=point(e,i,h);if(guideStroke!=1)return sample;guideEdge=RulerOverlay.followEdge(this,sample,guideEdge);return Float.isFinite(guideEdge)?RulerOverlay.project(this,sample,guideEdge):sample;}
    void prepareMoveLayers(){moveStack.clear();int rank=page.layers.size();for(Ink.Stroke s:selection)rank=Math.min(rank,page.rank(s.layer));for(Ink.Stroke s:page.strokes)if(page.visible(s)&&(selection.contains(s)||page.rank(s.layer)>rank))moveStack.add(s);}
    void prepareDraftLayers(){draftUpper.clear();int rank=page.rank(draft.layer);if(rank==page.layers.size()-1)return;for(Ink.Stroke stroke:page.strokes)if(page.visible(stroke)&&page.rank(stroke.layer)>rank)draftUpper.add(stroke);}
    void startPen(MotionEvent e,int i){float x=px(e.getX(i)),y=py(e.getY(i));
        hoverX=e.getX(i);hoverY=e.getY(i);hoverTilt=0;hoverAngle=nibAngle(e,i);hoverAzimuth=0;
        if(tool.equals("laser")){laser.down(x,y,SystemClock.uptimeMillis());invalidate();return;}
        if(tool.equals("protractor")){compassX=x;compassY=y;invalidate();return;}
        if((GuideTools.ink(tool)||tool.equals("text"))&&!page.writable()){if(listener!=null)listener.telemetry("Выберите видимый незаблокированный слой");return;}
        if(guideMode.equals("compass")&&GuideTools.ink(tool)){if(compassCenterNext||!compassPinned){compassX=x;compassY=y;compassCenterNext=false;compassPinned=true;invalidate();return;}draft=new Ink.Stroke();draft.tool=tool;draft.color=color;draft.width=width;draft.texture=tool.equals("tape")?tapeTexture:0;draft.layer=page.activeLayer;prepareDraftLayers();guideStroke=2;compassTrace.begin(draft,compassX,compassY,compassRadius,point(e,i,-1));compassStart=compassLast=compassTrace.last;compassSweep=0;invalidate();return;}
        if(tool.equals("text")){if(listener!=null)listener.text(x,y);return;}
        if(tool.equals("eraser")){gestureBefore=new ArrayList<>(page.strokes);eraserWorking.clear();erase(x,y);if(eraseDirty){refreshErasure();}return;}
        if(tool.equals("lasso")){if(!selection.isEmpty()){float[] b=selectionBounds();if(x>=b[0]-15&&x<=b[2]+15&&y>=b[1]-15&&y<=b[3]+15){moving=true;prepareMoveLayers();moveDx=moveDy=0;return;}}selection.clear();polygon.add(point(e,i,-1));invalidate();return;}
        draft=new Ink.Stroke();draft.tool=tool;draft.color=color;draft.width=width;draft.texture=tool.equals("tape")?tapeTexture:0;draft.layer=page.activeLayer;prepareDraftLayers();if(guideMode.equals("ruler")&&GuideTools.ink(tool)){RulerOverlay.place(this);guideStroke=1;guideEdge=Float.NaN;}draft.points.add(guidePoint(e,i,-1));invalidate();
    }
    void movePen(MotionEvent e,int i){maxTravel=Math.max(maxTravel,Ink.dist(downX,downY,e.getX(i),e.getY(i)));if(gestureNav){if(page.infinite){tx+=e.getX(i)-lastX;ty+=e.getY(i)-lastY;lastX=e.getX(i);lastY=e.getY(i);invalidate();}return;}
        hoverX=e.getX(i);hoverY=e.getY(i);hoverTilt=0;hoverAngle=nibAngle(e,i);hoverAzimuth=0;
        if(snappedShape!=null){float distance=Ink.dist(px(e.getX(i)),py(e.getY(i)),shapeHoldX,shapeHoldY)*scale();if(distance<=dp(12))return;shapeClock.cancel();draft=shapeSource;shapeSource=null;snappedShape=null;shapeAnchorSet=shapeAttempted=false;}
        if(tool.equals("laser")){long now=SystemClock.uptimeMillis();for(int h=0;h<e.getHistorySize();h++)laser.move(px(e.getHistoricalX(i,h)),py(e.getHistoricalY(i,h)),Math.min(now,e.getHistoricalEventTime(h)));laser.move(px(e.getX(i)),py(e.getY(i)),now);invalidate();return;}
        if(tool.equals("protractor")){compassStart=(float)Math.atan2(py(e.getY(i))-compassY,px(e.getX(i))-compassX);invalidate();return;}
        if(guideStroke==2&&draft!=null){for(int h=0;h<e.getHistorySize();h++)compassTrace.move(draft,point(e,i,h));compassTrace.move(draft,point(e,i,-1));compassSweep=compassTrace.sweep;compassLast=compassTrace.last;invalidate();return;}
        if(draft!=null&&(tool.equals("arrow")||tool.equals("triangle"))){Ink.Pt first=draft.points.get(0),last=point(e,i,-1);draft.points.clear();draft.points.add(first);if(tool.equals("triangle")){draft.points.add(new Ink.Pt(last.x,last.y,1,0,0));draft.points.add(new Ink.Pt(first.x*2-last.x,last.y,1,0,0));draft.points.add(first);}else{draft.points.add(last);float a=(float)Math.atan2(last.y-first.y,last.x-first.x),len=Math.max(18,width*4);draft.points.add(new Ink.Pt(last.x-len*(float)Math.cos(a-.5f),last.y-len*(float)Math.sin(a-.5f),1,0,0));draft.points.add(last);draft.points.add(new Ink.Pt(last.x-len*(float)Math.cos(a+.5f),last.y-len*(float)Math.sin(a+.5f),1,0,0));}invalidate();return;}
        if(tool.equals("eraser")){if(gestureBefore!=null){float x0=px(lastX),y0=py(lastY);for(int h=0;h<e.getHistorySize();h++){float x=px(e.getHistoricalX(i,h)),y=py(e.getHistoricalY(i,h));eraseSweep(x0,y0,x,y);x0=x;y0=y;}eraseSweep(x0,y0,px(e.getX(i)),py(e.getY(i)));if(eraseDirty){refreshErasure();}lastX=e.getX(i);lastY=e.getY(i);}invalidate();return;}
        if(tool.equals("lasso")){if(moving){moveDx=(e.getX(i)-downX)/scale();moveDy=(e.getY(i)-downY)/scale();}else polygon.add(point(e,i,-1));invalidate();return;}
        if(draft!=null){boolean shape=tool.equals("rect")||tool.equals("ellipse");if(shape){if(draft.points.size()>1)draft.points.remove(1);draft.points.add(point(e,i,-1));}else {for(int h=0;h<e.getHistorySize();h++)addPoint(guidePoint(e,i,h));addPoint(guidePoint(e,i,-1));armShapeAssist();}invalidate();}
    }
    void armShapeAssist(){if(guideStroke!=0||!autoShapes||draft==null||!ShapeRecognizer.eligible(tool)||draft.points.size()<2||snappedShape!=null)return;float x=px(hoverX),y=py(hoverY);if(!shapeAnchorSet||Ink.dist(x,y,shapeHoldX,shapeHoldY)*scale()>dp(2.5f)){shapeAnchorSet=true;shapeAttempted=false;shapeHoldX=x;shapeHoldY=y;ui.removeCallbacks(shapeHold);ui.postDelayed(shapeHold,420);}}
    void recognizeHeldShape(){if(!autoShapes||draft==null||shapeAttempted||snappedShape!=null||!ShapeRecognizer.eligible(tool)||penId==-1&&!fingerDraw)return;shapeAttempted=true;ShapeRecognizer.Result result=ShapeRecognizer.recognize(draft,dp(1)/scale());if(result==null)return;shapeSource=draft;snappedShape=result;performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);if(toolbarMotion())shapeClock.start(160,t->{if(snappedShape==result){draft=result.frame(t);invalidate();}},null);else {draft=result.stroke;invalidate();}}
    void finishShapeAssist(boolean cancel){ui.removeCallbacks(shapeHold);shapeClock.cancel();if(snappedShape!=null&&!cancel)draft=snappedShape.stroke;snappedShape=null;shapeSource=null;shapeAnchorSet=shapeAttempted=false;}
    void addPoint(Ink.Pt p){Ink.Pt last=draft.points.get(draft.points.size()-1);if((Ink.dist(last.x,last.y,p.x,p.y)>.35f||Math.abs(p.p-last.p)>.035f||Math.abs(p.tilt-last.tilt)>.025f||Math.abs(InkGeometry.delta(last.angle,p.angle))>.04f||Math.abs(InkGeometry.delta(last.azimuth,p.azimuth))>.04f)&&draft.points.size()<300000){float smoothing=guideStroke==1&&Float.isFinite(guideEdge)?1:.85f;draft.points.add(new Ink.Pt(last.x+(p.x-last.x)*smoothing,last.y+(p.y-last.y)*smoothing,last.p+(p.p-last.p)*.65f,p.tilt,p.angle,p.azimuth));}}
    void updateCompass(float x,float y){if(draft==null)return;compassTrace.move(draft,x,y);compassSweep=compassTrace.sweep;compassLast=compassTrace.last;}
    void drawCompass(Canvas c,Paint p,float s){
        boolean placing=compassCenterNext||!compassPinned;float x=placing?(hoverX>=0?px(hoverX):px(getWidth()/2f)):compassX,y=placing?(hoverX>=0?py(hoverY):py(getHeight()/2f)):compassY;
        p.setStyle(Paint.Style.STROKE);p.setColor(placing?0x777caef5:0x998699c4);p.setStrokeWidth(1.2f/s);p.setPathEffect(new DashPathEffect(new float[]{5/s,4/s},0));c.drawCircle(x,y,compassRadius,p);p.setPathEffect(null);
        p.setColor(0xff7d91b6);c.drawCircle(x,y,5/s,p);c.drawLine(x-10/s,y,x+10/s,y,p);c.drawLine(x,y-10/s,x,y+10/s,p);
        if(!placing){Ink.Pt end=new Ink.Pt(x+compassRadius*(float)Math.cos(compassTrace.last),y+compassRadius*(float)Math.sin(compassTrace.last),1,0,0);GuideArt.overlay(c,x,y,end.x,end.y,compassRadius,dp(1)/s);}
        p.setStyle(Paint.Style.FILL);p.setTextSize(dp(11)/s);c.drawText(String.format(Locale.ROOT,"R %.0f px · %s",compassRadius,placing?"коснитесь центра":"ведите перо по окружности"),x,y+compassRadius+dp(18)/s,p);
    }
    void drawProtractor(Canvas c,Paint p,float s){float r=dp(110)/s;c.save();c.translate(compassX,compassY);c.rotate((float)Math.toDegrees(compassStart));p.setColor(0x226492d2);p.setStyle(Paint.Style.FILL);c.drawArc(new RectF(-r,-r,r,r),180,180,true,p);p.setColor(0xff86a8d6);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1/s);c.drawArc(new RectF(-r,-r,r,r),180,180,false,p);c.drawLine(-r,0,r,0,p);p.setTextSize(dp(9)/s);p.setTextAlign(Paint.Align.CENTER);for(int degree=0;degree<=180;degree+=10){double a=Math.toRadians(180+degree);float len=dp(degree%30==0?12:7)/s;float dx=(float)Math.cos(a),dy=(float)Math.sin(a);c.drawLine(dx*(r-len),dy*(r-len),dx*r,dy*r,p);if(degree%30==0){p.setStyle(Paint.Style.FILL);c.drawText(""+degree,dx*(r-dp(24)/s),dy*(r-dp(24)/s)+dp(3)/s,p);p.setStyle(Paint.Style.STROKE);}}p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.LEFT);c.drawText(String.format(Locale.ROOT,"%.0f°",Math.toDegrees(compassStart)),0,dp(19)/s,p);c.restore();}
    void refreshErasure(){previewHidden.clear();postInvalidateOnAnimation();}
    void flushErasure(){
        if(!eraseDirty||page==null)return;eraseDirty=false;
        if(eraseRebuild&&!page.infinite)rebuild();
        else if(eraseDamage.dirty){if(page.infinite)tiles.erase(page,background,eraseDamage,spatial);
            else if(cache!=null){Canvas raster=new Canvas(cache);raster.scale(cacheScale,cacheScale);float[] b=eraseDamage.padded(2/Math.max(1e-12f,cacheScale));InkRenderer.eraseRegions(raster,page,background,eraseDamage,cacheScale,spatial.query(b[0],b[1],b[2],b[3]));}}
        eraseDamage.clear();eraseRebuild=false;previewHidden.clear();
    }
    void erase(float x,float y){eraseSweep(x,y,x,y);}
    void eraseSweep(float x,float y,float z,float w){
        float radius=eraserWidth/2;Ink.Cut cut=new Ink.Cut(x,y,radius,z,w);float[] box=cut.bounds();ArrayList<Ink.Stroke> found=new ArrayList<>();
        for(Ink.Stroke stroke:spatial.query(box[0],box[1],box[2],box[3])){
            boolean covered=false;for(int n=stroke.cuts.size()-1;n>=Math.max(0,stroke.cuts.size()-32);n--)if(stroke.cuts.get(n).covers(cut)){covered=true;break;}
            if(page.editable(stroke)&&!covered&&spatial.entries.get(stroke).geometry.sweep(stroke,x,y,z,w,radius))found.add(stroke);
        }
        if(found.isEmpty())return;
        if(eraserMode.equals("stroke")){
            HashSet<Ink.Stroke> remove=new HashSet<>();for(Ink.Stroke stroke:found){Set<Ink.Stroke> group=spatial.actions.get(stroke.action);if(group!=null)for(Ink.Stroke member:group)if(page.editable(member))remove.add(member);}
            for(Ink.Stroke stroke:remove){tiles.invalidate(spatial.entries.get(stroke).bounds);spatial.remove(stroke);}page.strokes.removeIf(remove::contains);spatial.reorder(page.strokes);if(!page.infinite)eraseRebuild=true;
        }else {
            for(Ink.Stroke old:found){SpatialIndex.Entry entry=spatial.entries.get(old);if(entry==null)continue;Ink.Stroke working=old;
                if(!eraserWorking.contains(old)){working=old.copy();page.strokes.set(entry.order,working);spatial.replaceMask(old,working);InkRenderer.erased(old,working);eraserWorking.add(working);}
                addCut(working,cut);
            }
            eraseDamage.add(cut);
        }
        erased=true;eraseDirty=true;
    }
    static void addCut(Ink.Stroke stroke,Ink.Cut cut){
        EraserDamage.append(stroke.cuts,cut);
    }
    void endPen(MotionEvent e,int i){
        if(gestureNav&&page.infinite){gestureNav=false;rememberViewport();return;}if(gestureNav){float dx=e.getX(i)-downX,dy=e.getY(i)-downY;if(Math.abs(dx)>dp(65)&&Math.abs(dx)>Math.abs(dy)*1.4f&&e.getEventTime()-downTime<1000){if(listener!=null)listener.page(dx<0?1:-1);}else if(tool.equals("hand")&&Math.abs(dy)>dp(65)&&Math.abs(dy)>Math.abs(dx)*1.4f){if(listener!=null)listener.page(dy<0?1:-1);}else if(tool.equals("hand")&&Math.abs(dx)+Math.abs(dy)<dp(8))revealTape(px(e.getX(i)),py(e.getY(i)));gestureNav=false;return;}
        finishGesture(false);
    }
    public void finishGesture(boolean cancel){
        if(cancel){eraseDamage.clear();eraseDirty=eraseRebuild=false;}else flushErasure();
        boolean did=false;finishShapeAssist(cancel);if(autoShapes&&guideStroke==0&&draft!=null&&!cancel&&!draft.tool.equals("line"))draft=ShapeRecognizer.alignTool(draft);if(cancel)laser.clear();else if(laser.touching)laser.release(SystemClock.uptimeMillis());
        if(draft!=null){if(!cancel&&!draft.points.isEmpty()){if(!draft.tool.equals("laser")){history().before(page);commitStroke(draft);did=true;}}draft=null;draftUpper.clear();}
        if(gestureBefore!=null){if(cancel){page.strokes=gestureBefore;indexAll();rebuild();}else if(erased){Ink.Page old=page.copy();old.strokes=gestureBefore;history().before(old);did=true;}gestureBefore=null;eraserWorking.clear();eraseDirty=false;erased=false;}
        if(moving){if(!cancel&&(Math.abs(moveDx)+Math.abs(moveDy)>1)){history().before(page);HashSet<Ink.Stroke> moved=new HashSet<>();for(int i=0;i<page.strokes.size();i++)if(selection.contains(page.strokes.get(i))){Ink.Stroke s=page.strokes.get(i).translated(moveDx,moveDy);page.strokes.set(i,s);moved.add(s);}selection.clear();selection.addAll(moved);did=true;}moving=false;moveStack.clear();moveDx=moveDy=0;rebuild();}
        else if(!polygon.isEmpty()){if(!cancel&&polygon.size()>2)for(Ink.Stroke s:page.strokes){if(!page.editable(s))continue;for(Ink.Pt p:s.points)if(Ink.inside(p.x,p.y,polygon)){selection.add(s);break;}}polygon.clear();}
        liveInk.clear();longInk.clear();compassTrace.clear();guideStroke=0;guideEdge=Float.NaN;penId=-1;if(did){if(tool.equals("lasso"))indexAll();changed();}String restore=invertedReturnTool;invertedReturnTool=null;if(restore!=null)setTool(restore);invalidate();
    }
    public void clearLaser(){if(laser!=null)laser.clear();invalidate();}
    public int activeColor(){return tool.equals("laser")?laser.color:color;}
    public boolean canColor(){return ToolColors.enabled(tool);}
    void rememberColor(){toolColors.put(tool,activeColor());}
    int colorFor(String name){if(!ToolColors.enabled(name))return 0xff28344b;return name.equals(tool)?activeColor():toolColors.get(name,name.equals("laser")?laser.color:ColorDefaults.first(name));}
    void colorFor(String name,int value){toolColors.put(name,value);if(name.equals("laser"))laser.color=value;else if(name.equals(tool)&&ToolColors.enabled(name))color=value;invalidate();}
    public void activeColor(int value){if(!canColor())return;colorFor(tool,value);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus){clearLaser();closePalette();ui.removeCallbacks(shapeHold);shapeClock.cancel();}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&laser!=null){clearLaser();if(radialMotion!=null)closePalette();if(shapeClock!=null){ui.removeCallbacks(shapeHold);shapeClock.cancel();}}}
    @Override protected void onDetachedFromWindow(){liveInk.clear();longInk.release();InkRenderer.COMMITTED.get().clear();radialPainter.clear();clearLaser();tiles.release();finishShapeAssist(true);closePalette();ui.removeCallbacksAndMessages(null);super.onDetachedFromWindow();if(cache!=null){cache.recycle();cache=null;}if(preview!=null){preview.recycle();preview=null;}if(background!=null){background.recycle();background=null;}}
}
