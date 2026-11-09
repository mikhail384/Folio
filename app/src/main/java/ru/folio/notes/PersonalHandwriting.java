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
        final int codePoint;final JSONArray strokes;final float baseline;final String pageKey,source;
        Raw(int cp,JSONArray strokes,float baseline,String pageKey,String source){this.codePoint=cp;this.strokes=strokes;this.baseline=baseline;this.pageKey=pageKey;this.source=source;}
    }
    private static final class WordRaw {
        final String text;final JSONArray strokes;final String identity;
        WordRaw(String text,JSONArray strokes){this.text=text;this.strokes=strokes;identity=text+":"+strokes.toString();}
    }
    private static final class GapFit {
        final float gap;final int examples;
        GapFit(float gap,int examples){this.gap=gap;this.examples=examples;}
    }
    static final class TempoSample {
        final float xHeightsPerSecond;final long penUpPauseMs;
        TempoSample(float rate,long pause){xHeightsPerSecond=rate;penUpPauseMs=pause;}
    }
    private static final class TempoFit {
        final float xHeightsPerSecond;final long penUpPauseMs;final int examples;
        TempoFit(float rate,long pause,int count){xHeightsPerSecond=rate;penUpPauseMs=pause;examples=count;}
    }
    private static final class Glyph {
        final int codePoint;final ArrayList<Ink.Stroke> paths;final float minX,minY,maxX,maxY,baseline,width,nominalHeight;
        final int entryPath,exitPath;final boolean entryReady,exitReady;
        Glyph(int cp,ArrayList<Ink.Stroke> source,float baseline){
            codePoint=cp;paths=source;this.baseline=baseline;float l=Float.MAX_VALUE,t=l,r=-l,b=-l,strokeWidth=0;
            for(Ink.Stroke s:source){strokeWidth=Math.max(strokeWidth,s.width);for(Ink.Pt p:s.points){l=Math.min(l,p.x);r=Math.max(r,p.x);t=Math.min(t,p.y);b=Math.max(b,p.y);}}
            minX=l;minY=t;maxX=r;maxY=b;float nominal=Math.max(1,strokeWidth);width=Math.max(r-l,nominal*.45f);nominalHeight=Math.max(b-t,nominal*.45f);
            float baselineWindow=Math.max(6,nominalHeight*.42f);
            int entry=-1,exit=-1;float entryScore=Float.MAX_VALUE,exitScore=Float.MAX_VALUE;
            for(int i=0;i<source.size();i++){Ink.Stroke path=source.get(i);if(path.points.size()<2)continue;
                Ink.Pt start=path.points.get(0),end=path.points.get(path.points.size()-1);
                float leftDistance=start.x-l,rightDistance=r-end.x;
                if(Math.abs(start.y-baseline)<=baselineWindow&&leftDistance>=-nominal&&leftDistance<=Math.max(width*.48f,nominal)){
                    float score=Math.abs(start.y-baseline)/baselineWindow+Math.max(0,leftDistance)/Math.max(width,nominal)*.3f;
                    if(score<entryScore){entryScore=score;entry=i;}
                }
                if(Math.abs(end.y-baseline)<=baselineWindow&&rightDistance>=-nominal&&rightDistance<=Math.max(width*.48f,nominal)){
                    float score=Math.abs(end.y-baseline)/baselineWindow+Math.max(0,rightDistance)/Math.max(width,nominal)*.3f;
                    if(score<exitScore){exitScore=score;exit=i;}
                }
            }
            entryPath=entry;exitPath=exit;entryReady=entry>=0;exitReady=exit>=0;
        }
    }

    static final class Model {
        final HashMap<Integer,ArrayList<Glyph>> glyphs;
        final float xHeight;
        final float letterGap;
        final int spacingExamples;
        final int variants;
        final float xHeightsPerSecond;
        final long penUpPauseMs;
        final int tempoExamples;
        Model(HashMap<Integer,ArrayList<Glyph>> glyphs,float xHeight,float letterGap,int spacingExamples,int variants,float tempo,long pause,int tempoExamples){this.glyphs=glyphs;this.xHeight=xHeight;this.letterGap=letterGap;this.spacingExamples=spacingExamples;this.variants=variants;this.xHeightsPerSecond=tempo;this.penUpPauseMs=pause;this.tempoExamples=tempoExamples;}
        boolean empty(){return glyphs.isEmpty()||xHeight<1;}
        String summary(){
            int ru=0,ruTotal=66,latin=0,latinTotal=52,digits=0;for(int cp:glyphs.keySet()){if(cp>='А'&&cp<='я'||cp=='Ё'||cp=='ё')ru++;if(cp>='A'&&cp<='Z'||cp>='a'&&cp<='z')latin++;if(cp>='0'&&cp<='9')digits++;}
            String result="Сохранено "+variants+" вариантов · "+glyphs.size()+" символов. Русские буквы: "+ru+"/"+ruTotal+"; латиница: "+latin+"/"+latinTotal+"; цифры: "+digits+"/10."+(spacingExamples>0?" Интервалы настроены по "+spacingExamples+" вашим словам.":" Интервалы пока стандартные.");
            String missing=missingFor("абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯabcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,!?;:-–—()[]{}'\"«»"+HandwritingTraining.MATH_SYMBOLS);
            String tempo=tempoExamples>0?" Данные темпа: "+String.format(Locale.ROOT,"%.1f",xHeightsPerSecond)+" высоты строки/с; пауза "+penUpPauseMs+" мс по "+tempoExamples+" образцам. Вывод пока без анимации.":" Темп пока не откалиброван; вывод штрихов мгновенный.";
            return (missing.isEmpty()?result+" Базовый набор готов.":result+" Пока нет: "+missing+".")+tempo;
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
                    ArrayList<Glyph> chosen=new ArrayList<>();int[] cps=word.codePoints().toArray();float[] baseScales=new float[cps.length];
                    float wordScale=.99f+random.nextFloat()*.02f,wordBaseline=(random.nextFloat()-.5f)*height*.018f,wordDrift=(random.nextFloat()-.5f)*height*.014f,wordShear=(random.nextFloat()-.5f)*.014f;
                    float spacing=formulaLine?height*.10f:letterGap*height/xHeight;float wordWidth=0;
                    for(int c=0;c<cps.length;c++){ArrayList<Glyph> choices=glyphs.get(cps[c]);Glyph g=choices.get(random.nextInt(choices.size()));chosen.add(g);baseScales[c]=height/xHeight*wordScale*(.99f+random.nextFloat()*.02f);wordWidth+=Math.max(g.width,.12f*xHeight)*baseScales[c]+spacing;}
                    if(cursor>originX&&cursor+wordWidth>originX+maxWidth){lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;}
                    for(int c=0;c<cps.length;c++){
                        int cp=cps[c];Glyph glyph=chosen.get(c);float scale=baseScales[c];
                        float glyphWidth=Math.max(glyph.width,.12f*xHeight);float advance=glyphWidth*scale+spacing;if(cursor>originX&&cursor+advance>originX+maxWidth){lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;}float along=cps.length<=1?0:c/(float)(cps.length-1);float baselineJitter=wordBaseline+wordDrift*along+(random.nextFloat()-.5f)*height*.004f;float shear=wordShear+(random.nextFloat()-.5f)*.003f;
                        float gapStart=cursor;boolean connect=!formulaLine&&previousGlyph!=null&&Character.isLetter(previousCodePoint)&&Character.isLetter(cp)&&previousGlyph.exitReady&&glyph.entryReady;
                        int firstIndex=out.strokes.size();
                        for(int pathIndex=0;pathIndex<glyph.paths.size();pathIndex++){
                            Ink.Stroke source=glyph.paths.get(pathIndex);if(source.points.isEmpty())continue;
                            Ink.Stroke made=new Ink.Stroke();made.tool="fountain";made.color=color;made.width=Ink.clamp(source.width*scale,.75f,3.5f);made.layer="base";
                            ArrayList<Ink.Pt> rebuilt=curve(source.points,glyph,gapStart,lineY+baselineJitter,scale,shear,height);pointCount+=rebuilt.size();if(pointCount>100000){out.strokes.clear();out.missing.add("слишком много точек");return out;}
                            made.points.addAll(rebuilt);if(!made.points.isEmpty())out.strokes.add(made);
                        }
                        boolean merged=false;int currentEntry=glyph.entryPath<0?-1:firstIndex+glyph.entryPath;
                        if(connect&&previousStroke>=0&&currentEntry>=firstIndex&&currentEntry<out.strokes.size()){
                            Ink.Stroke old=out.strokes.get(previousStroke),next=out.strokes.get(currentEntry);
                            if(CursiveJoin.merge(old,next,height*.38f)){out.strokes.remove(currentEntry);merged=true;}
                        }
                        if(out.strokes.size()>2000){out.strokes.clear();out.missing.add("слишком много штрихов");return out;}
                        int currentExit=glyph.exitPath<0?-1:firstIndex+glyph.exitPath-(merged&&glyph.entryPath<glyph.exitPath?1:0);
                        int usableExit=merged&&glyph.entryPath==glyph.exitPath?previousStroke:currentExit;
                        if(usableExit>=0&&usableExit<out.strokes.size()){previousStroke=usableExit;previousGlyph=glyph;previousCodePoint=cp;}else {previousGlyph=null;previousStroke=-1;previousCodePoint=-1;}
                        cursor+=advance;
                    }
                }
                lineY+=height*1.62f;cursor=originX;previousGlyph=null;previousCodePoint=-1;previousStroke=-1;
            }
            out.strokes.removeIf(s->s.points.isEmpty());int total=0;for(Ink.Stroke s:out.strokes){total+=s.points.size();for(Ink.Pt p:s.points){out.left=Math.min(out.left,p.x);out.top=Math.min(out.top,p.y);out.right=Math.max(out.right,p.x);out.bottom=Math.max(out.bottom,p.y);}}
            if(total>100000){out.strokes.clear();out.missing.add("слишком много точек");}return out;
        }

        private static ArrayList<String> words(String line){ArrayList<String> result=new ArrayList<>();StringBuilder word=new StringBuilder();for(int i=0;i<line.length();){int cp=line.codePointAt(i);i+=Character.charCount(cp);if(Character.isWhitespace(cp)){if(word.length()>0){result.add(word.toString());word.setLength(0);}if(cp=='\t')result.add(" ");else if(!result.isEmpty()&&!result.get(result.size()-1).equals(" "))result.add(" ");}else word.appendCodePoint(cp);}if(word.length()>0)result.add(word.toString());return result;}
        private static boolean formula(String line){if(line.codePoints().anyMatch(cp->HandwritingTraining.MATH_SYMBOLS.indexOf(cp)>=0))return true;if(line.matches(".*[A-Za-z0-9][−*/-][A-Za-z0-9].*" )&&line.matches(".*(?:[0-9]|[A-Z].*[A-Z]).*"))return true;if(line.matches(".*[A-Z][a-z][A-Z].*"))return true;return line.contains(" - ")||line.contains(" − ");}
        private static ArrayList<Ink.Pt> curve(List<Ink.Pt> source,Glyph glyph,float x,float baseline,float scale,float shear,float height){
            ArrayList<Ink.Pt> smooth=new ArrayList<>(source.size());for(int i=0;i<source.size();i++){Ink.Pt p=source.get(i);float sx=p.x,sy=p.y,pressure=p.p;if(i>0&&i<source.size()-1){Ink.Pt before=source.get(i-1),after=source.get(i+1);sx=p.x*.72f+(before.x+after.x)*.14f;sy=p.y*.72f+(before.y+after.y)*.14f;pressure=p.p*.68f+(before.p+after.p)*.16f;}smooth.add(new Ink.Pt(sx,sy,pressure,p.tilt,p.angle,p.azimuth));}
            ArrayList<Ink.Pt> out=new ArrayList<>();if(smooth.size()==1){Ink.Pt p=smooth.get(0);out.add(place(p,glyph,x,baseline,scale,shear));return out;}
            float spacing=Math.max(.75f,height*.055f);for(int i=1;i<smooth.size();i++){Ink.Pt a=smooth.get(i-1),b=smooth.get(i);float length=Ink.dist(a.x,a.y,b.x,b.y);int steps=Math.max(1,Math.min(24,(int)Math.ceil(length/spacing)));if(i==1)out.add(place(a,glyph,x,baseline,scale,shear));for(int n=1;n<=steps;n++){float t=n/(float)steps;Ink.Pt p=new Ink.Pt(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t,a.tilt+(b.tilt-a.tilt)*t,a.angle+(b.angle-a.angle)*t,a.azimuth+(b.azimuth-a.azimuth)*t);out.add(place(p,glyph,x,baseline,scale,shear));}}
            return out;
        }
        private static Ink.Pt place(Ink.Pt p,Glyph g,float x,float baseline,float scale,float shear){float localY=p.y-g.baseline;float px=x+(p.x-g.minX)*scale+shear*(-localY)*scale,py=baseline+localY*scale;return new Ink.Pt(px,py,Ink.clamp(p.p,.021f,2),p.tilt,p.angle,p.azimuth);}
    }

    static Model load(MainActivity activity){
        HashMap<Integer,ArrayList<Glyph>> map=new HashMap<>();HashSet<String> seen=new HashSet<>();HashSet<String> seenWords=new HashSet<>(),seenTimed=new HashSet<>();ArrayList<Raw> raw=new ArrayList<>();ArrayList<WordRaw> words=new ArrayList<>();ArrayList<JSONArray> timedExamples=new ArrayList<>();File root=new File(activity.getFilesDir(),"ai-training-corpus");
        File[] folders=root.listFiles(File::isDirectory);int examined=0;long bytesRead=0;
        if(folders!=null){Arrays.sort(folders,Comparator.comparing(File::getName));for(File folder:folders){String doc=folder.getName();if(!doc.equals(AiCorpusStore.CALIBRATION)&&!AiLearningPermissions.allowed(activity.prefs,doc))continue;File[] files=folder.listFiles((dir,name)->name.matches("[a-f0-9]{32}\\.json"));if(files==null)continue;Arrays.sort(files,Comparator.comparing(File::getName));for(File file:files){if(++examined>MAX_CORPUS_FILES||bytesRead>24L*1024*1024)break;if(file.length()<2||file.length()>AiCorpusStore.MAX_ITEM)continue;try{String json=read(file);bytesRead+=json.length();JSONObject item=new JSONObject(json);if(!"folio-training-example-v1".equals(item.optString("format")))continue;String source=item.optString("source");if(!source.equals("calibration")&&!source.equals("ocr"))continue;String label=item.optString("text");int count=label.codePointCount(0,label.length());JSONArray strokes=item.optJSONArray("strokes");if(strokes==null||strokes.length()==0)continue;
                    if(count<=32&&(doc.equals(AiCorpusStore.CALIBRATION)||source.equals("ocr")))addTimed(timedExamples,seenTimed,label,strokes);
                    if(count==1){int cp=label.codePointAt(0);float base=doc.equals(AiCorpusStore.CALIBRATION)?CALIBRATION_BASELINE:Float.NaN;String pageKey=item.optString("document_id",doc)+":"+item.optString("page_id","");raw.add(new Raw(cp,strokes,base,pageKey,source));}
                    else if(count<=32&&(doc.equals(AiCorpusStore.CALIBRATION)||source.equals("ocr"))&&"text".equals(item.optString("kind","text")))addWord(words,seenWords,label,strokes);
                }catch(Exception ignored){}}
                if(examined>MAX_CORPUS_FILES||bytesRead>24L*1024*1024)break;}}
        Map<String,ArrayList<Float>> pageBases=pageBaselines(raw);
        // Reviewed calibration wins over unreviewed OCR for the same character.
        for(Raw item:raw)if(item.source.equals("calibration")){String identity=item.codePoint+":"+item.strokes.toString();if(!seen.add(identity))continue;float baseline=Float.isFinite(item.baseline)?item.baseline:pageBaseline(item,pageBases);add(map,item.codePoint,item.strokes,baseline);}
        HashSet<Integer> calibrated=new HashSet<>(map.keySet());
        for(Raw item:raw)if(item.source.equals("ocr")&&!calibrated.contains(item.codePoint)){String identity=item.codePoint+":"+item.strokes.toString();if(!seen.add(identity))continue;add(map,item.codePoint,item.strokes,pageBaseline(item,pageBases));}
        // Keep older on-device samples usable if a previous app version stored them before the append-only corpus existed.
        if(activity.handwritingTraining!=null)for(HandwritingTraining.Group group:HandwritingTraining.GROUPS)for(String label:group.prompts){int count=label.codePointCount(0,label.length());try{JSONObject sample=activity.handwritingTraining.info(label);if(sample==null)continue;JSONArray strokes=sample.optJSONArray("strokes");if(strokes==null||strokes.length()==0)continue;addTimed(timedExamples,seenTimed,label,strokes);if(count==1){int cp=label.codePointAt(0);if(!map.containsKey(cp))add(map,cp,strokes,CALIBRATION_BASELINE);}else addWord(words,seenWords,label,strokes);}catch(Exception ignored){}}
        ArrayList<Float> xh=new ArrayList<>();for(Map.Entry<Integer,ArrayList<Glyph>> e:map.entrySet())if(isXHeight(e.getKey()))for(Glyph g:e.getValue()){float h=g.baseline-g.minY;if(h>2&&h<400)xh.add(h);}
        Collections.sort(xh);float height=xh.isEmpty()?0:xh.get(xh.size()/2);GapFit spacing=learnLetterGap(map,height,words);int variants=0;for(ArrayList<Glyph> list:map.values())variants+=list.size();TempoFit tempo=learnTempo(timedExamples,height);return new Model(map,height,spacing.gap,spacing.examples,variants,tempo.xHeightsPerSecond,tempo.penUpPauseMs,tempo.examples);
    }
    private static void addTimed(List<JSONArray> out,Set<String> seen,String label,JSONArray strokes){String id=label+":"+strokes.toString();if(seen.add(id))out.add(strokes);}
    static TempoSample tempoSample(JSONArray strokes,float xHeight)throws Exception{
        if(strokes==null||strokes.length()==0||!Float.isFinite(xHeight)||xHeight<1)return null;
        double distance=0;long activeMs=0,previousLast=-1;ArrayList<Long> pauses=new ArrayList<>();
        for(int i=0;i<strokes.length();i++){
            JSONObject stroke=strokes.getJSONObject(i);JSONArray points=stroke.optJSONArray("points"),times=stroke.optJSONArray("pointTimesMs");
            if(points==null||points.length()<10||points.length()%5!=0||times==null||times.length()!=points.length()/5)return null;
            long first=times.getLong(0),last=first;
            for(int p=1;p<times.length();p++){long time=times.getLong(p);if(time<last||time<0||time>HandwritingTraining.MAX_TIMED_SAMPLE_MS)return null;last=time;}
            if(previousLast>=0){long pause=first-previousLast;if(pause<0)return null;if(pause<=1500)pauses.add(pause);}
            previousLast=last;activeMs+=last-first;
            for(int p=5;p<points.length();p+=5)distance+=Ink.dist((float)points.getDouble(p-5),(float)points.getDouble(p-4),(float)points.getDouble(p),(float)points.getDouble(p+1));
        }
        if(activeMs<10||distance<xHeight*.1)return null;
        double rate=distance/xHeight/(activeMs/1000.0);if(!Double.isFinite(rate)||rate<.1||rate>50)return null;
        Collections.sort(pauses);long pause=pauses.isEmpty()?90:pauses.get(pauses.size()/2);
        return new TempoSample((float)rate,pause);
    }
    private static TempoFit learnTempo(List<JSONArray> examples,float xHeight){
        ArrayList<Float> rates=new ArrayList<>();ArrayList<Long> pauses=new ArrayList<>();for(JSONArray strokes:examples)try{TempoSample sample=tempoSample(strokes,xHeight);if(sample!=null){rates.add(sample.xHeightsPerSecond);pauses.add(sample.penUpPauseMs);}}catch(Exception ignored){}
        if(rates.isEmpty())return new TempoFit(0,90,0);Collections.sort(rates);Collections.sort(pauses);return new TempoFit(rates.get(rates.size()/2),pauses.get(pauses.size()/2),rates.size());
    }
    private static String read(File file)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>AiCorpusStore.MAX_ITEM)throw new IOException("oversize sample");}return out.toString("UTF-8");}}
    private static Map<String,ArrayList<Float>> pageBaselines(List<Raw> raw){
        HashMap<String,ArrayList<Float>> observed=new HashMap<>();
        for(Raw item:raw)if(isXHeight(item.codePoint)&&!Float.isFinite(item.baseline))try{float[] box=bounds(item.strokes);if(Float.isFinite(box[3])&&box[3]-box[1]>1)observed.computeIfAbsent(item.pageKey,k->new ArrayList<>()).add(box[3]);}catch(Exception ignored){}
        HashMap<String,ArrayList<Float>> result=new HashMap<>();
        for(Map.Entry<String,ArrayList<Float>> entry:observed.entrySet()){
            ArrayList<Float> values=entry.getValue();Collections.sort(values);ArrayList<Float> lines=new ArrayList<>();
            // OCR glyphs can come from multiple handwritten lines on one page. Cluster their
            // baselines instead of averaging absolute y-coordinates across unrelated pages.
            ArrayList<ArrayList<Float>> clusters=new ArrayList<>();ArrayList<Float> cluster=null;float previous=Float.NaN;
            for(float value:values){if(cluster==null||value-previous>12f){cluster=new ArrayList<>();clusters.add(cluster);}cluster.add(value);previous=value;}
            for(ArrayList<Float> row:clusters){Collections.sort(row);lines.add(row.get(row.size()/2));}
            result.put(entry.getKey(),lines);
        }
        return result;
    }
    private static float pageBaseline(Raw item,Map<String,ArrayList<Float>> pageBases){
        float[] box=bounds(item.strokes);float bottom=Float.isFinite(box[3])?box[3]:CALIBRATION_BASELINE;
        ArrayList<Float> candidates=pageBases.get(item.pageKey);if(candidates==null||candidates.isEmpty())return bottom;
        float nearest=candidates.get(0),distance=Math.abs(bottom-nearest);for(int i=1;i<candidates.size();i++){float d=Math.abs(bottom-candidates.get(i));if(d<distance){distance=d;nearest=candidates.get(i);}}
        return nearest;
    }
    private static float[] bounds(JSONArray raw){
        float l=Float.POSITIVE_INFINITY,t=Float.POSITIVE_INFINITY,r=Float.NEGATIVE_INFINITY,b=Float.NEGATIVE_INFINITY;
        for(int i=0;i<raw.length();i++)try{Ink.Stroke s=Ink.Stroke.parse(raw.getJSONObject(i));for(Ink.Pt p:s.points){l=Math.min(l,p.x);t=Math.min(t,p.y);r=Math.max(r,p.x);b=Math.max(b,p.y);}}catch(Exception ignored){}
        return new float[]{l,t,r,b};
    }
    private static void addWord(List<WordRaw> words,Set<String> seen,String text,JSONArray strokes){if(strokes==null||strokes.length()==0)return;WordRaw sample=new WordRaw(text,strokes);if(seen.add(sample.identity))words.add(sample);}
    private static GapFit learnLetterGap(HashMap<Integer,ArrayList<Glyph>> glyphs,float xHeight,List<WordRaw> words){
        if(xHeight<1||words.isEmpty())return new GapFit(xHeight*.075f,0);ArrayList<Float> estimates=new ArrayList<>();float fallbackWidth=xHeight*.55f;
        if(glyphs.size()>=3){ArrayList<Float> widths=new ArrayList<>();for(ArrayList<Glyph> variants:glyphs.values()){ArrayList<Float> perCharacter=new ArrayList<>();for(Glyph glyph:variants)perCharacter.add(glyph.width);Collections.sort(perCharacter);if(!perCharacter.isEmpty())widths.add(perCharacter.get(perCharacter.size()/2));}Collections.sort(widths);if(!widths.isEmpty())fallbackWidth=widths.get(widths.size()/2);}
        for(WordRaw sample:words){int[] cps=sample.text.codePoints().toArray();if(cps.length<3||cps.length>24)continue;float predicted=0;boolean complete=true;
            for(int cp:cps){if(!Character.isLetter(cp)){complete=false;break;}ArrayList<Glyph> variants=glyphs.get(cp);if(variants==null||variants.isEmpty())predicted+=fallbackWidth;else {ArrayList<Float> widths=new ArrayList<>();for(Glyph glyph:variants)widths.add(glyph.width);Collections.sort(widths);predicted+=widths.get(widths.size()/2);}}
            if(!complete)continue;float[] box=bounds(sample.strokes);float actual=box[2]-box[0];if(!Float.isFinite(actual)||actual<1)continue;
            float gap=letterGapSample(actual,predicted,cps.length,xHeight);if(Float.isFinite(gap))estimates.add(gap);
        }
        if(estimates.size()<2)return new GapFit(xHeight*.075f,0);Collections.sort(estimates);return new GapFit(estimates.get(estimates.size()/2),estimates.size());
    }
    static float letterGapSample(float actualWidth,float predictedGlyphWidth,int letters,float xHeight){
        if(letters<3||!Float.isFinite(actualWidth)||!Float.isFinite(predictedGlyphWidth)||!Float.isFinite(xHeight)||xHeight<1)return Float.NaN;
        float gap=(actualWidth-predictedGlyphWidth)/(letters-1);if(!Float.isFinite(gap)||gap < -xHeight*.35f||gap > xHeight*.35f)return Float.NaN;
        return Ink.clamp(gap,-xHeight*.12f,xHeight*.22f);
    }
    private static void add(HashMap<Integer,ArrayList<Glyph>> map,int cp,JSONArray raw,float baseline){try{ArrayList<Ink.Stroke> paths=new ArrayList<>();for(int i=0;i<raw.length();i++){Ink.Stroke s=Ink.Stroke.parse(raw.getJSONObject(i));if(!s.points.isEmpty())paths.add(s);}if(paths.isEmpty())return;Glyph glyph=new Glyph(cp,paths,baseline);if(glyph.nominalHeight<.1f)return;map.computeIfAbsent(cp,k->new ArrayList<>()).add(glyph);}catch(Exception ignored){}}
    private static boolean isXHeight(int cp){return X_HEIGHT.indexOf(cp)>=0;}
    private static String join(Collection<String> values,int max){StringBuilder s=new StringBuilder();int n=0;for(String value:values){if(n++>=max){s.append("…");break;}if(s.length()>0)s.append(' ');s.append(value);}return s.toString();}
}
