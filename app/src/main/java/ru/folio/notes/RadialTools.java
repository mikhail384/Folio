package ru.folio.notes;

import java.util.*;

/** Eight generous targets per page; the complete toolbar order is preserved. */
final class RadialTools {
    static final int PAGE_SIZE=12,PAGER=16;
    private List<String> order=Collections.emptyList();int page;
    boolean set(List<String> tools){if(order.equals(tools))return false;order=new ArrayList<>(tools);page=0;return true;}
    int pages(){return Math.max(1,(order.size()+PAGE_SIZE-1)/PAGE_SIZE);}
    int visibleCount(){return Math.min(PAGE_SIZE,Math.max(1,order.size()-page*PAGE_SIZE));}
    String tool(int slot){int index=page*PAGE_SIZE+slot;return slot<0||slot>=PAGE_SIZE||index>=order.size()?null:order.get(index);}
    void first(){page=0;}
    void next(){page=(page+1)%pages();}
    void copyTo(String[] slots){for(int i=0;i<PAGE_SIZE;i++)slots[i]=tool(i);}
}
