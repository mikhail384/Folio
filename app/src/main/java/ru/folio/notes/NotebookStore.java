package ru.folio.notes;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import android.util.LruCache;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.*;

public final class NotebookStore {
    private static final int PDF_CACHE_KB=96*1024;
    private static final int PDF_FORMAT_REVISION=1;
    private static final long PDF_RASTER_MAX_PIXELS=16_000_000L;
    private static final Object PDF_FILE_CACHE_LOCK=new Object();
    private static final LruCache<String,PdfPageRaster> PDF_PAGE_CACHE=new LruCache<String,PdfPageRaster>(PDF_CACHE_KB){
        @Override protected int sizeOf(String key,PdfPageRaster value){return Math.max(1,value.bitmap.getAllocationByteCount()/1024);}
    };
    private static final class PdfPageRaster {
        final long fingerprintA,fingerprintB;final int width,height;final Bitmap bitmap;
        PdfPageRaster(long a,long b,int w,int h,Bitmap image){fingerprintA=a;fingerprintB=b;width=w;height=h;bitmap=image;}
    }
    private static final class PdfTeeOutputStream extends OutputStream {
        final OutputStream target,cache;boolean cacheFailed;
        PdfTeeOutputStream(OutputStream target,OutputStream cache){this.target=target;this.cache=cache;}
        @Override public void write(int value)throws IOException{target.write(value);if(!cacheFailed)try{cache.write(value);}catch(IOException e){cacheFailed=true;}}
        @Override public void write(byte[] data,int offset,int length)throws IOException{target.write(data,offset,length);if(!cacheFailed)try{cache.write(data,offset,length);}catch(IOException e){cacheFailed=true;}}
        @Override public void flush()throws IOException{target.flush();if(!cacheFailed)try{cache.flush();}catch(IOException e){cacheFailed=true;}}
    }
    final Context context;
    final File root,assets;
    final NotebookNames names;
    final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String,Object> FILE_LOCKS=new ConcurrentHashMap<>();
    private static Object fileLock(File f){return FILE_LOCKS.computeIfAbsent(f.getAbsolutePath(),k->new Object());}
    NotebookStore(Context c){context=c;root=new File(c.getFilesDir(),"notebooks");assets=new File(c.getFilesDir(),"assets");root.mkdirs();assets.mkdirs();names=new NotebookNames(root);}
    private static byte[] read(InputStream in,long max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException("Файл превышает допустимый размер");out.write(b,0,n);}return out.toByteArray();}
    public ArrayList<Ink.Doc> load(){return loadSelected(null);}
    ArrayList<String> learningIds(SharedPreferences prefs){ArrayList<String> ids=new ArrayList<>();File[] files=root.listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files){String id=f.getName().substring(0,f.getName().length()-5);try{if(AiLearningPermissions.allowed(prefs,id))ids.add(id);}catch(IllegalArgumentException ignored){}}return ids;}
    Ink.Doc loadForLearning(String id,SharedPreferences prefs){
        if(!AiLearningPermissions.allowed(prefs,id))return null;File path=new File(root,id+".json");
        synchronized(fileLock(path)){if(!AiLearningPermissions.allowed(prefs,id))return null;try(InputStream in=new AtomicFile(path).openRead()){Ink.Doc doc=Ink.Doc.parse(new JSONObject(new String(read(in,100L*1024*1024),StandardCharsets.UTF_8)));doc.id=id;return AiLearningPermissions.allowed(prefs,id)?doc:null;}catch(Exception failure){android.util.Log.e("Folio","Notebook unreadable",failure);return null;}}
    }
    private ArrayList<Ink.Doc> loadSelected(SharedPreferences prefs){ArrayList<Ink.Doc> list=new ArrayList<>();File[] files=root.listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File f:files){String id=f.getName().substring(0,f.getName().length()-5);try{if(prefs!=null&&!AiLearningPermissions.allowed(prefs,id))continue;synchronized(fileLock(f)){if(prefs!=null&&!AiLearningPermissions.allowed(prefs,id))continue;try(InputStream in=new AtomicFile(f).openRead()){Ink.Doc d=Ink.Doc.parse(new JSONObject(new String(read(in,100L*1024*1024),StandardCharsets.UTF_8)));d.id=id;if(prefs==null||AiLearningPermissions.allowed(prefs,id))list.add(d);}}}catch(Exception e){android.util.Log.e("Folio","Notebook unreadable",e);}}list.sort((a,b)->Long.compare(b.modified,a.modified));return list;}
    public void write(Ink.Doc d)throws Exception{File path=new File(root,d.id+".json");synchronized(fileLock(path)){AtomicFile file=new AtomicFile(path);FileOutputStream out=null;try{out=file.startWrite();OutputStreamWriter writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);InkJsonWriter.write(d,writer);writer.flush();file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}}
    void enqueueWrite(Ink.Doc snapshot,java.util.function.Consumer<Exception> completed){
        names.beginSave(snapshot.id);
        try{io.execute(()->{Exception failure=null;try{write(snapshot);}catch(Exception e){failure=e;}finally{names.endSave(snapshot.id);}completed.accept(failure);});}
        catch(RejectedExecutionException e){names.endSave(snapshot.id);throw e;}
    }
    public void delete(Ink.Doc d){File path=new File(root,d.id+".json");synchronized(fileLock(path)){new AtomicFile(path).delete();}}
    public File asset(String name)throws IOException{if(!name.matches("[a-zA-Z0-9_-]+\\.(png|jpg|pdf|m4a)"))throw new IOException("Недопустимый ресурс");return new File(assets,name);}
    public Bitmap bitmap(String name,int max)throws IOException {BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(asset(name).toString(),bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Не удалось открыть изображение");BitmapFactory.Options o=new BitmapFactory.Options();int sample=1;while(bounds.outWidth/sample>max||bounds.outHeight/sample>max)sample*=2;o.inSampleSize=sample;Bitmap b=BitmapFactory.decodeFile(asset(name).toString(),o);if(b==null)throw new IOException("Не удалось открыть изображение");return b;}
    public String addBitmap(Bitmap bitmap)throws IOException{String name=Ink.id()+".png";try(FileOutputStream out=new FileOutputStream(asset(name))){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Не удалось сохранить изображение");}return name;}
    public Ink.Doc importFile(Uri uri,String mime,String name)throws Exception{
        if(name.toLowerCase(Locale.ROOT).endsWith(".folio")||name.toLowerCase(Locale.ROOT).endsWith(".zip"))return importArchive(uri);
        Ink.Doc d=new Ink.Doc();d.title=name.replaceFirst("\\.[^.]+$","");d.pages.clear();
        if("application/pdf".equals(mime)||name.toLowerCase(Locale.ROOT).endsWith(".pdf")){
            File temp=new File(context.getCacheDir(),Ink.id()+".pdf");
            try{try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("Файл недоступен");byte[] data=read(in,250L*1024*1024);out.write(data);}
                try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd)){
                    if(pdf.getPageCount()>500)throw new IOException("Импортируйте PDF частями до 500 страниц");
                    for(int i=0;i<pdf.getPageCount();i++)try(PdfRenderer.Page page=pdf.openPage(i)){
                        float factor=Math.min(2.5f,2000f/Math.max(page.getWidth(),page.getHeight()));int w=Math.max(1,(int)(page.getWidth()*factor)),h=Math.max(1,(int)(page.getHeight()*factor));
                        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        Ink.Page p=new Ink.Page();p.infinite=false;p.width=1200;p.height=1200f*h/w;p.pattern="blank";p.background=addBitmap(b);b.recycle();d.pages.add(p);
                    }
                }
            }finally{temp.delete();}
        }else if(mime!=null&&mime.startsWith("image/")){
            File temp=new File(context.getCacheDir(),Ink.id()+".img");try{
                try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("Файл недоступен");out.write(read(in,80L*1024*1024));}
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.toString(),bounds);if(bounds.outWidth<=0)throw new IOException("Неподдерживаемое изображение");
                BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/o.inSampleSize>2400)o.inSampleSize*=2;
                Bitmap b=BitmapFactory.decodeFile(temp.toString(),o);if(b==null)throw new IOException("Изображение повреждено");Ink.Page p=new Ink.Page();p.infinite=false;p.pattern="blank";p.width=1200;p.height=1200f*b.getHeight()/b.getWidth();p.background=addBitmap(b);b.recycle();d.pages.add(p);
            }finally{temp.delete();}
        }else throw new IOException("Поддерживаются PDF, изображения и .folio");
        if(d.pages.isEmpty())throw new IOException("В документе нет страниц");write(d);return d;
    }
    public void exportArchive(Ink.Doc doc,OutputStream output)throws Exception{
        try(ZipOutputStream zip=new ZipOutputStream(output)){zip.setLevel(Deflater.BEST_SPEED);
            zip.putNextEntry(new ZipEntry("document.json"));OutputStreamWriter writer=new OutputStreamWriter(zip,StandardCharsets.UTF_8);InkJsonWriter.write(doc,writer);writer.flush();zip.closeEntry();
            HashSet<String> names=new HashSet<>();for(Ink.Page p:doc.pages)if(!p.background.isEmpty())names.add(p.background);
            for(Ink.Audio audio:doc.audio)names.add(audio.file);for(String name:names){zip.putNextEntry(new ZipEntry("assets/"+name));try(InputStream in=new FileInputStream(asset(name))){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)zip.write(b,0,n);}zip.closeEntry();}
        }
    }
    private Ink.Doc importArchive(Uri uri)throws Exception{
        HashMap<String,String> renames=new HashMap<>();ArrayList<File> created=new ArrayList<>();Ink.Doc doc=null;long total=0;
        try(InputStream input=context.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry e;int count=0;while((e=zip.getNextEntry())!=null){if(++count>2500)throw new IOException("Слишком много файлов в архиве");if(e.isDirectory())continue;
                byte[] data=read(zip,100L*1024*1024);total+=data.length;if(total>350L*1024*1024)throw new IOException("Архив слишком большой");
                if(e.getName().equals("document.json")){if(doc!=null)throw new IOException("Повторный документ в архиве");doc=Ink.Doc.parse(new JSONObject(new String(data,StandardCharsets.UTF_8)));}
                else if(e.getName().matches("assets/[a-zA-Z0-9_-]+\\.(png|jpg|m4a)")){String old=e.getName().substring(7);if(renames.containsKey(old))throw new IOException("Повторный ресурс");String n=Ink.id()+old.substring(old.lastIndexOf('.'));File f=asset(n);created.add(f);try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}renames.put(old,n);}
                else throw new IOException("Неожиданный файл в архиве");
            }
            if(doc==null)throw new IOException("В архиве нет document.json");for(Ink.Page p:doc.pages)if(!p.background.isEmpty()){String n=renames.get(p.background);if(n==null)throw new IOException("Отсутствует изображение страницы");p.background=n;}
            for(Ink.Audio audio:doc.audio){String name=renames.get(audio.file);if(name==null)throw new IOException("Отсутствует аудиозапись");audio.file=name;}doc.id=Ink.id();doc.folder="";doc.modified=System.currentTimeMillis();write(doc);return doc;
        }catch(Exception e){for(File f:created)f.delete();throw e;}
    }
    public void exportPdf(Ink.Doc doc,OutputStream out)throws Exception{
        synchronized(PDF_FILE_CACHE_LOCK){
            long[][] fingerprints=new long[doc.pages.size()][];long a=pdfHash(0xcbf29ce484222325L,PDF_FORMAT_REVISION),b=pdfHash(0x84222325cbf29ce4L,doc.pages.size());
            for(int i=0;i<doc.pages.size();i++){fingerprints[i]=pdfFingerprint(doc.pages.get(i));a=pdfHash(a,i);a=pdfHash(a,(int)fingerprints[i][0]);a=pdfHash(a,(int)(fingerprints[i][0]>>>32));b=pdfHash(b,i);b=pdfHash(b,(int)fingerprints[i][1]);b=pdfHash(b,(int)(fingerprints[i][1]>>>32));}
            File cache=new File(context.getCacheDir(),"folio-pdf-"+Long.toHexString(a)+"-"+Long.toHexString(b)+".pdf");
            if(cache.isFile()&&cache.length()>0){copyPdf(cache,out);return;}
            File temporary=new File(cache.getPath()+".tmp");temporary.delete();FileOutputStream cachedOut;
            try{cachedOut=new FileOutputStream(temporary);}catch(IOException unavailable){temporary.delete();writePdf(doc,fingerprints,out);return;}
            PdfTeeOutputStream tee=new PdfTeeOutputStream(out,cachedOut);
            try{writePdf(doc,fingerprints,tee);tee.flush();}catch(Exception|Error failure){try{cachedOut.close();}catch(IOException ignored){}temporary.delete();throw failure;}
            try{cachedOut.close();}catch(IOException unavailable){temporary.delete();return;}
            if(tee.cacheFailed){temporary.delete();return;}
            cache.delete();if(!temporary.renameTo(cache)){temporary.delete();return;}prunePdfFiles(cache);
        }
    }
    private PdfPageRaster pdfRaster(Ink.Page page,long[] fingerprint)throws Exception{
        String key=page.id;
        synchronized(PDF_PAGE_CACHE){PdfPageRaster cached=PDF_PAGE_CACHE.get(key);if(cached!=null&&cached.fingerprintA==fingerprint[0]&&cached.fingerprintB==fingerprint[1])return cached;}
        float[] bounds=CanvasViewport.exportBounds(page);float width=bounds[2]-bounds[0],height=bounds[3]-bounds[1];
        float scale=page.infinite?600/Math.max(width,height):600/width;int pdfWidth=page.infinite?Math.max(100,Math.round(width*scale)):600,pdfHeight=Math.max(100,Math.round(height*scale));
        float density=Math.min(2f,(float)Math.sqrt(PDF_RASTER_MAX_PIXELS/Math.max(1d,(double)pdfWidth*pdfHeight)));
        int rasterWidth=Math.max(1,Math.round(pdfWidth*density)),rasterHeight=Math.max(1,Math.round(pdfHeight*density));
        Bitmap raster=Bitmap.createBitmap(rasterWidth,rasterHeight,Bitmap.Config.ARGB_8888);Bitmap background=null;InkRenderer.Prepared prepared=new InkRenderer.Prepared();
        try{
            Canvas canvas=new Canvas(raster);canvas.drawColor(page.paper);
            canvas.translate((rasterWidth-width*scale*density)/2,(rasterHeight-height*scale*density)/2);canvas.scale(scale*density,scale*density);canvas.translate(-bounds[0],-bounds[1]);
            background=page.background.isEmpty()?null:bitmap(page.background,2400);
            if(page.infinite)InkRenderer.region(canvas,page,background,page.strokes,Collections.emptySet(),bounds[0],bounds[1],bounds[2],bounds[3],scale,prepared);
            else InkRenderer.page(canvas,page,background,Collections.emptySet(),prepared);
        }catch(Exception|Error failure){raster.recycle();throw failure;}
        finally{prepared.clear();if(background!=null)background.recycle();}
        PdfPageRaster result=new PdfPageRaster(fingerprint[0],fingerprint[1],pdfWidth,pdfHeight,raster);
        synchronized(PDF_PAGE_CACHE){PDF_PAGE_CACHE.put(key,result);}
        return result;
    }
    private void writePdf(Ink.Doc doc,long[][] fingerprints,OutputStream output)throws Exception{
        PdfDocument pdf=new PdfDocument();Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        try{for(int i=0;i<doc.pages.size();i++){PdfPageRaster raster=pdfRaster(doc.pages.get(i),fingerprints[i]);PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(raster.width,raster.height,i+1).create());page.getCanvas().drawBitmap(raster.bitmap,null,new Rect(0,0,raster.width,raster.height),imagePaint);pdf.finishPage(page);}pdf.writeTo(output);}finally{pdf.close();}
    }
    private static void copyPdf(File source,OutputStream output)throws IOException{try(InputStream input=new FileInputStream(source)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}}
    private void prunePdfFiles(File keep){File[] files=context.getCacheDir().listFiles((dir,name)->name.startsWith("folio-pdf-")&&name.endsWith(".pdf"));if(files!=null)for(File file:files)if(!file.equals(keep))file.delete();}
    private long[] pdfFingerprint(Ink.Page page)throws IOException{
        long a=0xcbf29ce484222325L,b=0x84222325cbf29ce4L;
        a=pdfHash(a,page.id);b=pdfHash(b,page.id);a=pdfHash(a,page.pattern);b=pdfHash(b,page.background);
        a=pdfHash(a,page.paper);b=pdfHash(b,page.lines);a=pdfHash(a,Float.floatToIntBits(page.width));b=pdfHash(b,Float.floatToIntBits(page.height));
        a=pdfHash(a,Float.floatToIntBits(page.spacing));b=pdfHash(b,page.infinite?1:0);
        if(!page.background.isEmpty()){File image=asset(page.background);a=pdfHash(a,image.length());b=pdfHash(b,image.lastModified());}
        a=pdfHash(a,page.layers.size());b=pdfHash(b,page.strokes.size());
        for(Ink.Layer layer:page.layers){a=pdfHash(a,layer.id);b=pdfHash(b,layer.visible?1:0);}
        for(Ink.Stroke stroke:page.strokes){
            a=pdfHash(a,System.identityHashCode(stroke));b=pdfHash(b,stroke.tool);a=pdfHash(a,stroke.layer);b=pdfHash(b,stroke.text);
            a=pdfHash(a,stroke.color);b=pdfHash(b,Float.floatToIntBits(stroke.width));a=pdfHash(a,stroke.texture);b=pdfHash(b,stroke.revealed?1:0);
            int count=stroke.points.size();a=pdfHash(a,count);b=pdfHash(b,stroke.cuts.size());
            if(count>0){a=pdfHash(a,System.identityHashCode(stroke.points.get(0)));b=pdfHash(b,System.identityHashCode(stroke.points.get(count/2)));a=pdfHash(a,System.identityHashCode(stroke.points.get(count-1)));}
            if(!stroke.cuts.isEmpty()){a=pdfHash(a,System.identityHashCode(stroke.cuts.get(0)));b=pdfHash(b,System.identityHashCode(stroke.cuts.get(stroke.cuts.size()-1)));}
        }
        return new long[]{a,b};
    }
    private static long pdfHash(long hash,int value){return (hash^Integer.toUnsignedLong(value))*0x100000001b3L;}
    private static long pdfHash(long hash,long value){for(int i=0;i<8;i++){hash=(hash^(value&0xffL))*0x100000001b3L;value>>>=8;}return hash;}
    private static long pdfHash(long hash,String value){if(value==null)return pdfHash(hash,0);for(int i=0;i<value.length();i++)hash=pdfHash(hash,value.charAt(i));return pdfHash(hash,value.length());}
    public void exportPng(Ink.Page p,OutputStream out)throws Exception{float[] bounds=CanvasViewport.exportBounds(p);float width=bounds[2]-bounds[0],height=bounds[3]-bounds[1],scale=Math.min(1.5f,2400f/Math.max(width,height));Bitmap image=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(width*scale)),Math.max(1,(int)Math.ceil(height*scale)),Bitmap.Config.ARGB_8888);Bitmap bg=null;InkRenderer.Prepared prepared=new InkRenderer.Prepared();try{Canvas c=new Canvas(image);c.drawColor(p.paper);c.scale(scale,scale);c.translate(-bounds[0],-bounds[1]);bg=p.background.isEmpty()?null:bitmap(p.background,2400);if(p.infinite)InkRenderer.region(c,p,bg,p.strokes,Collections.emptySet(),bounds[0],bounds[1],bounds[2],bounds[3],scale,prepared);else InkRenderer.page(c,p,bg,Collections.emptySet(),prepared);if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Ошибка PNG");}finally{prepared.clear();if(bg!=null)bg.recycle();image.recycle();}}
}
