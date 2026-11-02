package ru.folio.notes;

import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

final class ToolSettings {
    static final String DEFAULT="pen,fountain,brush,pencil,marker,eraser,lasso";
    static ArrayList<String> ordered(MainActivity a){ArrayList<String> out=new ArrayList<>();String saved=a.prefs.getString("tool_order",DEFAULT);for(String name:saved.split(","))if(Arrays.asList(Ink.TOOLS).contains(name)&&!GuideTools.auxiliary(name)&&!out.contains(name))out.add(name);for(String name:Ink.TOOLS)if(!GuideTools.auxiliary(name)&&!out.contains(name))out.add(name);return out;}
    static ArrayList<String> visible(MainActivity a){ArrayList<String> out=new ArrayList<>();for(String name:ordered(a))if(a.prefs.getBoolean("tool_visible_"+name,Arrays.asList(DEFAULT.split(",")).contains(name)))out.add(name);if(out.isEmpty())out.add("pen");return out;}
    static ArrayList<String> panelOrder(List<String> visible){ArrayList<String> out=new ArrayList<>(visible);out.remove("hand");out.add(0,"hand");return out;}
    static void panel(MainActivity a){ToolStudio.panel(a);}
    static void options(MainActivity a,String tool){ToolStudio.options(a,tool);}
    static void eraser(MainActivity a){ToolStudio.eraser(a);}
    static void compass(MainActivity a){ToolStudio.compass(a);}
}
