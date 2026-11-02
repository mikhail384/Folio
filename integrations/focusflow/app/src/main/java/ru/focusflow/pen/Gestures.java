package ru.focusflow.pen;
import java.util.*;
public final class Gestures {
    public static final Map<String,String> ALL;
    static {
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        m.put("squeeze_single","Одиночное сжатие");m.put("squeeze_double","Двойное сжатие");
        m.put("swipe_up","Свайп вверх");m.put("swipe_down","Свайп вниз");
        m.put("swipe_up_up","Вверх → вверх");m.put("swipe_down_down","Вниз → вниз");
        m.put("swipe_up_down","Вверх → вниз");m.put("swipe_down_up","Вниз → вверх");
        ALL=Collections.unmodifiableMap(m);
    }
    public static List<String> steps(String id){if(id.equals("squeeze_single"))return Arrays.asList("pinch");if(id.equals("squeeze_double"))return Arrays.asList("pinch","pinch");return Arrays.asList(id.substring(6).split("_"));}
}
