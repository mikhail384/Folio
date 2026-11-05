package ru.folio.notes;

import org.json.*;
import java.util.*;
import java.io.*;

public final class DocumentUpgradeTests {
    static int assertions;
    static void check(boolean yes,String name){if(!yes)throw new AssertionError(name);assertions++;}
    public static void main(String[] args)throws Exception{
        Ink.Doc doc=new Ink.Doc();doc.title="21.10.2026 (2)";Ink.Page page=doc.page();page.width=10000;page.height=7600;page.infinite=false;page.paper=0xff000000;page.lines=0x4dffffff;
        Ink.Stroke stroke=new Ink.Stroke();stroke.tool="brush";stroke.width=180;for(int i=0;i<10000;i++)stroke.points.add(new Ink.Pt(i*.6f,(float)Math.sin(i*.02)*500,.02f+i%150/100f,.8f,i*.1f,i*.03f));
        for(int i=0;i<1000;i++)stroke.cuts.add(new Ink.Cut(i*4,-300,25,i*4+90,500));page.strokes.add(stroke);
        JSONObject encoded=doc.json();check(encoded.getInt("version")==3,"new mask endpoints have explicit version 3");StringWriter streamed=new StringWriter();InkJsonWriter.write(doc,streamed);Ink.Doc streamLoaded=Ink.Doc.parse(new JSONObject(streamed.toString()));check(streamLoaded.pages.size()==doc.pages.size()&&streamLoaded.page().strokes.get(0).points.size()==10000&&streamLoaded.page().strokes.get(0).cuts.size()==1000,"streamed JSON round trips large geometry and eraser masks");Ink.Doc loaded=Ink.Doc.parse(new JSONObject(encoded.toString()));Ink.Stroke restored=loaded.page().strokes.get(0);
        check(loaded.title.equals(doc.title)&&loaded.page().width==10000&&loaded.page().height==7600&&!loaded.page().infinite,"date title and full custom dimensions round trip");check(loaded.page().paper==0xff000000&&loaded.page().lines==0x4dffffff,"black paper and translucent white grid round trip");check(restored.points.size()==10000&&restored.cuts.size()==1000,"large geometry/masks retained");
        for(int i=0;i<stroke.points.size();i++){Ink.Pt a=stroke.points.get(i),b=restored.points.get(i);check(a.x==b.x&&a.y==b.y&&Math.abs(InkGeometry.delta(a.angle,b.angle))<.0001&&Math.abs(InkGeometry.delta(a.azimuth,b.azimuth))<.0001,"position and pen rotation preserved");}
        for(int i=0;i<stroke.cuts.size();i++){Ink.Cut a=stroke.cuts.get(i),b=restored.cuts.get(i);check(a.x==b.x&&a.y==b.y&&a.z==b.z&&a.w==b.w&&a.r==b.r,"every sweep endpoint and radius survives JSON");}
        Ink.History history=new Ink.History();history.before(page);Ink.Stroke masked=stroke.copy();masked.cuts.add(new Ink.Cut(100,100,30,500,100));page.strokes.set(0,masked);Ink.Page undone=history.undo(page);check(undone.strokes.get(0).cuts.size()==1000,"undo returns immutable prior mask");check(history.redo(undone).strokes.get(0).cuts.size()==1001,"redo returns full swept gesture");
        for(int version:new int[]{1,2}){JSONObject old=new JSONObject(encoded.toString());old.put("version",version);old.getJSONArray("pages").getJSONObject(0).getJSONArray("strokes").getJSONObject(0).remove("cutEnds");Ink.Doc legacy=Ink.Doc.parse(old);for(Ink.Cut cut:legacy.page().strokes.get(0).cuts)check(cut.x==cut.z&&cut.y==cut.w,"legacy circle mask remains a circle");}
        JSONObject invalid=new JSONObject(encoded.toString());invalid.getJSONArray("pages").getJSONObject(0).getJSONArray("strokes").getJSONObject(0).put("cutEnds",new JSONArray().put(1));boolean rejected=false;try{Ink.Doc.parse(invalid);}catch(JSONException expected){rejected=true;}check(rejected,"mismatched mask endpoints rejected");
        invalid=new JSONObject(encoded.toString());invalid.getJSONArray("pages").getJSONObject(0).getJSONArray("strokes").getJSONObject(0).getJSONArray("cutEnds").put(0,11000000);rejected=false;try{Ink.Doc.parse(invalid);}catch(JSONException expected){rejected=true;}check(rejected,"out-of-world mask endpoint rejected");
        System.out.println("PASS "+assertions+" production document assertions: format 3 large brush/swept-mask JSON, title/paper/size, immutable undo/redo, legacy 1/2 and malformed input. JSON-java desktop runtime; not Android.");
    }
}
