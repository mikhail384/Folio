package ru.folio.notes;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Uses production title lookup and real temporary files, without Android. */
public final class NotebookNameTests {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory(Paths.get(args[0]),"notebook-names-");
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar calendar=Calendar.getInstance();calendar.clear();calendar.set(2026,9,22,1,0);
            Date now=calendar.getTime();
            NotebookNames names=new NotebookNames(directory.toFile());
            Map<String,String> docs=new LinkedHashMap<>();
            check(names.inLibrary(" Эскиз ",docs,null,now).equals("Эскиз"),"unused name has no suffix");
            check(names.inLibrary("",docs,null,now).equals("22.10.2026"),"blank name uses today's date without suffix");
            docs.put("missing","Эскиз");
            check(names.inLibrary("Эскиз",docs,null,now).equals("Эскиз"),"stale document with no file cannot reserve a title");
            Files.write(directory.resolve("missing.json"),new byte[]{1});
            check(names.inLibrary("Эскиз",docs,null,now).equals("Эскиз (1)"),"existing file reserves its title");
            check(names.inLibrary("эскиз",docs,null,now).equals("эскиз (1)"),"Cyrillic case-insensitive collisions retained");
            check(names.inLibrary("Эскиз",docs,"missing",now).equals("Эскиз"),"rename excludes own file by ID");
            check(names.inLibrary("  Эскиз  ",docs,new String("missing"),now).equals("Эскиз"),"exclusion uses ID value, not Java object identity");
            docs.put("one","Эскиз (1)");Files.write(directory.resolve("one.json"),new byte[]{1});
            docs.put("three","Эскиз (3)");Files.write(directory.resolve("three.json"),new byte[]{1});
            check(names.inLibrary("Эскиз",docs,null,now).equals("Эскиз (2)"),"first free suffix fills gaps");
            Files.delete(directory.resolve("missing.json"));
            check(names.inLibrary("Эскиз",docs,null,now).equals("Эскиз"),"base name is reused even when indexed files exist");
            check(names.inLibrary("Эскиз (2)",docs,null,now).equals("Эскиз (2)"),"explicit free indexed title is preserved");
            Files.delete(directory.resolve("one.json"));Files.delete(directory.resolve("three.json"));
            check(names.inLibrary("Эскиз",docs,null,now).equals("Эскиз"),"deletion frees the name despite stale list entries");
            docs.put("date","22.10.2026");Files.write(directory.resolve("date.json"),new byte[]{1});
            check(names.inLibrary(" ",docs,null,now).equals("22.10.2026 (1)"),"date suffix requires an actual dated file");
            check(names.inLibrary(null,docs,"date",now).equals("22.10.2026"),"renaming own dated file does not add suffix");
            docs.put("date-one","22.10.2026 (1)");Files.write(directory.resolve("date-one.json"),new byte[]{1});
            Files.delete(directory.resolve("date.json"));
            check(names.inLibrary("",docs,null,now).equals("22.10.2026"),"deleted date is reused before any indices");
            docs.put("backup","Восстановление");Files.write(directory.resolve("backup.json.bak"),new byte[]{1});
            check(names.inLibrary("Восстановление",docs,null,now).equals("Восстановление (1)"),"AtomicFile recovery file counts as an existing notebook");
            Files.delete(directory.resolve("backup.json.bak"));Files.write(directory.resolve("backup.json.new"),new byte[]{1});
            check(names.inLibrary("Восстановление",docs,null,now).equals("Восстановление"),"abandoned incomplete atomic write does not reserve a name");
            docs.put("dir","Папка");Files.createDirectory(directory.resolve("dir.json"));
            check(names.inLibrary("Папка",docs,null,now).equals("Папка"),"a directory is not a notebook file");
            docs.put("pending","Сохранение");names.beginSave("pending");
            check(names.inLibrary("Сохранение",docs,null,now).equals("Сохранение (1)"),"queued creation reserves its title before disk write");
            check(names.inLibrary("Сохранение",docs,"pending",now).equals("Сохранение"),"queued own save is excluded on rename");
            names.beginSave("pending");names.endSave("pending");
            check(names.inLibrary("Сохранение",docs,null,now).equals("Сохранение (1)"),"finishing one save cannot release a later queued save");
            names.endSave("pending");
            check(names.inLibrary("Сохранение",docs,null,now).equals("Сохранение"),"failed or cancelled first save leaves no phantom filename");
            names.beginSave("pending");Files.write(directory.resolve("pending.json"),new byte[]{1});names.endSave("pending");
            check(names.inLibrary("Сохранение",docs,null,now).equals("Сохранение (1)"),"successful save keeps the title reserved by the file");
            check(names.inLibrary("Импорт",docs,"external",now).equals("Импорт"),"non-conflicting imported title stays unchanged");
            docs.put("external","Импорт");Files.write(directory.resolve("external.json"),new byte[]{1});
            check(names.inLibrary("Импорт",docs,"external",now).equals("Импорт"),"persisted import does not collide with itself");
            docs.put("old-import","Импорт");Files.write(directory.resolve("old-import.json"),new byte[]{1});
            check(names.inLibrary("Импорт",docs,"external",now).equals("Импорт (1)"),"other imported file with the same title adds index");
            Map<String,String> rapid=new LinkedHashMap<>();Set<String> allocated=new HashSet<>();
            for(int i=0;i<100;i++){
                String title=names.inLibrary("Быстро",rapid,null,now),id="rapid-"+i;
                check(allocated.add(title),"rapid queued creations remain unique");
                check(title.equals(i==0?"Быстро":"Быстро ("+i+")"),"rapid creations use the first free filename");
                rapid.put(id,title);names.beginSave(id);
            }
            for(String id:rapid.keySet()){Files.write(directory.resolve(id+".json"),new byte[]{1});names.endSave(id);}
            check(names.inLibrary("Быстро",rapid,null,now).equals("Быстро (100)"),"queued-to-persisted transition preserves all names");
            Files.delete(directory.resolve("rapid-0.json"));
            check(names.inLibrary("Быстро",rapid,null,now).equals("Быстро"),"free base name wins over the highest suffix");
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            check(names.inLibrary("",Collections.emptyMap(),null,now).equals("21.10.2026"),"automatic date follows the local timezone");
            System.out.println("PASS "+checks+" notebook naming checks: real file existence, deleted names, self-rename/import, date, first free index and queued saves.");
        } finally {
            TimeZone.setDefault(previous);
            try(java.util.stream.Stream<Path> files=Files.walk(directory)){
                Path[] paths=files.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for(Path file:paths)Files.delete(file);
            }
        }
    }
}
