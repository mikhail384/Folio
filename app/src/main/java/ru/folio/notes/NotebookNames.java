package ru.folio.notes;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only existing notebook files and queued saves reserve a title. */
final class NotebookNames {
    private final File directory;
    private final Map<String,Integer> pending=new HashMap<>();

    NotebookNames(File directory) { this.directory=directory; }

    synchronized void beginSave(String id) { pending.put(id,pending.getOrDefault(id,0)+1); }
    synchronized void endSave(String id) {
        Integer count=pending.get(id);
        if(count!=null) { if(count==1)pending.remove(id);else pending.put(id,count-1); }
    }
    private synchronized boolean exists(String id) {
        // AtomicFile can temporarily keep the last complete file as .bak.
        return pending.containsKey(id)||new File(directory,id+".json").isFile()
                ||new File(directory,id+".json.bak").isFile();
    }
    String inLibrary(String input,Map<String,String> documents,String ownId,Date now) {
        ArrayList<String> titles=new ArrayList<>();
        for(Map.Entry<String,String> doc:documents.entrySet())
            if(!doc.getKey().equals(ownId)&&exists(doc.getKey()))titles.add(doc.getValue());
        return unique(input,titles,now);
    }
    static String unique(String input, Collection<String> existing, Date now) {
        String base=input==null?"":input.trim();
        if(base.isEmpty())base=new SimpleDateFormat("dd.MM.yyyy",Locale.ROOT).format(now);
        Set<String> used=new HashSet<>();
        for(String title:existing)if(title!=null)used.add(title.toLowerCase(Locale.ROOT));
        if(!used.contains(base.toLowerCase(Locale.ROOT)))return base;
        for(int n=1;;n++) {
            String suffix=" ("+n+")";
            String candidate=base.substring(0,Math.min(base.length(),120-suffix.length()))+suffix;
            if(!used.contains(candidate.toLowerCase(Locale.ROOT)))return candidate;
        }
    }
}
