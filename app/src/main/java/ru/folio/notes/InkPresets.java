package ru.folio.notes;

import java.util.HashMap;

/** Three editable colors per tool. Arbitrary ink colors do not overwrite a preset. */
final class InkPresets {
    final HashMap<String,int[]> colors=new HashMap<>();
    final HashMap<String,Integer> selection=new HashMap<>();
    int[] forTool(String tool){return colors.computeIfAbsent(tool,ColorDefaults::forTool);}
    int color(String tool,int slot){return forTool(tool)[slot];}
    void put(String tool,int slot,int color){if(ToolColors.enabled(tool)&&slot>=0&&slot<3)forTool(tool)[slot]=color|0xff000000;}
    void select(String tool,int slot){if(slot>=0&&slot<3)selection.put(tool,slot);}
    int selected(String tool,int active){int[] values=forTool(tool);Integer slot=selection.get(tool);if(slot!=null&&values[slot]==active)return slot;for(int i=0;i<3;i++)if(values[i]==active)return i;return -1;}
}
