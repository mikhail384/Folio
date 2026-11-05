package ru.folio.notes;

import java.io.IOException;
import java.io.Writer;
import org.json.JSONObject;

/** Compact streaming serializer used by save and export to avoid a second object tree. */
final class InkJsonWriter {
    private InkJsonWriter(){}
    static void write(Ink.Doc doc,Writer out)throws IOException{
        out.write("{\"format\":\"folio\",\"version\":3,\"id\":");string(out,doc.id);out.write(",\"title\":");string(out,doc.title);out.write(",\"folder\":");string(out,doc.folder);out.write(",\"modified\":");out.write(Long.toString(doc.modified));out.write(",\"cover\":");out.write(Integer.toString(doc.cover));out.write(",\"current\":");out.write(Integer.toString(doc.current));out.write(",\"audio\":[");
        for(int i=0;i<doc.audio.size();i++){if(i>0)out.write(',');Ink.Audio a=doc.audio.get(i);out.write("{\"file\":");string(out,a.file);out.write(",\"name\":");string(out,a.name);out.write(",\"page\":");string(out,a.page);out.write(",\"duration\":");out.write(Long.toString(a.duration));out.write(",\"created\":");out.write(Long.toString(a.created));out.write('}');}
        out.write("],\"pages\":[");for(int i=0;i<doc.pages.size();i++){if(i>0)out.write(',');page(doc.pages.get(i),out);}out.write("]}");
    }
    private static void page(Ink.Page p,Writer out)throws IOException{
        out.write("{\"id\":");string(out,p.id);out.write(",\"activeLayer\":");string(out,p.activeLayer);out.write(",\"bookmark\":");string(out,p.bookmark);out.write(",\"layers\":[");
        for(int i=0;i<p.layers.size();i++){if(i>0)out.write(',');Ink.Layer l=p.layers.get(i);out.write("{\"id\":");string(out,l.id);out.write(",\"name\":");string(out,l.name);out.write(",\"visible\":");out.write(Boolean.toString(l.visible));out.write(",\"locked\":");out.write(Boolean.toString(l.locked));out.write('}');}
        out.write("],\"pattern\":");string(out,p.pattern);out.write(",\"background\":");string(out,p.background);out.write(",\"paper\":");out.write(Integer.toString(p.paper));out.write(",\"lines\":");out.write(Integer.toString(p.lines));out.write(",\"width\":");number(out,p.width);out.write(",\"height\":");number(out,p.height);out.write(",\"spacing\":");number(out,p.spacing);out.write(",\"infinite\":");out.write(Boolean.toString(p.infinite));out.write(",\"hasViewport\":");out.write(Boolean.toString(p.hasViewport));out.write(",\"viewX\":");number(out,p.viewX);out.write(",\"viewY\":");number(out,p.viewY);out.write(",\"viewZoom\":");number(out,p.viewZoom);out.write(",\"strokes\":[");
        for(int i=0;i<p.strokes.size();i++){if(i>0)out.write(',');stroke(p.strokes.get(i),out);}out.write("]}");
    }
    private static void stroke(Ink.Stroke s,Writer out)throws IOException{
        out.write("{\"layer\":");string(out,s.layer);out.write(",\"texture\":");out.write(Integer.toString(s.texture));out.write(",\"tool\":");string(out,s.tool);out.write(",\"color\":");out.write(Integer.toString(s.color));out.write(",\"width\":");number(out,s.width);out.write(",\"text\":");string(out,s.text);out.write(",\"action\":");string(out,s.action);out.write(",\"revealed\":");out.write(Boolean.toString(s.revealed));out.write(",\"cuts\":[");
        for(int i=0;i<s.cuts.size();i++){if(i>0)out.write(',');Ink.Cut c=s.cuts.get(i);number(out,c.x);out.write(',');number(out,c.y);out.write(',');number(out,c.r);}out.write("],\"cutEnds\":[");for(int i=0;i<s.cuts.size();i++){if(i>0)out.write(',');Ink.Cut c=s.cuts.get(i);number(out,c.z);out.write(',');number(out,c.w);}out.write("],\"points\":[");
        for(int i=0;i<s.points.size();i++){if(i>0)out.write(',');Ink.Pt p=s.points.get(i);number(out,p.x);out.write(',');number(out,p.y);out.write(',');number(out,p.p);out.write(',');number(out,p.tilt);out.write(',');number(out,p.angle);}out.write("],\"azimuths\":[");for(int i=0;i<s.points.size();i++){if(i>0)out.write(',');number(out,s.points.get(i).azimuth);}out.write("]}");
    }
    private static void string(Writer out,String value)throws IOException{out.write(JSONObject.quote(value==null?"":value));}
    private static void number(Writer out,float value)throws IOException{if(!Float.isFinite(value))throw new IOException("Некорректное число в рукописном документе");out.write(Float.toString(value));}
}
