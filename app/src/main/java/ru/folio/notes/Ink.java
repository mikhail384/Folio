package ru.folio.notes;

import java.util.*;
import org.json.*;

/** Platform-independent document geometry. Committed strokes are immutable. */
public final class Ink {
    public static final String[] TOOLS={"pen","fountain","pencil","brush","marker","rainbow","tape","laser","eraser","lasso","line","arrow","rect","ellipse","triangle","compass","protractor","text","hand"};
    public static final String[] LABELS={"Шариковая ручка","Перьевая ручка","Карандаш","Кисть","Маркер","Радужная ручка","Лента","Лазер","Ластик","Лассо","Линейка","Стрелка","Прямоугольник","Эллипс","Треугольник","Циркуль","Транспортир","Текст","Навигация"};
    public static String label(String tool){int i=Arrays.asList(TOOLS).indexOf(tool);return i<0?tool:LABELS[i];}
    public static float defaultWidth(String tool){return tool.equals("brush")?12:tool.equals("marker")?22:tool.equals("tape")?40:tool.equals("laser")?5:3;}
    public static String id(){return UUID.randomUUID().toString();}
    public static float clamp(float v,float lo,float hi){return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):lo;}
    public static class Pt {
        public final float x,y,p,tilt,angle,azimuth;
        public Pt(float x,float y,float p,float t,float a){this(x,y,p,t,a,a);}
        public Pt(float x,float y,float p,float t,float a,float bearing){this.x=x;this.y=y;this.p=clamp(p,0.02f,2);tilt=clamp(t,0,1.57f);angle=Float.isFinite(a)?a%(float)(Math.PI*2):0;azimuth=Float.isFinite(bearing)?bearing%(float)(Math.PI*2):0;}
    }
    public static class Stroke {
        public String tool="pen",text="",layer="base";public int texture;
        public String action=Ink.id();
        public boolean revealed;
        public int color=0xff28344b;
        public float width=3;
        public final ArrayList<Pt> points=new ArrayList<>();
        public final ArrayList<Cut> cuts=new ArrayList<>();
        public Stroke copy(){Stroke s=new Stroke();s.tool=tool;s.text=text;s.layer=layer;s.texture=texture;s.color=color;s.width=width;s.action=action;s.revealed=revealed;s.points.addAll(points);s.cuts.addAll(cuts);return s;}
        public Stroke translated(float dx,float dy){Stroke s=copy();s.points.clear();for(Pt p:points)s.points.add(new Pt(p.x+dx,p.y+dy,p.p,p.tilt,p.angle,p.azimuth));s.cuts.clear();for(Cut cut:cuts)s.cuts.add(new Cut(cut.x+dx,cut.y+dy,cut.r,cut.z+dx,cut.w+dy));return s;}
        public Stroke cut(float x,float y,float radius){Stroke s=copy();s.cuts.add(new Cut(x,y,radius));return s;}
        public boolean covered(float x,float y,float radius){for(int i=cuts.size()-1;i>=0;i--)if(cuts.get(i).covers(x,y,radius))return true;return false;}
        public Stroke mirrored(float cx,float cy,boolean horizontal){Stroke s=copy();s.points.clear();s.cuts.clear();for(Pt p:points)s.points.add(new Pt(horizontal?2*cx-p.x:p.x,horizontal?p.y:2*cy-p.y,p.p,p.tilt,horizontal?(float)Math.PI-p.angle:-p.angle,horizontal?(float)Math.PI-p.azimuth:-p.azimuth));for(Cut c:cuts)s.cuts.add(new Cut(horizontal?2*cx-c.x:c.x,horizontal?c.y:2*cy-c.y,c.r,horizontal?2*cx-c.z:c.z,horizontal?c.w:2*cy-c.w));return s;}
        public Stroke transformed(float cx,float cy,float factor,float radians){Stroke result=copy();result.width*=factor;result.points.clear();result.cuts.clear();float cos=(float)Math.cos(radians),sin=(float)Math.sin(radians);for(Pt p:points){float x=(p.x-cx)*factor,y=(p.y-cy)*factor;result.points.add(new Pt(cx+x*cos-y*sin,cy+x*sin+y*cos,p.p,p.tilt,p.angle+radians,p.azimuth+radians));}for(Cut cut:cuts){float x=(cut.x-cx)*factor,y=(cut.y-cy)*factor;float zx=(cut.z-cx)*factor,zy=(cut.w-cy)*factor;result.cuts.add(new Cut(cx+x*cos-y*sin,cy+x*sin+y*cos,cut.r*factor,cx+zx*cos-zy*sin,cy+zx*sin+zy*cos));}return result;}
        public float[] bounds(){
            float l=Float.MAX_VALUE,t=l,r=-l,b=-l;
            for(Pt p:points){l=Math.min(l,p.x);r=Math.max(r,p.x);t=Math.min(t,p.y);b=Math.max(b,p.y);}
            if(tool.equals("text")&&!points.isEmpty()){String[] rows=text.split("\n",-1);int longest=0;for(String row:rows)longest=Math.max(longest,row.length());r=Math.max(r,l+longest*width*.65f);b=t+width*1.3f*rows.length;}
            return new float[]{l-width,t-width,r+width,b+width};
        }
        public boolean hit(float x,float y,float radius){
            if(covered(x,y,radius))return false;
            float[] b=bounds();if(x<b[0]-radius||x>b[2]+radius||y<b[1]-radius||y>b[3]+radius)return false;
            if(tool.equals("marker"))return InkGeometry.markerHit(this,x,y,radius);
            float hit=radius+width*(tool.equals("brush")?.9f:.6f);
            if(tool.equals("text"))return true;
            if(points.size()==1)return dist(x,y,points.get(0).x,points.get(0).y)<hit;
            if(tool.equals("rect")||tool.equals("ellipse")){
                Pt a=points.get(0),z=points.get(points.size()-1);
                float l=Math.min(a.x,z.x),r=Math.max(a.x,z.x),t=Math.min(a.y,z.y),bottom=Math.max(a.y,z.y);
                if(tool.equals("rect"))return segment(x,y,l,t,r,t)<hit||segment(x,y,r,t,r,bottom)<hit||segment(x,y,r,bottom,l,bottom)<hit||segment(x,y,l,bottom,l,t)<hit;
                float rx=(r-l)/2,ry=(bottom-t)/2;if(rx<1||ry<1)return segment(x,y,a.x,a.y,z.x,z.y)<hit;
                double ang=Math.atan2((y-(t+ry))/ry,(x-(l+rx))/rx);
                return dist(x,y,(float)(l+rx+rx*Math.cos(ang)),(float)(t+ry+ry*Math.sin(ang)))<hit;
            }
            for(int i=1;i<points.size();i++){Pt a=points.get(i-1),z=points.get(i);if(segment(x,y,a.x,a.y,z.x,z.y)<hit)return true;}
            return false;
        }
        JSONObject json() throws JSONException {
            JSONObject o=new JSONObject();o.put("layer",layer);o.put("texture",texture);o.put("tool",tool);o.put("color",color);o.put("width",width);o.put("text",text);o.put("action",action);o.put("revealed",revealed);
            JSONArray erased=new JSONArray(),ends=new JSONArray();for(Cut cut:cuts){erased.put(cut.x);erased.put(cut.y);erased.put(cut.r);ends.put(cut.z);ends.put(cut.w);}o.put("cuts",erased);o.put("cutEnds",ends);
            JSONArray a=new JSONArray();for(Pt p:points){a.put(p.x);a.put(p.y);a.put(p.p);a.put(p.tilt);a.put(p.angle);}o.put("points",a);JSONArray bearings=new JSONArray();for(Pt p:points)bearings.put(p.azimuth);o.put("azimuths",bearings);return o;
        }
        static Stroke parse(JSONObject o) throws JSONException {
            Stroke s=new Stroke();s.tool=o.getString("tool");if(!Arrays.asList(TOOLS).contains(s.tool))throw new JSONException("Неизвестный инструмент");
            s.layer=o.optString("layer","base");s.texture=Math.max(0,Math.min(5,o.optInt("texture",0)));s.action=o.optString("action",s.action);s.revealed=o.optBoolean("revealed",false);s.color=o.optInt("color",0xff28344b);s.width=clamp((float)o.optDouble("width",3),.2f,200);s.text=o.optString("text","");if(s.text.length()>20000)throw new JSONException("Слишком длинный текст");
            JSONArray erased=o.optJSONArray("cuts");if(erased!=null){if(erased.length()%3!=0||erased.length()>600000)throw new JSONException("Повреждена маска ластика");for(int i=0;i<erased.length();i+=3){float x=(float)erased.getDouble(i),y=(float)erased.getDouble(i+1),r=(float)erased.getDouble(i+2);if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>10000000||Math.abs(y)>10000000||!Float.isFinite(r)||r<=0||r>1000)throw new JSONException("Повреждён круг ластика");s.cuts.add(new Cut(x,y,r));}}
            JSONArray ends=o.optJSONArray("cutEnds");if(ends!=null){if(ends.length()!=s.cuts.size()*2)throw new JSONException("Повреждён путь ластика");for(int i=0;i<s.cuts.size();i++){Cut old=s.cuts.get(i);float z=(float)ends.getDouble(i*2),w=(float)ends.getDouble(i*2+1);if(!Float.isFinite(z)||!Float.isFinite(w)||Math.abs(z)>10000000||Math.abs(w)>10000000)throw new JSONException("Повреждён путь ластика");s.cuts.set(i,new Cut(old.x,old.y,old.r,z,w));}}
            JSONArray a=o.getJSONArray("points");JSONArray bearings=o.optJSONArray("azimuths");if(bearings!=null&&bearings.length()!=a.length()/5)throw new JSONException("Invalid tilt bearings");if(a.length()%5!=0||a.length()>1500000)throw new JSONException("Повреждены точки");
            for(int i=0;i<a.length();i+=5){float x=(float)a.getDouble(i),y=(float)a.getDouble(i+1);if(!Float.isFinite(x)||!Float.isFinite(y)||Math.abs(x)>10000000||Math.abs(y)>10000000)throw new JSONException("Повреждены координаты");s.points.add(new Pt(x,y,(float)a.getDouble(i+2),(float)a.getDouble(i+3),(float)a.getDouble(i+4),bearings==null?(float)a.getDouble(i+4):(float)bearings.getDouble(i/5)));}return s;
        }
    }
    public static final class Cut {
        public final float x,y,r,z,w;
        public Cut(float x,float y,float r){this(x,y,r,x,y);}
        public Cut(float x,float y,float r,float z,float w){this.x=x;this.y=y;this.r=r;this.z=z;this.w=w;}
        public boolean covers(float px,float py,float radius){return Ink.segment(px,py,x,y,z,w)+radius<=r+.0001f;}
        boolean covers(Cut cut){return r>=cut.r&&covers(cut.x,cut.y,cut.r)&&covers(cut.z,cut.w,cut.r);}
        float[] bounds(){return new float[]{Math.min(x,z)-r,Math.min(y,w)-r,Math.max(x,z)+r,Math.max(y,w)+r};}
    }
    public static float dist(float x,float y,float a,float b){return (float)Math.hypot(x-a,y-b);}
    public static float segment(float x,float y,float a,float b,float c,float d){float dx=c-a,dy=d-b;float q=dx*dx+dy*dy;float t=q==0?0:clamp(((x-a)*dx+(y-b)*dy)/q,0,1);return dist(x,y,a+t*dx,b+t*dy);}
    public static float segmentsDistance(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float ux=bx-ax,uy=by-ay,vx=dx-cx,vy=dy-cy,cross=ux*vy-uy*vx;
        if(Math.abs(cross)>1e-9f){float qx=cx-ax,qy=cy-ay,t=(qx*vy-qy*vx)/cross,s=(qx*uy-qy*ux)/cross;if(t>=0&&t<=1&&s>=0&&s<=1)return 0;}
        return Math.min(Math.min(Ink.segment(ax,ay,cx,cy,dx,dy),Ink.segment(bx,by,cx,cy,dx,dy)),Math.min(Ink.segment(cx,cy,ax,ay,bx,by),Ink.segment(dx,dy,ax,ay,bx,by)));
    }
    public static boolean inside(float x,float y,List<Pt> poly){boolean in=false;for(int i=0,j=poly.size()-1;i<poly.size();j=i++){Pt a=poly.get(i),b=poly.get(j);if(((a.y>y)!=(b.y>y))&&(x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x))in=!in;}return in;}
    public static final class Layer {
        public String id="base",name="Основной слой";public boolean visible=true,locked;
        public Layer copy(){Layer l=new Layer();l.id=id;l.name=name;l.visible=visible;l.locked=locked;return l;}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("visible",visible).put("locked",locked);}
    }
    public static final class Audio {
        public String file="",name="Аудиозаметка",page="";public long duration,created=System.currentTimeMillis();
        public Audio copy(){Audio a=new Audio();a.file=file;a.name=name;a.page=page;a.duration=duration;a.created=created;return a;}
        JSONObject json()throws JSONException{return new JSONObject().put("file",file).put("name",name).put("page",page).put("duration",duration).put("created",created);}
    }
    public static class Page {
        public String id=Ink.id(),pattern="grid",background="";
        public int paper=0xffffffff,lines=0x4d000000;
        public float width=1200,height=1640,spacing=32;
        public boolean infinite=true,hasViewport;public float viewX=600,viewY=820,viewZoom=1;
        public String activeLayer="base",bookmark="";
        public ArrayList<Layer> layers=new ArrayList<>();
        public Page(){layers.add(new Layer());}
        public Layer layer(String id){for(Layer l:layers)if(l.id.equals(id))return l;return layers.get(0);}
        public boolean visible(Stroke s){return layer(s.layer).visible;}
        public boolean editable(Stroke s){Layer l=layer(s.layer);return l.visible&&!l.locked;}
        public boolean writable(){Layer l=layer(activeLayer);return l.visible&&!l.locked;}
        public int rank(String id){for(int i=0;i<layers.size();i++)if(layers.get(i).id.equals(id))return i;return 0;}
        public void order(){strokes.sort((a,b)->Integer.compare(rank(a.layer),rank(b.layer)));}
        public ArrayList<Stroke> strokes=new ArrayList<>();
        public Page copy(){Page p=new Page();p.id=id;p.activeLayer=activeLayer;p.bookmark=bookmark;p.layers.clear();for(Layer layer:layers)p.layers.add(layer.copy());p.pattern=pattern;p.background=background;p.paper=paper;p.lines=lines;p.width=width;p.height=height;p.spacing=spacing;p.infinite=infinite;p.hasViewport=hasViewport;p.viewX=viewX;p.viewY=viewY;p.viewZoom=viewZoom;p.strokes=new ArrayList<>(strokes);return p;}
        JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("id",id);o.put("activeLayer",activeLayer);o.put("bookmark",bookmark);JSONArray ls=new JSONArray();for(Layer l:layers)ls.put(l.json());o.put("layers",ls);o.put("pattern",pattern);o.put("background",background);o.put("paper",paper);o.put("lines",lines);o.put("width",width);o.put("height",height);o.put("spacing",spacing);o.put("infinite",infinite);o.put("hasViewport",hasViewport);o.put("viewX",viewX);o.put("viewY",viewY);o.put("viewZoom",viewZoom);JSONArray a=new JSONArray();for(Stroke s:strokes)a.put(s.json());o.put("strokes",a);return o;}
        static Page parse(JSONObject o) throws JSONException {Page p=new Page();p.id=o.optString("id",p.id);p.bookmark=o.optString("bookmark","");if(p.bookmark.length()>120)throw new JSONException("Слишком длинная закладка");
            JSONArray ls=o.optJSONArray("layers");if(ls!=null){if(ls.length()==0||ls.length()>64)throw new JSONException("Допустимо до 64 слоев");p.layers.clear();HashSet<String> ids=new HashSet<>();for(int n=0;n<ls.length();n++){JSONObject l=ls.getJSONObject(n);Layer layer=new Layer();layer.id=l.getString("id");layer.name=l.optString("name","Слой");if(!layer.id.matches("[a-zA-Z0-9_-]{1,80}")||!ids.add(layer.id)||layer.name.length()>120)throw new JSONException("Поврежден слой");layer.visible=l.optBoolean("visible",true);layer.locked=l.optBoolean("locked",false);p.layers.add(layer);}}
            p.activeLayer=o.optString("activeLayer",p.layers.get(0).id);if(p.layers.stream().noneMatch(l->l.id.equals(p.activeLayer)))p.activeLayer=p.layers.get(0).id;p.pattern=o.optString("pattern","grid");p.background=o.optString("background","");if(!p.background.isEmpty()&&!p.background.matches("[a-zA-Z0-9_-]+\\.(png|jpg|pdf)"))throw new JSONException("Некорректный ресурс");p.infinite=o.optBoolean("infinite",p.background.isEmpty());p.hasViewport=o.optBoolean("hasViewport",false);p.viewX=clamp((float)o.optDouble("viewX",600),-10000000,10000000);p.viewY=clamp((float)o.optDouble("viewY",820),-10000000,10000000);p.viewZoom=clamp((float)o.optDouble("viewZoom",1),.000001f,12);p.paper=o.optInt("paper",p.paper);p.lines=o.optInt("lines",p.lines);p.width=clamp((float)o.optDouble("width",1200),200,10000);p.height=clamp((float)o.optDouble("height",1640),200,10000);p.spacing=clamp((float)o.optDouble("spacing",32),8,160);JSONArray a=o.getJSONArray("strokes");if(a.length()>100000)throw new JSONException("Слишком много штрихов");for(int i=0;i<a.length();i++){Stroke st=Stroke.parse(a.getJSONObject(i));if(p.layers.stream().noneMatch(l->l.id.equals(st.layer)))st.layer=p.layers.get(0).id;p.strokes.add(st);}p.order();return p;}
    }
    public static class Doc {
        public String id=Ink.id(),title="Новая тетрадь",folder="";
        public long modified=System.currentTimeMillis();
        public int cover=0xffced6f9,current=0;
        public ArrayList<Audio> audio=new ArrayList<>();public ArrayList<Page> pages=new ArrayList<>();
        public Doc(){pages.add(new Page());}
        public Doc copy(){Doc d=new Doc();d.id=id;d.title=title;d.folder=folder;d.modified=modified;d.cover=cover;d.current=current;for(Audio a:audio)d.audio.add(a.copy());d.pages.clear();for(Page p:pages)d.pages.add(p.copy());return d;}
        public Page page(){current=Math.max(0,Math.min(current,pages.size()-1));return pages.get(current);}
        public JSONObject json() throws JSONException {JSONObject o=new JSONObject();o.put("format","folio");o.put("version",3);o.put("id",id);o.put("title",title);o.put("folder",folder);o.put("modified",modified);o.put("cover",cover);o.put("current",current);JSONArray records=new JSONArray();for(Audio record:audio)records.put(record.json());o.put("audio",records);JSONArray a=new JSONArray();for(Page p:pages)a.put(p.json());o.put("pages",a);return o;}
        public static Doc parse(JSONObject o) throws JSONException {if(!"folio".equals(o.getString("format"))||(o.getInt("version")<1||o.getInt("version")>3))throw new JSONException("Неподдерживаемый формат Folio");Doc d=new Doc();d.title=o.optString("title","Импорт");d.folder=o.optString("folder","");d.modified=o.optLong("modified",System.currentTimeMillis());d.cover=o.optInt("cover",d.cover);d.current=o.optInt("current",0);d.pages.clear();JSONArray a=o.getJSONArray("pages");if(a.length()==0||a.length()>2000)throw new JSONException("Допустимо от 1 до 2000 страниц");for(int i=0;i<a.length();i++)d.pages.add(Page.parse(a.getJSONObject(i)));JSONArray records=o.optJSONArray("audio");if(records!=null){if(records.length()>2000)throw new JSONException("Слишком много записей");for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);Audio record=new Audio();record.file=r.getString("file");if(!record.file.matches("[a-zA-Z0-9_-]+\\.m4a"))throw new JSONException("Поврежден аудиоресурс");record.name=r.optString("name","Аудиозаметка");record.page=r.optString("page","");record.duration=Math.max(0,r.optLong("duration",0));record.created=r.optLong("created",0);d.audio.add(record);}}d.page();return d;}
    }
    /** A history transaction covers a whole stroke, erase gesture or lasso move. */
    public static class History {
        private final ArrayDeque<Page> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
        public void before(Page p){undo.push(p.copy());while(undo.size()>60)undo.removeLast();redo.clear();}
        public Page undo(Page current){if(undo.isEmpty())return current;redo.push(current.copy());return undo.pop();}
        public Page redo(Page current){if(redo.isEmpty())return current;undo.push(current.copy());return redo.pop();}
        public boolean canUndo(){return !undo.isEmpty();}
        public boolean canRedo(){return !redo.isEmpty();}
        public void clear(){undo.clear();redo.clear();}
    }
}
