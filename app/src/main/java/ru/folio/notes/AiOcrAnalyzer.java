package ru.folio.notes;

import android.content.Context;
import android.graphics.*;
import com.googlecode.tesseract.android.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Local OCR only. The renderer and model never receive non-consenting notebooks. */
final class AiOcrAnalyzer implements AutoCloseable {
    private final TessBaseAPI tess;
    private boolean closed;
    AiOcrAnalyzer(Context context)throws Exception{tess=OcrStudio.create(context,TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT);}
    JSONArray analyze(Ink.Doc doc,Ink.Page page,BooleanSupplier allowed)throws Exception{
        JSONArray results=new JSONArray();HashSet<String> ids=new HashSet<>();
        for(AiLearningCore.Tile tile:AiLearningCore.tiles(page)){
            if(!allowed.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException("Анализ приостановлен");
            float left=tile.l-16,top=tile.t-16,width=Math.max(32,tile.r-tile.l+32),height=Math.max(32,tile.b-tile.t+32),scale=Math.min(2f,1800f/Math.max(width,height));
            if(!Float.isFinite(scale)||scale<=0)throw new IOException("Не удалось подготовить область для анализа");
            Bitmap image=Bitmap.createBitmap(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),Bitmap.Config.ARGB_8888);ResultIterator it=null;
            try{
                Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);canvas.translate(-left,-top);
                for(Ink.Stroke original:tile.strokes){Ink.Stroke dark=original.copy();dark.color=Color.BLACK;InkRenderer.stroke(canvas,dark);}
                tess.setImage(image);tess.getUTF8Text();it=tess.getResultIterator();if(it==null)continue;it.begin();
                do{
                    if(!allowed.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException("Анализ приостановлен");
                    int level=TessBaseAPI.PageIteratorLevel.RIL_WORD;String text=AiLearningCore.label(it.getUTF8Text(level),it.confidence(level));Rect rect=it.getBoundingRect(level);if(text==null||rect==null||rect.isEmpty())continue;
                    float margin=3f/scale,l=left+rect.left/scale-margin,t=top+rect.top/scale-margin,r=left+rect.right/scale+margin,b=top+rect.bottom/scale+margin;
                    ArrayList<Ink.Stroke> selected=AiLearningCore.select(page,tile.strokes,l,t,r,b);if(selected.isEmpty())continue;
                    JSONArray strokes=AiLearningCore.json(selected);try{HandwritingTraining.validate(strokes);}catch(Exception invalid){continue;}
                    String id=AiLearningCore.sampleId(doc.id,page.id,text,strokes);if(ids.add(id))results.put(new JSONObject().put("id",id).put("text",text).put("strokes",strokes));
                }while(it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD));
            }finally{if(it!=null)it.delete();image.recycle();tess.clear();}
        }return results;
    }
    public synchronized void close(){if(!closed){closed=true;tess.recycle();}}
    synchronized void stop(){if(!closed)tess.stop();}
}
