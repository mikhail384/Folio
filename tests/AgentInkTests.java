package ru.folio.notes;
import org.json.*;
import java.util.*;
public final class AgentInkTests {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    interface Attempt{void run()throws Exception;}
    static void rejects(Attempt a)throws Exception{boolean failed=false;try{a.run();}catch(Exception expected){failed=true;}check(failed,"invalid batch accepted");}
    public static void main(String[] args)throws Exception{
        JSONArray raw=new JSONArray("[{points:[[1,2,0.8],[3,4,1]],color:'#345678',width:2}]");String action=AgentInk.action("operation_123",raw);
        ArrayList<Ink.Stroke> batch=AgentInk.prepare(raw,action,"base");check(batch.size()==1,"stroke count");check(batch.get(0).points.get(0).p==.8f,"pressure");check(batch.get(0).color==0xff345678,"color");
        Ink.Page page=new Ink.Page();Ink.Stroke user=new Ink.Stroke();user.points.add(new Ink.Pt(99,99,1,0,0));page.strokes.add(user);page.strokes.addAll(batch);
        check(action.equals(AgentInk.existing(page,"operation_123")),"operation id persisted on strokes");
        Ink.Doc doc=new Ink.Doc();doc.pages.set(0,page);Ink.Doc restored=Ink.Doc.parse(new JSONObject(doc.json().toString()));check(action.equals(AgentInk.existing(restored.page(),"operation_123")),"save/load keeps idempotency tag");
        check(AgentInk.remove(page,action)==1,"selective undo count");check(page.strokes.size()==1&&page.strokes.get(0)==user,"selective undo preserves user notes");check(AgentInk.remove(page,action)==0,"repeat undo harmless");
        JSONArray different=new JSONArray("[{points:[[1,2],[7,8]]}]");check(!AgentInk.action("operation_123",different).equals(action),"different payload tagged differently");
        rejects(()->AgentInk.action("bad",raw));rejects(()->AgentInk.prepare(new JSONArray(),action,"base"));
        for(String item:new String[]{"{points:[[1e100,2]]}","{points:[[1,2,0]]}","{points:[[1,2,1,20]]}","{points:[[1,2]],width:0}","{points:[[1,2]],color:'red'}","{points:[[1,2]],tool:'eraser'}","{points:[]}"})rejects(()->AgentInk.prepare(new JSONArray("["+item+"]"),action,"base"));
        int size=page.strokes.size();rejects(()->AgentInk.prepare(new JSONArray("[{points:[[1,2]]},{points:[[1e100,2]]}]"),action,"base"));check(page.strokes.size()==size,"validation leaves page untouched");
        System.out.println("PASS "+checks+" agent ink validation, durable tags, atomic preparation and selective undo checks");
    }
}
