package ru.folio.notes;
import org.json.*;

public final class AgentNotebookTests {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Ink.Stroke stroke(float x,float y){Ink.Stroke s=new Ink.Stroke();s.points.add(new Ink.Pt(x,y,1,0,0));s.points.add(new Ink.Pt(x+10,y+15,.6f,0,0));return s;}
    public static void main(String[] args)throws Exception{
        MainActivity a=new MainActivity();a.docs.add(a.current);Ink.Doc other=new Ink.Doc();other.title="Другие записи";a.docs.add(other);
        Ink.Page p=other.page();Ink.Stroke genuine=stroke(10,10);p.strokes.add(genuine);
        Ink.Stroke ai=stroke(10,10);ai.action="ai:sample";p.strokes.add(ai);
        Ink.Stroke erased=stroke(10,10);erased.cuts.add(new Ink.Cut(12,12,2));p.strokes.add(erased);
        Ink.Layer hidden=new Ink.Layer();hidden.id="hidden";hidden.visible=false;p.layers.add(hidden);Ink.Stroke concealed=stroke(10,10);concealed.layer="hidden";p.strokes.add(concealed);
        AgentNotebook scan=new AgentNotebook(a);
        check(scan.library().getJSONArray("notebooks").length()==2,"lists notebooks");
        JSONObject pages=scan.pages(new JSONObject().put("document_id",other.id));check(pages.getJSONArray("pages").getJSONObject(0).getString("page_id").equals(p.id),"finds other notebook page");
        JSONObject req=new JSONObject().put("document_id",other.id).put("page_id",p.id).put("revision",scan.revision(other)).put("bounds",new JSONArray("[0,0,100,100]"));
        req.put("samples",new JSONArray("[{text:'я',rect:[0.05,0.05,0.3,0.3]}]"));
        JSONArray strokes=scan.samples(req).getJSONArray("samples").getJSONObject(0).getJSONArray("strokes");
        check(strokes.length()==1&&strokes.getJSONObject(0).getString("action").equals(genuine.action),"only whole visible user ink is copied");
        other.modified++;try{scan.samples(req);throw new AssertionError("stale revision accepted");}catch(AgentBridge.Fault expected){check(expected.code==409,"stale page rejected");}
        req.put("revision",scan.revision(other)).put("samples",new JSONArray("[{text:'я',rect:[0.1,0.1,0.12,0.12]}]"));
        try{scan.samples(req);throw new AssertionError("incomplete sample accepted");}catch(AgentBridge.Fault expected){check(expected.code==400,"partial stroke rejected");}
        System.out.println("PASS notebook listing, cross-page revision, hidden/erased/AI ink exclusion and bounded sample extraction");
    }
}
