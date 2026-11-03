package ru.folio.notes;
import java.util.*;
import org.json.*;
public final class AiLearningCoreTests {
    static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static Ink.Stroke stroke(float x,float y){Ink.Stroke s=new Ink.Stroke();s.tool="fountain";s.width=2;s.points.add(new Ink.Pt(x,y,.3f,0,0));s.points.add(new Ink.Pt(x+8,y+14,.7f,0,0));return s;}
    public static void main(String[] args)throws Exception{
        Ink.Page page=new Ink.Page();Ink.Stroke own=stroke(10,10),other=stroke(300,10),ai=stroke(12,10),erased=stroke(14,10),marker=stroke(16,10);
        ai.action="ai:operation:hash";erased.cuts.add(new Ink.Cut(16,12,2));marker.tool="marker";
        page.strokes.addAll(Arrays.asList(own,other,ai,erased,marker));
        check(AiLearningCore.eligible(page,own),"ordinary user ink is eligible");
        for(Ink.Stroke s:new Ink.Stroke[]{ai,erased,marker})check(!AiLearningCore.eligible(page,s),"AI, erased and marker strokes excluded");
        check(AiLearningCore.label("Привет",98).equals("Привет"),"confident Russian word");check(AiLearningCore.label("ошибка",85)==null,"low-confidence OCR ignored");check(AiLearningCore.label("a",99)==null,"ambiguous single character ignored");check(AiLearningCore.label("a b",99)==null,"mixed text rejected");
        ArrayList<Ink.Stroke> picked=AiLearningCore.select(page,page.strokes,0,0,40,40);check(picked.size()==1&&picked.get(0)==own,"only completely overlapping ordinary stroke selected");
        check(AiLearningCore.select(page,page.strokes,0,0,15,40).isEmpty(),"cross-boundary stroke excluded");
        JSONArray one=AiLearningCore.json(picked);String id=AiLearningCore.sampleId("doc","page","Привет",one);check(id.length()==32&&id.equals(AiLearningCore.sampleId("doc","page","Привет",one)),"sample ID stable across retries");
        check(!id.equals(AiLearningCore.sampleId("other","page","Привет",one)),"samples bound to their notebook");
        String before=AiLearningCore.pageFingerprint(page);page.viewX+=100;check(before.equals(AiLearningCore.pageFingerprint(page)),"viewport changes do not cause reanalysis");page.strokes.add(stroke(60,20));check(!before.equals(AiLearningCore.pageFingerprint(page)),"changed page scheduled for reanalysis");
        check(!AiLearningCore.tiles(page).isEmpty(),"ink grouped into bounded tiles");
        System.out.println("PASS "+checks+" automatic ink eligibility, conservative labels, selection and stable checkpoint checks");
    }
}
