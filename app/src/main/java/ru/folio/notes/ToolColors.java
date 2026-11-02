package ru.folio.notes;

import java.util.*;

/** Each instrument owns its ink. Navigation, selection and erasing have no color. */
final class ToolColors {
    final HashMap<String,Integer> saved=new HashMap<>();
    static boolean enabled(String tool){return !Arrays.asList("eraser","hand","lasso","protractor").contains(tool);}
    int get(String tool,int fallback){Integer value=saved.get(tool);return value==null?fallback:value;}
    void put(String tool,int color){if(enabled(tool))saved.put(tool,color|0xff000000);}
}
