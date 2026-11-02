package ru.folio.notes;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;

/** Isolated on-device regression. Never opens, writes or changes a user notebook/settings. */
public final class CompatibilityEraserTests extends Instrumentation {
    int checks;NotesView view;NotebookStore store;
    void check(boolean yes,String label){if(!yes)throw new AssertionError(label);checks++;}
    MotionEvent event(int action,long time,int[] types,int[] ids,float[] xs,float[] ys){
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[ids.length];
        for(int i=0;i<ids.length;i++){properties[i]=new MotionEvent.PointerProperties();properties[i].id=ids[i];properties[i].toolType=types[i];coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=xs[i];coordinates[i].y=ys[i];coordinates[i].pressure=.7f;coordinates[i].size=types[i]==MotionEvent.TOOL_TYPE_FINGER?.8f:.01f;}
        return MotionEvent.obtain(time,time,action,ids.length,properties,coordinates,0,0,1,1,-733,0,InputDevice.SOURCE_STYLUS,0);
    }
    void pen(int action,long time,float x,float y,int type){MotionEvent e=event(action,time,new int[]{type},new int[]{7},new float[]{view.tx+x*view.scale()},new float[]{view.ty+y*view.scale()});view.onTouchEvent(e);e.recycle();}
    Bitmap full(){Bitmap bitmap=Bitmap.createBitmap(view.cache.getWidth(),view.cache.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.scale(view.cacheScale,view.cacheScale);InkRenderer.page(canvas,view.page,null,Collections.emptySet());return bitmap;}
    void draw(){Bitmap frame=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(frame));frame.recycle();}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();Throwable[] failure={null};
        runOnMainSync(()->{try{
            Context isolated=new ContextWrapper(getTargetContext()){@Override public File getFilesDir(){File directory=new File(getCacheDir(),"folio-native-compat-fixture");directory.mkdirs();return directory;}};
            store=new NotebookStore(isolated);view=new NotesView(isolated,store);view.layout(0,0,900,700);view.autoShapes=false;view.edgeSwipe=false;view.showHover=false;
            Ink.Doc document=new Ink.Doc();Ink.Page page=document.page();page.infinite=false;page.width=320;page.height=240;page.pattern="blank";
            Ink.Layer locked=new Ink.Layer();locked.id="protected";locked.locked=true;page.layers.add(locked);
            for(int j=0;j<32;j++){Ink.Stroke stroke=new Ink.Stroke();stroke.tool="marker";stroke.width=30+j%5;stroke.color=j%2==0?0xffd4a24a:0xff9784b8;if(j==31)stroke.layer="protected";for(int i=0;i<150;i++)stroke.points.add(new Ink.Pt(10+i*2,100+(float)Math.sin(i*.03+j*.05)*10,1,0,i*.002f));page.strokes.add(stroke);}page.order();view.open(document);view.setTool("eraser");view.eraserWidth=26;
            long now=SystemClock.uptimeMillis();pen(0,now,65,100,2);draw();Bitmap before=view.cache.copy(Bitmap.Config.ARGB_8888,true);
            for(int i=1;i<=12;i++)pen(2,now+i,65+i*5,100+(i%3)*2,2);
            check(view.eraseDirty&&view.eraseDamage.dirty,"multiple input events wait for one raster frame");check(before.sameAs(view.cache),"cached image is not replayed for every stylus event");draw();check(!view.eraseDirty&&!view.eraseDamage.dirty,"draw flushes accumulated damage once");
            Bitmap reference=full();check(reference.sameAs(view.cache),"overlapping marker cache equals full native redraw, including alpha and protected ink");reference.recycle();before.recycle();
            pen(1,now+20,125,100,2);check(view.gestureBefore==null,"eraser gesture closes cleanly");for(Ink.Stroke stroke:page.strokes)if(stroke.layer.equals("protected"))check(stroke.cuts.isEmpty(),"protected marker never erased");
            view.undo();check(view.page.strokes.stream().allMatch(s->s.cuts.isEmpty()),"one undo restores complete eraser gesture");view.redo();check(view.page.strokes.stream().anyMatch(s->!s.cuts.isEmpty()),"redo restores swept erasure");
            int masks=view.page.strokes.stream().mapToInt(s->s.cuts.size()).sum();pen(0,now+30,210,100,2);pen(2,now+31,240,100,2);pen(3,now+32,240,100,2);check(view.page.strokes.stream().mapToInt(s->s.cuts.size()).sum()==masks,"cancel discards vector and queued raster changes");reference=full();check(reference.sameAs(view.cache),"cancel reconstructs original cached composition");reference.recycle();
            view.setTool("pen");int count=view.page.strokes.size();MotionEvent finger=event(0,now+40,new int[]{1},new int[]{3},new float[]{100},new float[]{100});view.onTouchEvent(finger);finger.recycle();check(view.ignoreTouch,"large initial palm is rejected");
            MotionEvent join=event(5|(1<<8),now+41,new int[]{1,2},new int[]{3,7},new float[]{100,view.tx+150*view.scale()},new float[]{100,view.ty+150*view.scale()});view.onTouchEvent(join);join.recycle();check(view.penId==7&&!view.ignoreTouch&&!view.twoTap,"stylus takes over a palm-first stream by pointer ID");
            MotionEvent lift=event(6|(1<<8),now+42,new int[]{1,2},new int[]{3,7},new float[]{100,view.tx+160*view.scale()},new float[]{100,view.ty+150*view.scale()});view.onTouchEvent(lift);lift.recycle();check(view.page.strokes.size()==count+1,"pen contact is retained without phantom two-finger undo");
            view.setTool("brush");pen(0,now+50,65,100,4);check(view.tool.equals("eraser")&&view.invertedReturnTool.equals("brush"),"inverted pen temporarily selects eraser");pen(1,now+51,75,100,4);check(view.tool.equals("brush")&&view.invertedReturnTool==null,"front tool restored after inverted eraser contact");
            view.setTool("hand");pen(0,now+60,65,100,4);check(view.tool.equals("eraser")&&!view.gestureNav,"inverted eraser overrides the hand navigation tool");pen(1,now+61,75,100,4);check(view.tool.equals("hand"),"navigation tool restored after inverted erase");
        }catch(Throwable error){failure[0]=error;}finally{if(view!=null){view.ui.removeCallbacksAndMessages(null);view.tiles.clear();if(view.cache!=null)view.cache.recycle();if(view.preview!=null)view.preview.recycle();}InkRenderer.COMMITTED.get().clear();if(store!=null)store.io.shutdownNow();}});
        if(failure[0]!=null){StringWriter trace=new StringWriter();failure[0].printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL "+checks+" native compatibility/eraser checks\n"+trace);finish(Activity.RESULT_CANCELED,result);}
        else {result.putString("stream","PASS "+checks+" native compatibility/eraser checks: batched marker composition, protected layers, undo/redo/cancel, palm-first stylus adoption and inverted eraser restoration. Simulated Android input, not physical pen certification.\n");finish(Activity.RESULT_OK,result);}
    }
}
