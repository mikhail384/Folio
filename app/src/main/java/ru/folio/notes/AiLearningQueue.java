package ru.folio.notes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Atomic checkpoints and bounded upload queue; entries remain bound to their original account. */
final class AiLearningQueue {
    static final int MAX_BYTES=8*1024*1024,MAX_PENDING=256,MAX_SEEN=8000;
    final File file;private JSONObject root;
    AiLearningQueue(File path){file=path;}
    private void ensure()throws Exception{
        if(root!=null)return;if(!file.exists()){root=new JSONObject().put("version",1).put("pages",new JSONObject()).put("seen",new JSONObject()).put("pending",new JSONObject());return;}
        if(file.length()>MAX_BYTES)throw new IOException("Очередь анализа слишком большая");ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>MAX_BYTES)throw new IOException("Очередь анализа слишком большая");}}
        JSONObject p=new JSONObject(out.toString("UTF-8"));if(p.optInt("version")!=1||p.optJSONObject("pages")==null||p.optJSONObject("seen")==null||p.optJSONObject("pending")==null)throw new IOException("Очередь анализа повреждена");root=p;
    }
    private JSONObject copy()throws Exception{ensure();return new JSONObject(root.toString());}
    private void write(JSONObject data)throws Exception{
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Очередь образцов заполнена");
        File dir=file.getParentFile();if(dir!=null&&!dir.exists()&&!dir.mkdirs())throw new IOException("Нет каталога для анализа");File temp=new File(dir,file.getName()+".tmp");
        try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}root=data;}finally{if(temp.exists())temp.delete();}
    }
    static String pageKey(String doc,String page){return doc+"|"+page;}
    synchronized boolean analyzed(String doc,String page,String fingerprint)throws Exception{ensure();return fingerprint.equals(root.getJSONObject("pages").optString(pageKey(doc,page)));}
    synchronized boolean record(String doc,String page,String fingerprint,JSONArray samples,String target)throws Exception{
        JSONObject next=copy(),seen=next.getJSONObject("seen"),pending=next.getJSONObject("pending");if(target==null)target="";
        int available=MAX_BYTES-65536-next.toString().getBytes(StandardCharsets.UTF_8).length;
        boolean complete=true,changed=false;for(int i=0;i<samples.length();i++){JSONObject s=samples.getJSONObject(i);String id=s.getString("id");if(!id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid sample");if(seen.has(id))continue;if(pending.length()>=MAX_PENDING){complete=false;break;}
            if(seen.length()>=MAX_SEEN){Iterator<String> old=seen.keys();String evict=null;while(old.hasNext()){String x=old.next();if(!pending.has(x)){evict=x;break;}}if(evict==null){complete=false;break;}seen.remove(evict);}
            JSONObject item=new JSONObject().put("id",id).put("document_id",doc).put("page_id",page).put("text",s.getString("text")).put("strokes",s.getJSONArray("strokes")).put("target",target);int size=item.toString().getBytes(StandardCharsets.UTF_8).length+id.length()*2+32;if(size>available){complete=false;break;}available-=size;
            pending.put(id,item);seen.put(id,true);changed=true;}
        if(complete)next.getJSONObject("pages").put(pageKey(doc,page),fingerprint);if(changed||complete)write(next);return complete;
    }
    synchronized JSONArray pendingFor(String target)throws Exception{
        if(target==null||target.isEmpty())return new JSONArray();JSONObject data=copy(),pending=data.getJSONObject("pending");JSONArray out=new JSONArray();boolean changed=false;Iterator<String> ids=pending.keys();
        while(ids.hasNext()){JSONObject p=pending.getJSONObject(ids.next());String bound=p.optString("target");if(!bound.isEmpty()&&!bound.equals(target))continue;if(bound.isEmpty()){p.put("target",target);changed=true;}out.put(new JSONObject(p.toString()));}
        if(changed)write(data);return out;
    }
    synchronized void acknowledge(String id)throws Exception{JSONObject data=copy();if(data.getJSONObject("pending").has(id)){data.getJSONObject("pending").remove(id);write(data);}}
    synchronized void purgeRevoked(java.util.function.Predicate<String> allowed)throws Exception{
        JSONObject data=copy(),pending=data.getJSONObject("pending"),seen=data.getJSONObject("seen"),pages=data.getJSONObject("pages");boolean changed=false;
        ArrayList<String> remove=new ArrayList<>();Iterator<String> ids=pending.keys();while(ids.hasNext()){String id=ids.next();if(!allowed.test(pending.getJSONObject(id).getString("document_id")))remove.add(id);}
        for(String id:remove){pending.remove(id);seen.remove(id);changed=true;}
        remove.clear();Iterator<String> keys=pages.keys();while(keys.hasNext()){String key=keys.next();int split=key.indexOf('|');if(split<1||!allowed.test(key.substring(0,split)))remove.add(key);}
        for(String key:remove){pages.remove(key);changed=true;}if(changed)write(data);
    }
    synchronized int size()throws Exception{ensure();return root.getJSONObject("pending").length();}
}
