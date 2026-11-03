package ru.folio.notes;

import android.content.*;
import android.graphics.*;
import android.widget.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bundled Russian/English OCR; images never leave the device. */
final class OcrStudio {
    final MainActivity a;final ExecutorService worker=Executors.newSingleThreadExecutor();boolean busy;
    OcrStudio(MainActivity a){this.a=a;}
    String recognize(Bitmap image)throws Exception{return recognize(a,image);}
    private static synchronized File prepare(Context context)throws Exception{File root=new File(context.getFilesDir(),"ocr"),data=new File(root,"tessdata");if(!data.isDirectory()&&!data.mkdirs())throw new IOException("Не удалось создать каталог моделей");
        for(String language:new String[]{"eng","rus"}){File file=new File(data,language+".traineddata");if(file.isFile()&&file.length()>1000000)continue;android.util.AtomicFile target=new android.util.AtomicFile(file);FileOutputStream out=null;try(InputStream in=context.getAssets().open("ocr/"+file.getName())){out=target.startWrite();byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);target.finishWrite(out);out=null;}catch(Exception e){if(out!=null)target.failWrite(out);throw e;}}
        return root;
    }
    static TessBaseAPI create(Context context,int mode)throws Exception{File root=prepare(context);TessBaseAPI tess=new TessBaseAPI();try{if(!tess.init(root.toString(),"rus+eng"))throw new IOException("Не удалось загрузить модели распознавания");tess.setPageSegMode(mode);return tess;}catch(Exception e){tess.recycle();throw e;}}
    static String recognize(Context context,Bitmap image)throws Exception{TessBaseAPI tess=create(context,TessBaseAPI.PageSegMode.PSM_AUTO);try{tess.setImage(image);return tess.getUTF8Text().trim();}finally{tess.recycle();}}
    Studio.Sheet show(){if(a.canvas==null||busy)return null;LinearLayout box=a.column();Studio.addCard(a,box,Studio.row(a,"text","Распознать текст",a.canvas.selection.isEmpty()?"Видимая область страницы · русский и английский":"Выделенная область · русский и английский",this::start));box.addView(a.text("Работает без интернета. Лучше всего распознаёт печатный текст; рукописный результат может потребовать исправления.",12,Studio.MUTED,false));return new Studio.Builder(a).anchor(a.editorHeader).setTitle("Текст из изображения").setView(box).setPositiveButton("Закрыть",null).show();}
    void start(){if(a.canvas==null||busy)return;a.canvas.finishGesture(false);NotesView view=a.canvas;Ink.Page page=view.page.copy();Ink.Doc owner=a.current;float[] b=view.selection.isEmpty()?new float[]{view.px(0),view.py(0),view.px(view.getWidth()),view.py(view.getHeight())}:view.selectionBounds();float scale=Math.min(2,2400f/Math.max(b[2]-b[0],b[3]-b[1]));if(scale<=0)return;busy=true;Studio.Sheet progress=Studio.busy(a,"Распознавание","Русский и английский · на устройстве…");
        worker.execute(()->{Bitmap image=null,bg=null;try{image=Bitmap.createBitmap(Math.max(1,Math.round((b[2]-b[0])*scale)),Math.max(1,Math.round((b[3]-b[1])*scale)),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.drawColor(Color.WHITE);c.scale(scale,scale);c.translate(-b[0],-b[1]);if(!page.background.isEmpty())bg=a.store.bitmap(page.background,2400);InkRenderer.region(c,page,bg,page.strokes,Collections.emptySet(),b[0],b[1],b[2],b[3],scale);String text=recognize(image);a.runOnUiThread(()->{busy=false;progress.dismiss();if(!a.isDestroyed())result(text,owner,page.id,b[0],b[1]);});}catch(Exception|OutOfMemoryError|LinkageError e){a.runOnUiThread(()->{busy=false;progress.dismiss();if(!a.isDestroyed())a.error("Распознавание",e);});}finally{if(image!=null)image.recycle();if(bg!=null)bg.recycle();}});
    }
    void result(String value,Ink.Doc owner,String page,float x,float y){EditText input=Studio.input(a,value,"Текст не найден. Можно ввести его вручную.");input.setSingleLine(false);input.setMinLines(4);input.setMaxLines(12);LinearLayout box=a.column();box.addView(input);new Studio.Builder(a).anchor(a.editorHeader).width(430).setTitle("Распознанный текст").setView(box).setNegativeButton("Закрыть",null).setNeutralButton("Копировать",(d,w)->((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Folio",input.getText()))).setPositiveButton("На холст",(d,w)->{if(a.current==owner&&a.canvas.page.id.equals(page))a.canvas.addText(input.getText().toString(),x,y);else a.toast("Вернитесь на исходную страницу");}).show();}
}
