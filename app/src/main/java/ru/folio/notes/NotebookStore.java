package ru.folio.notes;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.*;

public final class NotebookStore {
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
        PdfDocument pdf=new PdfDocument();try{for(int i=0;i<doc.pages.size();i++){Ink.Page p=doc.pages.get(i);float[] bounds=CanvasViewport.exportBounds(p);float width=bounds[2]-bounds[0],height=bounds[3]-bounds[1],scale=p.infinite?600/Math.max(width,height):600/width;int w=p.infinite?Math.max(100,Math.round(width*scale)):600,h=Math.max(100,Math.round(height*scale));PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,i+1).create());Canvas c=page.getCanvas();c.drawColor(p.paper);c.translate((w-width*scale)/2,(h-height*scale)/2);c.scale(scale,scale);c.translate(-bounds[0],-bounds[1]);Bitmap image=p.background.isEmpty()?null:bitmap(p.background,2400);InkRenderer.Prepared prepared=new InkRenderer.Prepared();try{if(p.infinite)InkRenderer.region(c,p,image,p.strokes,Collections.emptySet(),bounds[0],bounds[1],bounds[2],bounds[3],scale,prepared);else InkRenderer.page(c,p,image,Collections.emptySet(),prepared);}finally{prepared.clear();if(image!=null)image.recycle();}pdf.finishPage(page);}pdf.writeTo(out);}finally{pdf.close();}
    }
    public void exportPng(Ink.Page p,OutputStream out)throws Exception{float[] bounds=CanvasViewport.exportBounds(p);float width=bounds[2]-bounds[0],height=bounds[3]-bounds[1],scale=Math.min(1.5f,2400f/Math.max(width,height));Bitmap image=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(width*scale)),Math.max(1,(int)Math.ceil(height*scale)),Bitmap.Config.ARGB_8888);Bitmap bg=null;InkRenderer.Prepared prepared=new InkRenderer.Prepared();try{Canvas c=new Canvas(image);c.drawColor(p.paper);c.scale(scale,scale);c.translate(-bounds[0],-bounds[1]);bg=p.background.isEmpty()?null:bitmap(p.background,2400);if(p.infinite)InkRenderer.region(c,p,bg,p.strokes,Collections.emptySet(),bounds[0],bounds[1],bounds[2],bounds[3],scale,prepared);else InkRenderer.page(c,p,bg,Collections.emptySet(),prepared);if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Ошибка PNG");}finally{prepared.clear();if(bg!=null)bg.recycle();image.recycle();}}
}
