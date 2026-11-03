package ru.folio.notes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded, read-only notebook views and user-ink extraction for handwriting learning. */
final class AgentNotebook {
    private final MainActivity a;
    AgentNotebook(MainActivity activity){a=activity;}
    String revision(Ink.Doc doc){return doc.modified+":"+(doc==a.current?a.revision:0);}
    Ink.Doc document(String id)throws AgentBridge.Fault{
        if(a.current!=null&&a.current.id.equals(id))return a.current;
        for(Ink.Doc d:a.docs)if(d.id.equals(id))return d;
        throw new AgentBridge.Fault(404,"Тетрадь не найдена");
    }
    Ink.Page page(Ink.Doc doc,String id)throws AgentBridge.Fault{
        for(Ink.Page p:doc.pages)if(p.id.equals(id))return p;
        throw new AgentBridge.Fault(404,"Страница не найдена");
    }
    static boolean ink(Ink.Page page,Ink.Stroke s){
        return page.visible(s)&&s.text.isEmpty()&&s.cuts.isEmpty()&&!s.action.startsWith("ai:")&&
            (s.tool.equals("pen")||s.tool.equals("fountain")||s.tool.equals("pencil")||s.tool.equals("brush"))&&!s.points.isEmpty();
    }
    static float[] bounds(Ink.Page page){
        float l=Float.MAX_VALUE,t=l,r=-l,b=-l;
        for(Ink.Stroke s:page.strokes)if(ink(page,s)){float[] q=s.bounds();l=Math.min(l,q[0]);t=Math.min(t,q[1]);r=Math.max(r,q[2]);b=Math.max(b,q[3]);}
        if(l==Float.MAX_VALUE)return new float[]{0,0,Math.max(100,page.width),Math.max(100,page.height)};
        float margin=Math.max(10,Math.min(100,Math.max(r-l,b-t)*.03f));
        return new float[]{l-margin,t-margin,r+margin,b+margin};
    }
    static JSONArray arr(float[] b)throws Exception{return new JSONArray(new double[]{b[0],b[1],b[2],b[3]});}
    static float[] rectangle(JSONArray raw)throws Exception{
        if(raw==null||raw.length()!=4)throw new AgentBridge.Fault(400,"Область должна содержать четыре координаты");
        float[] b=new float[4];for(int i=0;i<4;i++){b[i]=(float)raw.getDouble(i);if(!Float.isFinite(b[i])||Math.abs(b[i])>10000000)throw new AgentBridge.Fault(400,"Некорректные координаты");}
        if(b[2]-b[0]<1||b[3]-b[1]<1)throw new AgentBridge.Fault(400,"Область слишком мала");return b;
    }
    JSONObject library()throws Exception{
        JSONArray docs=new JSONArray();for(Ink.Doc d:a.docs){if(docs.length()>=500)break;docs.put(new JSONObject().put("id",d.id).put("title",d.title).put("folder",d.folder).put("pages",d.pages.size()).put("revision",revision(d)).put("training_allowed",AiLearningPermissions.allowed(a.prefs,d.id)));}
        return new JSONObject().put("notebooks",docs).put("truncated",a.docs.size()>500);
    }
    JSONObject pages(JSONObject req)throws Exception{
        Ink.Doc doc=document(req.getString("document_id"));JSONArray pages=new JSONArray();
        for(int i=0;i<doc.pages.size();i++){Ink.Page p=doc.pages.get(i);pages.put(new JSONObject().put("page_id",p.id).put("number",i+1).put("ink_bounds",arr(bounds(p))).put("stroke_count",p.strokes.size()));}
        return new JSONObject().put("document_id",doc.id).put("title",doc.title).put("revision",revision(doc)).put("pages",pages);
    }
    JSONObject scan(JSONObject req)throws Exception{
        Ink.Doc doc=document(req.getString("document_id"));Ink.Page page=page(doc,req.getString("page_id"));
        float[] box=req.has("bounds")?rectangle(req.getJSONArray("bounds")):bounds(page);float w=box[2]-box[0],h=box[3]-box[1];
        ArrayList<Ink.Stroke> visible=new ArrayList<>();int points=0;
        for(Ink.Stroke s:page.strokes)if(ink(page,s)){float[] b=s.bounds();if(b[2]<box[0]||b[0]>box[2]||b[3]<box[1]||b[1]>box[3])continue;visible.add(s);points+=s.points.size();if(visible.size()>4000||points>100000)throw new AgentBridge.Fault(413,"Область слишком плотная: выберите меньший фрагмент");}
        float scale=Math.min(2f,1400f/Math.max(w,h));int width=Math.max(1,Math.round(w*scale)),height=Math.max(1,Math.round(h*scale));
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);try{
            Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);canvas.scale(scale,scale);canvas.translate(-box[0],-box[1]);for(Ink.Stroke s:visible)InkRenderer.stroke(canvas,s);
            ByteArrayOutputStream png=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.PNG,100,png))throw new AgentBridge.Fault(500,"Не удалось получить изображение страницы");
            return new JSONObject().put("document_id",doc.id).put("page_id",page.id).put("revision",revision(doc)).put("bounds",arr(box)).put("image_size",new JSONArray(new int[]{width,height})).put("stroke_count",visible.size()).put("image_png",Base64.encodeToString(png.toByteArray(),Base64.NO_WRAP));
        }finally{image.recycle();}
    }
    JSONObject samples(JSONObject req)throws Exception{
        Ink.Doc doc=document(req.getString("document_id"));Ink.Page page=page(doc,req.getString("page_id"));
        if(!revision(doc).equals(req.getString("revision")))throw new AgentBridge.Fault(409,"Тетрадь изменилась: прочитайте страницу снова");
        float[] box=rectangle(req.getJSONArray("bounds"));JSONArray input=req.getJSONArray("samples");
        if(input.length()<1||input.length()>16)throw new AgentBridge.Fault(400,"За раз можно выбрать от 1 до 16 образцов");
        JSONArray result=new JSONArray();int totalPoints=0;
        for(int i=0;i<input.length();i++){
            JSONObject item=input.getJSONObject(i);String text=item.getString("text");JSONArray area=item.getJSONArray("rect");
            if(text.isEmpty()||text.length()>32||text.matches(".*[\\r\\n].*")||area.length()!=4)throw new AgentBridge.Fault(400,"Недопустимая подпись образца");
            float[] rel=new float[4];for(int j=0;j<4;j++){rel[j]=(float)area.getDouble(j);if(!Float.isFinite(rel[j])||rel[j]<0||rel[j]>1)throw new AgentBridge.Fault(400,"Координаты образца должны быть от 0 до 1");}
            if(rel[2]<=rel[0]||rel[3]<=rel[1])throw new AgentBridge.Fault(400,"Пустая область образца");
            float l=box[0]+rel[0]*(box[2]-box[0]),t=box[1]+rel[1]*(box[3]-box[1]),r=box[0]+rel[2]*(box[2]-box[0]),b=box[1]+rel[3]*(box[3]-box[1]);
            JSONArray strokes=new JSONArray();int samplePoints=0;
            for(Ink.Stroke s:page.strokes)if(ink(page,s)){
                float[] sb=s.bounds();if((sb[0]+sb[2])/2<l||(sb[0]+sb[2])/2>r||(sb[1]+sb[3])/2<t||(sb[1]+sb[3])/2>b)continue;
                int in=0;for(Ink.Pt p:s.points)if(p.x>=l&&p.x<=r&&p.y>=t&&p.y<=b)in++;
                if(in*100<s.points.size()*85)continue;
                samplePoints+=s.points.size();totalPoints+=s.points.size();if(strokes.length()>=128||samplePoints>16000||totalPoints>40000)throw new AgentBridge.Fault(413,"Выберите меньшие образцы");strokes.put(s.json());
            }
            if(strokes.length()==0)throw new AgentBridge.Fault(400,"В области «"+text+"» нет цельных штрихов; уточните границы");
            result.put(new JSONObject().put("text",text).put("strokes",strokes));
        }
        return new JSONObject().put("document_id",doc.id).put("page_id",page.id).put("revision",revision(doc)).put("samples",result);
    }
    /** Capture document identity on the UI thread; disk reads happen on the calling worker. */
    java.util.concurrent.Callable<JSONObject> trainingExamples(JSONObject req,AgentBridge bridge,int generation)throws Exception{
        String doc=req.getString("document_id");boolean calibration=AiCorpusStore.CALIBRATION.equals(doc);if(!calibration)document(doc);
        String target=bridge.trainingTarget();if(target.isEmpty())throw new AgentBridge.Fault(409,"Сначала подключите облако");
        String cursor=req.optString("cursor","");int limit=req.optInt("limit",8);
        java.io.File root=new java.io.File(a.getFilesDir(),"ai-training-corpus");
        return ()->{try{return new AiCorpusStore(root).page(doc,cursor,limit,target,()->bridge.epoch==generation&&bridge.enabled()&&target.equals(bridge.trainingTarget())&&(calibration||AiLearningPermissions.allowed(a.prefs,doc)));}
            catch(SecurityException e){throw new AgentBridge.Fault(403,"Нет разрешения на обучение по этой тетради");}
            catch(IllegalArgumentException e){throw new AgentBridge.Fault(400,e.getMessage());}};
    }
}
