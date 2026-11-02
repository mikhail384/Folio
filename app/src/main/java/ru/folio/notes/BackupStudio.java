package ru.folio.notes;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Versioned .folio copies in an explicitly selected DocumentsProvider folder. */
final class BackupStudio {
    static final int PICK=43;final MainActivity a;final ExecutorService worker=Executors.newSingleThreadExecutor();final Map<String,Ink.Doc> pending=new LinkedHashMap<>();boolean running;
    BackupStudio(MainActivity a){this.a=a;}
    Studio.Sheet show(){LinearLayout box=a.column();String folder=a.prefs.getString("backup_tree","");Studio.addCard(a,box,Studio.row(a,"folder",folder.isEmpty()?"Выбрать папку копий":"Изменить папку копий","Память устройства или провайдер в меню Android",this::pick));
        if(!folder.isEmpty()){a.toggle(box,"Автоматические копии","После сохранения и при выходе из тетради","backup_auto",false);Studio.addCard(a,box,Studio.row(a,"export","Скопировать всю библиотеку","Отдельный .folio для каждой тетради, включая звук",()->{a.saveNow();for(Ink.Doc d:a.docs)if(!a.prefs.getBoolean("trash_"+d.id,false))offer(d.copy());a.toast("Копирование запущено");}));Studio.addCard(a,box,Studio.row(a,"close","Отключить папку","Существующие копии останутся",()->{a.prefs.edit().remove("backup_tree").putBoolean("backup_auto",false).apply();a.toast("Автокопирование отключено");}));}
        box.addView(a.text(a.prefs.getString("backup_status","Копии ещё не создавались"),11,Studio.MUTED,false));CompactInspector.gap(a,box,9);box.addView(a.text("Синхронизацией выбранной облачной папки управляет её провайдер. Восстановление — через «Импорт .folio». Каждая копия сохраняется отдельной версией.",11,Studio.MUTED,false));return new Studio.Builder(a).setTitle("Резервные копии").setView(box).setPositiveButton("Готово",null).show();}
    void pick(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);a.startActivityForResult(intent,PICK);}
    void selected(Intent data){Uri uri=data.getData();if(uri==null)return;try{int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new IOException("Папка недоступна для записи");a.getContentResolver().takePersistableUriPermission(uri,flags);a.prefs.edit().putString("backup_tree",uri.toString()).apply();show();}catch(Exception e){a.error("Папка копий",e);}}
    void automatic(Ink.Doc snapshot){if(a.prefs.getBoolean("backup_auto",false))offer(snapshot);}
    void offer(Ink.Doc snapshot){String tree=a.prefs.getString("backup_tree","");if(tree.isEmpty())return;synchronized(pending){pending.put(snapshot.id,snapshot);if(running)return;running=true;}worker.execute(()->{while(true){Ink.Doc next;synchronized(pending){if(pending.isEmpty()){running=false;return;}Iterator<Ink.Doc> i=pending.values().iterator();next=i.next();i.remove();}try{write(next,Uri.parse(tree));a.prefs.edit().putString("backup_status","Сохранено: "+new SimpleDateFormat("d MMM HH:mm",new Locale("ru")).format(new Date())).apply();}catch(Exception e){a.prefs.edit().putString("backup_status","Не удалось создать копию: "+(e.getMessage()==null?"проверьте папку":e.getMessage())).apply();}}});}
    void write(Ink.Doc doc,Uri tree)throws Exception{Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));String title=doc.title.replaceAll("[\\\\/:*?\"<>|]","_");if(title.length()>70)title=title.substring(0,70);String name=title+"_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date())+"_"+doc.id.substring(0,8)+".folio";Uri file=DocumentsContract.createDocument(a.getContentResolver(),parent,"application/zip",name);if(file==null)throw new IOException("Провайдер не создал файл");boolean complete=false;try{try(OutputStream out=a.getContentResolver().openOutputStream(file,"wt")){if(out==null)throw new IOException("Провайдер не открыл файл");a.store.exportArchive(doc,out);}complete=true;}finally{if(!complete)try{DocumentsContract.deleteDocument(a.getContentResolver(),file);}catch(Exception ignored){}}}
}
