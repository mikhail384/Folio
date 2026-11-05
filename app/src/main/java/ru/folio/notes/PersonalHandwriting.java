package ru.folio.notes;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Rebuilds new, smoothed vector writing from the user's own labeled glyph variations. */
final class PersonalHandwriting {
    private static final String X_HEIGHT="аеиносхэюяaceimnorsuvwxz";
    private static final float CALIBRATION_BASELINE=235f;
    private static final int MAX_CORPUS_FILES=5000;
    private PersonalHandwriting(){}

    static final class Result {
        final ArrayList<Ink.Stroke> strokes=new ArrayList<>();
        final LinkedHashSet<String> missing=new LinkedHashSet<>();
        float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=-Float.MAX_VALUE,bottom=-Float.MAX_VALUE;
        boolean complete(){return missing.isEmpty()&&!strokes.isEmpty();}
    }
    private static final class Raw {
        final int codePoint;final JSONArray strokes;final float baseline;final String identity;
        Raw(int cp,JSONArray strokes,float baseline,String identity){this.codePoint=cp;this.strokes=strokes;this.baseline=baseline;this.identity=identity;}
    }
    private static final class Glyph {
        final int codePoint;final ArrayList<Ink.Stroke> paths;final float minX,minY,maxX,maxY,baseline,width,nominalHeight;
        final boolean entryReady,exitReady;
        Glyph(int cp,ArrayList<Ink.Stroke> source,float baseline){
            codePoint=cp;paths=source;this.baseline=baseline;float l=Float.MAX_VALUE,t=l,r=-l,b=-l,strokeWidth=0;
            for(Ink.Stroke s:source){strokeWidth=Math.max(strokeWidth,s.width);for(Ink.Pt p:s.points){l=Math.min(l,p.x);r=Math.max(r,p.x);t=Math.min(t,p.y);b=Math.max(b,p.y);}}
            minX=l;minY=t;maxX=r;maxY=b;float nominal=Math.max(1,strokeWidth);width=Math.max(r-l,nominal*.45f);nominalHeight=Math.max(b-t,nominal*.45f);
            Ink.Stroke first=source.get(0),last=source.get(source.size()-1);Ink.Pt entry=first.points.get(0),exit=last.points.get(last.points.size()-1);
            float baselineWindow=Math.max(6,nominalHeight*.42f);
            entryReady=source.size()>0&&(entry.y-baseline)>=-baselineWindow&&(entry.y-baseline)<=baselineWindow&&(entry.x-l)<=Math.max(width*.48f,nominal);
            exitReady=(exit.y-baseline)>=-baselineWindow&&(exit.y-baseline)<=baselineWindow&&(r-exit.x)<=Math.max(width*.48f,nominal);
        }
    }

    static final class Model {
        final HashMap<Integer,ArrayList<Glyph>> glyphs;
        final float xHeight;
        final int variants;
        Model(HashMap<Integer,ArrayList<Glyph>> glyphs,float xHeight,int variants){this.glyphs=glyphs;this.xHeight=xHeight;this.variants=variants;}
        boolean empty(){return glyphs.isEmpty()||xHeight<1;}
        String summary(){
            int ru=0,ruTotal=66,latin=0,latinTotal=52,digits=0;for(int cp:glyphs.keySet()){if(cp>='А'&&cp<='я'||cp=='Ё'||cp=='ё')ru++;if(cp>='A'&&cp<='Z'||cp>='a'&&cp<='z')latin++;if(cp>='0'&&cp<='9')digits++;}
            String result="Сохранено "+variants+" вариантов · "+glyphs.size()+" символов. Русские буквы: "+ru+"/"+ruTotal+"; латиница: "+latin+"/"+latinTotal+"; цифры: "+digits+"/10.";
            String missing=missingFor("абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!?;:-–—()[]{}'\"«»+−=×÷*/^_²³₀₁₂₃→√π∞≈≤≥≠∫%");
            return missing.isEmpty()?result+" Базовый набор готов.":result+" Пока нет: "+missing+".";
        }
        String missingFor(String text){LinkedHashSet<String> missing=new LinkedHashSet<>();for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(Character.isWhitespace(cp))continue;if(!glyphs.containsKey(cp))missing.add(new String(Character.toChars(cp)));}return PersonalHandwriting.join(missing,24);}

        Result layout(String text,float x,float baseline,float height,float maxWidth,int color){
            Result out=new Result();if(text==null||text.isEmpty()||text.length()>4000){out.missing.add("слишком длинный текст");return out;}
            if(empty()){out.missing.add("недостаточно образцов строчных букв");return out;}
            for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(Character.isWhitespace(cp)||cp=='\n'||cp=='\r'||cp=='\t')continue;if(!glyphs.containsKey(cp))out.missing.add(new String(Character.toChars(cp)));}
            if(!out.missing.isEmpty())return out;
            Random random=new Random(System.nanoTime()^text.hashCode());float originX=x,cursor=x,lineY=baseline;int previousCodePoint=-1,previousStroke=-1,pointCount=0;Glyph previousGlyph=null;boolean formulaLine=false;
            String[] lines=text.replace("\r","").split("\n",-1);
            for(String line:lines){formulaLine=formula(line);ArrayList<String> words=words(line);for(String word:words){if(word.equals(" ")){cursor+=height*(formulaLine?.62f:.48f);previousGlyph=null;previousCodePoint=-1;previousStroke=-1;continue;}
                    ArrayList<Glyph> chosen=new ArrayList<>();int[] cps=word.codePoints().toArray();float[] scales=new float[cps.length];float wordWidth=0;for(int c=0;c<cps.length;c++){ArrayList<Glyph> choices=glyphs.get(cps[c]);Glyph g=choices.get(random.nextInt(choices.size()));chosen.add(g);scales[c]=height/xHeight*(.96f+random.nextFloat()*.08f);wordWidth+=(Math.max(g.width,.12f*xHeight)*scales[c])+height*(formulaLine?.10f:.075f);}
                    if(cursor>originX&&cursor+wordWidth>originX+maxWidth){lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;}
                    for(int c=0;c<cps.length;c++){
                        int cp=cps[c];Glyph glyph=chosen.get(c);float scale=scales[c];
                        float glyphWidth=Math.max(glyph.width,.12f*xHeight);float advance=glyphWidth*scale+height*(formulaLine?.10f:.075f);if(cursor>originX&&cursor+advance>originX+maxWidth){lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;}float baselineJitter=(random.nextFloat()-.5f)*height*.045f;float shear=(random.nextFloat()-.5f)*.018f;
                        float gapStart=cursor;boolean connect=!formulaLine&&previousGlyph!=null&&Character.isLetter(previousCodePoint)&&Character.isLetter(cp)&&previousGlyph.exitReady&&glyph.entryReady;
                        int firstIndex=out.strokes.size();
                        for(int pathIndex=0;pathIndex<glyph.paths.size();pathIndex++){
                            Ink.Stroke source=glyph.paths.get(pathIndex);if(source.points.isEmpty())continue;
                            Ink.Stroke made=new Ink.Stroke();made.tool="fountain";made.color=color;made.width=Ink.clamp(source.width*scale,.75f,3.5f);made.layer="base";
                            ArrayList<Ink.Pt> rebuilt=curve(source.points,glyph,gapStart,lineY+baselineJitter,scale,shear,height);pointCount+=rebuilt.size();if(pointCount>100000){out.strokes.clear();out.missing.add("слишком много точек");return out;}
                            made.points.addAll(rebuilt);if(!made.points.isEmpty())out.strokes.add(made);
                        }
                        boolean merged=false;if(connect&&previousStroke>=0&&firstIndex<out.strokes.size()){
                            Ink.Stroke old=out.strokes.get(previousStroke),next=out.strokes.get(firstIndex);
                            if(CursiveJoin.merge(old,next,height*.38f)){out.strokes.remove(firstIndex);merged=true;}
                        }
                        if(out.strokes.size()>2000){out.strokes.clear();out.missing.add("слишком много штрихов");return out;}
                        if(out.strokes.size()>firstIndex||merged){previousStroke=out.strokes.size()-1;previousGlyph=glyph;previousCodePoint=cp;}else {previousGlyph=null;previousStroke=-1;previousCodePoint=-1;}
                        cursor+=advance;
                    }
                }
                lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;
            }
            out.strokes.removeIf(s->s.points.isEmpty());int total=0;for(Ink.Stroke s:out.strokes){total+=s.points.size();for(Ink.Pt p:s.points){out.left=Math.min(out.left,p.x);out.top=Math.min(out.top,p.y);out.right=Math.max(out.right,p.x);out.bottom=Math.max(out.bottom,p.y);}}
            if(total>100000){out.strokes.clear();out.missing.add("слишком много точек");}return out;
        }

        private static ArrayList<String> words(String line){ArrayList<String> result=new ArrayList<>();StringBuilder word=new StringBuilder();for(int i=0;i<line.length();){int cp=line.codePointAt(i);i+=Character.charCount(cp);if(Character.isWhitespace(cp)){if(word.length()>0){result.add(word.toString());word.setLength(0);}if(cp=='\t')result.add(" ");else if(!result.isEmpty()&&!result.get(result.size()-1).equals(" "))result.add(" ");}else word.appendCodePoint(cp);}if(word.length()>0)result.add(word.toString());return result;}
        private static boolean formula(String line){if(line.matches(".*[=+×÷→√^_∫≈≤≥≠²³₀₁₂₃].*"))return true;if(line.matches(".*[A-Za-z0-9][−*/-][A-Za-z0-9].*" )&&line.matches(".*(?:[0-9]|[A-Z].*[A-Z]).*"))return true;if(line.matches(".*[A-Z][a-z][A-Z].*"))return true;return line.contains(" - ")||line.contains(" − ");}
        private static ArrayList<Ink.Pt> curve(List<Ink.Pt> source,Glyph glyph,float x,float baseline,float scale,float shear,float height){
            ArrayList<Ink.Pt> smooth=new ArrayList<>(source.size());for(int i=0;i<source.size();i++){Ink.Pt p=source.get(i);float sx=p.x,sy=p.y,pressure=p.p;if(i>0&&i<source.size()-1){Ink.Pt before=source.get(i-1),after=source.get(i+1);sx=p.x*.72f+(before.x+after.x)*.14f;sy=p.y*.72f+(before.y+after.y)*.14f;pressure=p.p*.68f+(before.p+after.p)*.16f;}smooth.add(new Ink.Pt(sx,sy,pressure,p.tilt,p.angle,p.azimuth));}
            ArrayList<Ink.Pt> out=new ArrayList<>();if(smooth.size()==1){Ink.Pt p=smooth.get(0);out.add(place(p,glyph,x,baseline,scale,shear));return out;}
            float spacing=Math.max(.75f,height*.055f);for(int i=1;i<smooth.size();i++){Ink.Pt a=smooth.get(i-1),b=smooth.get(i);float length=Ink.dist(a.x,a.y,b.x,b.y);int steps=Math.max(1,Math.min(24,(int)Math.ceil(length/spacing)));if(i==1)out.add(place(a,glyph,x,baseline,scale,shear));for(int n=1;n<=steps;n++){float t=n/(float)steps;Ink.Pt p=new Ink.Pt(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t,a.tilt+(b.tilt-a.tilt)*t,a.angle+(b.angle-a.angle)*t,a.azimuth+(b.azimuth-a.azimuth)*t);out.add(place(p,glyph,x,baseline,scale,shear));}}
            return out;
        }
        private static Ink.Pt place(Ink.Pt p,Glyph g,float x,float baseline,float scale,float shear){float localY=p.y-g.baseline;float px=x+(p.x-g.minX)*scale+shear*(-localY)*scale,py=baseline+localY*scale;return new Ink.Pt(px,py,Ink.clamp(p.p,.021f,2),p.tilt,p.angle,p.azimuth);}
    }

    static Model load(MainActivity activity){
        HashMap<Integer,ArrayList<Glyph>> map=new HashMap<>();HashSet<String> seen=new HashSet<>();ArrayList<Raw> raw=new ArrayList<>();File root=new File(activity.getFilesDir(),"ai-training-corpus");File calibration=new File(root,AiCorpusStore.CALIBRATION);
        File[] folders=root.listFiles(File::isDirectory);int examined=0;long bytesRead=0;
        if(folders!=null){Arrays.sort(folders,Comparator.comparing(File::getName));for(File folder:folders){String doc=folder.getName();if(!doc.equals(AiCorpusStore.CALIBRATION)&&!AiLearningPermissions.allowed(activity.prefs,doc))continue;File[] files=folder.listFiles((dir,name)->name.matches("[a-f0-9]{32}\\.json"));if(files==null)continue;Arrays.sort(files,Comparator.comparing(File::getName));for(File file:files){if(++examined>MAX_CORPUS_FILES||bytesRead>24L*1024*1024)break;if(file.length()<2||file.length()>AiCorpusStore.MAX_ITEM)continue;try{String json=read(file);bytesRead+=json.length();JSONObject item=new JSONObject(json);if(!"folio-training-example-v1".equals(item.optString("format")))continue;String source=item.optString("source");if(!source.equals("calibration")&&!source.equals("ocr"))continue;String label=item.optString("text");if(label.codePointCount(0,label.length())!=1)continue;int cp=label.codePointAt(0);JSONArray strokes=item.optJSONArray("strokes");if(strokes==null||strokes.length()==0)continue;float base=doc.equals(AiCorpusStore.CALIBRATION)?CALIBRATION_BASELINE:Float.NaN;raw.add(new Raw(cp,strokes,base,item.optString("id",file.getName())));}catch(Exception ignored){}}
                if(examined>MAX_CORPUS_FILES||bytesRead>24L*1024*1024)break;}}
        float[] pageBases=pageBaselines(raw);
        for(Raw item:raw){String identity=item.identity+":"+item.strokes.toString();if(!seen.add(identity))continue;float baseline=Float.isFinite(item.baseline)?item.baseline:pageBases[0];add(map,item.codePoint,item.strokes,baseline);}
        // Keep older on-device samples usable if a previous app version stored them before the append-only corpus existed.
        if(activity.handwritingTraining!=null)for(HandwritingTraining.Group group:HandwritingTraining.GROUPS)for(String label:group.prompts)if(label.codePointCount(0,label.length())==1){int cp=label.codePointAt(0);if(map.containsKey(cp))continue;try{JSONObject sample=activity.handwritingTraining.info(label);if(sample!=null){JSONArray strokes=sample.optJSONArray("strokes");if(strokes!=null)add(map,cp,strokes,CALIBRATION_BASELINE);}}catch(Exception ignored){}}
        ArrayList<Float> xh=new ArrayList<>();for(Map.Entry<Integer,ArrayList<Glyph>> e:map.entrySet())if(isXHeight(e.getKey()))for(Glyph g:e.getValue()){float h=g.baseline-g.minY;if(h>2&&h<400)xh.add(h);}
        Collections.sort(xh);float height=xh.isEmpty()?0:xh.get(xh.size()/2);int variants=0;for(ArrayList<Glyph> list:map.values())variants+=list.size();return new Model(map,height,variants);
    }
    private static String read(File file)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>AiCorpusStore.MAX_ITEM)throw new IOException("oversize sample");}return out.toString("UTF-8");}}
    private static float[] pageBaselines(List<Raw> raw){ArrayList<Float> values=new ArrayList<>();for(Raw item:raw)if(isXHeight(item.codePoint))try{float bottom=Float.NEGATIVE_INFINITY;for(int i=0;i<item.strokes.length();i++){Ink.Stroke s=Ink.Stroke.parse(item.strokes.getJSONObject(i));for(Ink.Pt p:s.points)bottom=Math.max(bottom,p.y);}if(Float.isFinite(bottom))values.add(bottom);}catch(Exception ignored){}if(values.isEmpty())return new float[]{CALIBRATION_BASELINE};Collections.sort(values);return new float[]{values.get(values.size()/2)};}
    private static void add(HashMap<Integer,ArrayList<Glyph>> map,int cp,JSONArray raw,float baseline){try{ArrayList<Ink.Stroke> paths=new ArrayList<>();for(int i=0;i<raw.length();i++){Ink.Stroke s=Ink.Stroke.parse(raw.getJSONObject(i));if(!s.points.isEmpty())paths.add(s);}if(paths.isEmpty())return;Glyph glyph=new Glyph(cp,paths,baseline);if(glyph.nominalHeight<.1f)return;map.computeIfAbsent(cp,k->new ArrayList<>()).add(glyph);}catch(Exception ignored){}}
    private static boolean isXHeight(int cp){return X_HEIGHT.indexOf(cp)>=0;}
    private static String join(Collection<String> values,int max){StringBuilder s=new StringBuilder();int n=0;for(String value:values){if(n++>=max){s.append("…");break;}if(s.length()>0)s.append(' ');s.append(value);}return s.toString();}
}
