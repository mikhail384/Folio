package ru.focusflow.pen;
import org.json.*;

/** Shared validation for imported, drawn and numeric screen gestures. Coordinates are percentages. */
public final class ScreenPath {
    public static JSONObject validate(JSONObject input)throws JSONException{
        JSONArray strokes=input.getJSONArray("strokes"),safe=new JSONArray();
        if(strokes.length()<1||strokes.length()>10)throw new JSONException("От 1 до 10 линий в траектории");
        long lastEnd=0;
        for(int i=0;i<strokes.length();i++){
            JSONObject s=strokes.getJSONObject(i);JSONArray points=s.getJSONArray("points"),clean=new JSONArray();
            int start=s.optInt("startMs",0),duration=s.optInt("durationMs",500);
            if(start<lastEnd||duration<50||start+duration>30000||points.length()<1||points.length()>256)throw new JSONException("Некорректное время или число точек");
            for(int j=0;j<points.length();j++){JSONArray xy=points.getJSONArray(j);if(xy.length()!=2)throw new JSONException("Нужны X и Y");double x=xy.getDouble(0),y=xy.getDouble(1);if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>100||y<0||y>100)throw new JSONException("Координаты от 0 до 100%");clean.put(new JSONArray().put(x).put(y));}
            safe.put(new JSONObject().put("points",clean).put("startMs",start).put("durationMs",duration));lastEnd=start+duration;
        }return new JSONObject().put("strokes",safe);
    }
}
