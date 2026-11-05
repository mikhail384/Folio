package ru.folio.notes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.json.*;

/** Private, append-only examples for a generative handwriting model. */
final class AiCorpusStore {
    static final String CALIBRATION="calibration";
    static final int MAX_ITEM=2*1024*1024,MAX_RESPONSE=3*1024*1024,MAX_PAGE=12;
    private static final Object ACCOUNT_LOCK=new Object();
    final File root;
    AiCorpusStore(File root){this.root=root;}
    static void name(String s){if(s==null||!s.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Некорректный идентификатор тетради");}
    static void id(String s){if(s==null||!s.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Некорректный идентификатор образца");}
    File folder(String doc){name(doc);return new File(root,doc);}
    synchronized boolean save(String doc,String page,String id,String text,JSONArray strokes,String source,String kind,String target,BooleanSupplier allowed)throws Exception{
        name(doc);name(page);id(id);
        if(!allowed.getAsBoolean())return false;
        if(text==null||text.trim().isEmpty()||text.length()>64||text.matches(".*[\\r\\n\\u0000-\\u001f].*"))throw new IllegalArgumentException("Некорректная подпись");
        if(!source.equals("ocr")&&!source.equals("calibration"))throw new IllegalArgumentException("Некорректный источник");
        if(!kind.equals("text")&&!kind.equals("formula")&&!kind.equals("unknown"))throw new IllegalArgumentException("Некорректный тип");
        if(target==null)target="";if(target.length()>512)throw new IllegalArgumentException("Некорректное подключение");
        HandwritingTraining.validate(strokes,text);
        JSONObject item=new JSONObject().put("format","folio-training-example-v1").put("id",id).put("document_id",doc).put("page_id",page).put("text",text).put("source",source).put("reviewed",source.equals("calibration")).put("kind",kind).put("target",target).put("strokes",new JSONArray(strokes.toString()));
        byte[] bytes=item.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_ITEM)throw new IOException("Образец слишком большой");
        File dir=folder(doc);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать хранилище образцов");
        File dest=new File(dir,id+".json");if(dest.exists())return false;
        File tmp=new File(dir,id+"."+UUID.randomUUID().toString()+".tmp");
        try{try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);out.getFD().sync();}if(!allowed.getAsBoolean())return false;
            try{Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),dest.toPath());}catch(FileAlreadyExistsException e){return false;}return true;
        }finally{if(tmp.exists())tmp.delete();}
    }
    synchronized void saveOcr(String doc,String page,JSONArray examples,String target,BooleanSupplier allowed)throws Exception{
        for(int i=0;i<examples.length();i++){if(!allowed.getAsBoolean())return;JSONObject sample=examples.getJSONObject(i);save(doc,page,sample.getString("id"),sample.getString("text"),sample.getJSONArray("strokes"),"ocr","text",target,allowed);}
    }
    private JSONObject read(File file)throws Exception{
        if(file.length()<1||file.length()>MAX_ITEM)throw new IOException("Повреждён образец");ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>MAX_ITEM)throw new IOException("Повреждён образец");}}
        return new JSONObject(bytes.toString("UTF-8"));
    }
    private void bind(File file,JSONObject item,String target)throws Exception{
        item.put("target",target);File temp=new File(file.getParentFile(),file.getName()+"."+UUID.randomUUID()+".tmp");
        try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(item.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
        }finally{if(temp.exists())temp.delete();}
    }
    synchronized JSONObject page(String doc,String cursor,int limit,String target,BooleanSupplier allowed)throws Exception{
        name(doc);if(cursor==null)cursor="";if(!cursor.isEmpty())id(cursor);if(limit<1||limit>MAX_PAGE)throw new IllegalArgumentException("Некорректный размер страницы");
        if(target==null||target.isEmpty())throw new IllegalArgumentException("Сначала подключите облако");if(!allowed.getAsBoolean())throw new SecurityException("Нет разрешения на обучение по этой тетради");
        File[] entries=folder(doc).listFiles((dir,file)->file.matches("[a-f0-9]{32}\\.json"));if(entries==null)entries=new File[0];Arrays.sort(entries,Comparator.comparing(File::getName));
        JSONArray result=new JSONArray();String last="";boolean more=false;int size=0;
        for(File file:entries){String key=file.getName().substring(0,32);if(key.compareTo(cursor)<=0)continue;if(!allowed.getAsBoolean())throw new SecurityException("Разрешение на обучение отключено");
            synchronized(ACCOUNT_LOCK){JSONObject item=read(file);
            if(!key.equals(item.optString("id"))||!doc.equals(item.optString("document_id")))throw new IOException("Повреждён образец");String bound=item.optString("target");if(!bound.isEmpty()&&!bound.equals(target))continue;
            int length=item.toString().getBytes(StandardCharsets.UTF_8).length;if(result.length()>=limit||size+length>MAX_RESPONSE){more=true;break;}
            if(bound.isEmpty()){if(!allowed.getAsBoolean())throw new SecurityException("Разрешение отключено");bind(file,item,target);}
            size+=length;item.remove("target");result.put(item);last=key;}
        }
        if(!allowed.getAsBoolean())throw new SecurityException("Разрешение на обучение отключено");
        return new JSONObject().put("document_id",doc).put("examples",result).put("has_more",more).put("next_cursor",more?last:"");
    }
    /** Explicit transfer requested by the owner; revoked notebooks remain inaccessible. */
    int transfer(String target,BooleanSupplier current,Predicate<String> permitted,boolean apply)throws Exception{
        if(target==null||target.isEmpty()||target.length()>512)throw new IllegalArgumentException("Сначала подключите облако");
        File[] folders=root.listFiles(File::isDirectory);if(folders==null)return 0;int count=0;
        for(File dir:folders){String doc=dir.getName();if(!doc.matches("[A-Za-z0-9_-]{1,80}")||(!doc.equals(CALIBRATION)&&!permitted.test(doc)))continue;
            File[] files=dir.listFiles((d,file)->file.matches("[a-f0-9]{32}\\.json"));if(files==null)continue;
            for(File file:files){if(!current.getAsBoolean())throw new SecurityException("Подключение изменилось");if(!doc.equals(CALIBRATION)&&!permitted.test(doc))break;
                synchronized(ACCOUNT_LOCK){JSONObject item=read(file);String key=file.getName().substring(0,32);
                    if(!key.equals(item.optString("id"))||!doc.equals(item.optString("document_id")))throw new IOException("Повреждён образец");
                    String old=item.optString("target");if(old.isEmpty()||old.equals(target))continue;
                    if(apply){if(!current.getAsBoolean()||(!doc.equals(CALIBRATION)&&!permitted.test(doc)))throw new SecurityException("Разрешение изменилось");bind(file,item,target);}count++;
                }
            }
        }return count;
    }
}
