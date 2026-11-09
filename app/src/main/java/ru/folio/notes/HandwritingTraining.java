package ru.folio.notes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Private, crash-safe drafts and account-bound samples for the first-run writing exercise. */
final class HandwritingTraining {
    static final String PROFILE="my-handwriting";
    static final int MAX_BYTES=8*1024*1024,MAX_POINTS=16000,MAX_STROKES=128;
    static final long MAX_TIMED_SAMPLE_MS=10*60*1000L;
    static final String MATH_OPERATORS="+−±×·⋅÷∕/=≠≈≡≅∝<≤>≥≪≫*^_%";
    static final String MATH_LOGIC="∈∉∋∌⊂⊆⊃⊇∪∩∅∧∨¬∀∃";
    static final String MATH_ANALYSIS="√∛∞π∑∏∫∬∭∮∂∇ΔδαβγθλμρσφωΩ";
    static final String MATH_SUPERSCRIPTS="⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ";
    static final String MATH_SUBSCRIPTS="₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎";
    static final String MATH_GEOMETRY="°′″∠⊥∥→←↔⇒⇔↦∴∵|‖";
    static final String MATH_SYMBOLS=MATH_OPERATORS+MATH_LOGIC+MATH_ANALYSIS+MATH_SUPERSCRIPTS+MATH_SUBSCRIPTS+MATH_GEOMETRY;
    static final class Group {
        final String title,description,kind;final String[] prompts;
        Group(String t,String d,String... p){this(t,d,false,p);}
        Group(String t,String d,boolean formula,String... p){title=t;description=d;kind=formula?"formula":"text";prompts=p;}
    }
    private static String[] chars(String s){ArrayList<String> out=new ArrayList<>();for(int i=0;i<s.length();){int cp=s.codePointAt(i);out.add(new String(Character.toChars(cp)));i+=Character.charCount(cp);}return out.toArray(new String[0]);}
    static final Group[] GROUPS={
        new Group("Связные строки","Пишите обычным почерком, соединяя буквы так, как вы привыкли.","Съешь же ещё этих мягких","французских булок и выпей чаю","В чащах юга жил бы цитрус?","Жёлтый ёж нашёл ключ.","Мы изучаем физику и химию."),
        new Group("Слова","Напишите знакомые слова своим обычным почерком.","Привет","спасибо","тетрадь","идея"),
        new Group("Предложения","Фраза сохраняется целиком, вместе с соединениями букв.","Я записываю свои мысли.","Сегодня хороший день.","Каждая идея важна."),
        new Group("Математика","Можно писать привычные вам выражения и обозначения.",true,"2 + 2 = 4","x² + y² = r²","f(x) = x² + 1"),
        new Group("Физика","Сохраните привычное написание физических формул.",true,"F = ma","E = mc²","v = s / t","U = IR"),
        new Group("Химия","Сохраните формулы и уравнения своими штрихами.",true,"H₂O","NaCl","2H₂ + O₂ → 2H₂O","CO₂ + H₂O → H₂CO₃"),
        new Group("Русские буквы","Отдельные буквы нужны, чтобы составлять новые слова.",chars("абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ")),
        new Group("Латиница","Отдельные буквы пригодятся в формулах.",chars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")),
        new Group("Цифры","Добавьте цифры для новых выражений.",chars("0123456789")),
        new Group("Пунктуация","Пишите по одному знаку; для точки достаточно лёгкого короткого касания.",chars(".,!?;:-–—()[]{}'\"«»")),
        new Group("Операции и сравнения","Пишите каждый знак отдельно, привычным движением пера.",true,chars(MATH_OPERATORS)),
        new Group("Множества и логика","Образцы кванторов, множеств и логических операций.",true,chars(MATH_LOGIC)),
        new Group("Анализ и греческие буквы","Корни, пределы, суммы, интегралы и греческие обозначения.",true,chars(MATH_ANALYSIS)),
        new Group("Степени и индексы","Верхние и нижние цифры и знаки для индексов.",true,chars(MATH_SUPERSCRIPTS+MATH_SUBSCRIPTS)),
        new Group("Геометрические знаки","Углы, параллельность, направления и геометрические символы.",true,chars(MATH_GEOMETRY)),
        new Group("Примеры формул","Пишите целиком: здесь сохраняются интервалы и характерные связки.",true,"a/b = c/d","√(x² + 1)","∫₀¹ x² dx","Σᵢ₌₁ⁿ i","α + β = γ","|x| ≤ 1","∠ABC = 90°","lim x→∞")
    };
    final File file;private JSONObject root;
    HandwritingTraining(File file){this.file=file;}

    private void ensure()throws Exception{
        if(root!=null)return;
        if(!file.exists()){root=new JSONObject().put("version",1).put("samples",new JSONObject()).put("drafts",new JSONObject());return;}
        if(file.length()>MAX_BYTES)throw new IOException("Файл образцов слишком большой");
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>MAX_BYTES)throw new IOException("Файл образцов слишком большой");}}
        JSONObject data=new JSONObject(out.toString("UTF-8"));if(data.optInt("version")!=1||data.optJSONObject("samples")==null||data.optJSONObject("drafts")==null)throw new IOException("Не удалось прочитать сохранённые образцы");
        root=data;
    }
    private void write(JSONObject candidate)throws Exception{
        byte[] data=candidate.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Достигнут размер хранилища образцов");
        File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось создать хранилище");
        File tmp=new File(parent,file.getName()+".tmp");try{
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data);out.getFD().sync();}
            try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            root=candidate;
        }finally{if(tmp.exists())tmp.delete();}
    }
    private JSONObject copy()throws Exception{ensure();return new JSONObject(root.toString());}
    private static void label(String s){if(s==null||s.isEmpty()||s.length()>32||s.matches(".*[\\r\\n\\u0000-\\u001f].*")||s.equals("__proto__")||s.equals("constructor")||s.equals("prototype"))throw new IllegalArgumentException("Недопустимая подпись образца");}
    static void validate(JSONArray strokes)throws Exception{validate(strokes,null);}
    static void validate(JSONArray strokes,String label)throws Exception{
        if(strokes==null||strokes.length()<1||strokes.length()>MAX_STROKES)throw new IllegalArgumentException("Напишите образец пером");
        int count=0;long lastTimedPoint=-1;double minX=Double.MAX_VALUE,minY=minX,maxX=-minX,maxY=-minX;
        for(int i=0;i<strokes.length();i++){JSONObject stroke=strokes.getJSONObject(i);JSONArray p=stroke.getJSONArray("points");if(p.length()<5||p.length()%5!=0)throw new IllegalArgumentException("Повреждён штрих");count+=p.length()/5;if(count>MAX_POINTS)throw new IllegalArgumentException("Образец слишком большой");
            JSONArray times=stroke.optJSONArray("pointTimesMs");if(times!=null){if(times.length()!=p.length()/5)throw new IllegalArgumentException("Повреждён темп образца");for(int t=0;t<times.length();t++){double raw=times.getDouble(t);long ms=Math.round(raw);if(!Double.isFinite(raw)||Math.abs(raw-ms)>.001||ms<0||ms>MAX_TIMED_SAMPLE_MS||ms<lastTimedPoint)throw new IllegalArgumentException("Повреждён темп образца");lastTimedPoint=ms;}}
            for(int j=0;j<p.length();j+=5){double x=p.getDouble(j),y=p.getDouble(j+1),pressure=p.getDouble(j+2),tilt=p.getDouble(j+3),roll=p.getDouble(j+4);if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>10000000||Math.abs(y)>10000000||!Double.isFinite(pressure)||!Double.isFinite(tilt)||!Double.isFinite(roll)||pressure<0||pressure>2)throw new IllegalArgumentException("Повреждены точки образца");minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        double width=maxX-minX,height=maxY-minY;if((height<1||width<.1)&&!isSmallSymbol(label))throw new IllegalArgumentException("Напишите образец чуть крупнее");
    }
    private static boolean isSmallSymbol(String label){if(label==null||label.codePointCount(0,label.length())!=1)return false;int type=Character.getType(label.codePointAt(0));return type==Character.CONNECTOR_PUNCTUATION||type==Character.DASH_PUNCTUATION||type==Character.START_PUNCTUATION||type==Character.END_PUNCTUATION||type==Character.OTHER_PUNCTUATION||type==Character.INITIAL_QUOTE_PUNCTUATION||type==Character.FINAL_QUOTE_PUNCTUATION||type==Character.MATH_SYMBOL||type==Character.MODIFIER_SYMBOL||type==Character.OTHER_SYMBOL;}
    synchronized JSONArray draft(String text)throws Exception{label(text);ensure();JSONArray d=root.getJSONObject("drafts").optJSONArray(text);return d==null?new JSONArray():new JSONArray(d.toString());}
    synchronized JSONObject info(String text)throws Exception{label(text);ensure();JSONObject item=root.getJSONObject("samples").optJSONObject(text);return item==null?null:new JSONObject(item.toString());}
    synchronized String sampleStatus(String text)throws Exception{label(text);ensure();JSONObject item=root.getJSONObject("samples").optJSONObject(text);return item==null?"":item.optString("status");}
    synchronized String sampleStatus(String text,String target)throws Exception{label(text);ensure();JSONObject item=root.getJSONObject("samples").optJSONObject(text);if(item==null)return "";String bound=item.optString("target");return item.optString("status")+(!bound.isEmpty()&&!bound.equals(target)?"_other":"");}
    synchronized void saveDraft(String text,JSONArray strokes)throws Exception{
        label(text);JSONObject c=copy(),drafts=c.getJSONObject("drafts");if(strokes==null||strokes.length()==0)drafts.remove(text);else {if(strokes.length()>MAX_STROKES)throw new IllegalArgumentException("Слишком много штрихов");drafts.put(text,new JSONArray(strokes.toString()));}write(c);
    }
    synchronized String save(String text,JSONArray strokes,String target)throws Exception{
        label(text);validate(strokes,text);if(target==null)target="";if(target.length()>512)throw new IllegalArgumentException("Некорректное подключение");
        JSONObject c=copy(),samples=c.getJSONObject("samples"),old=samples.optJSONObject(text);if(old!=null&&"pending".equals(old.optString("status"))&&!old.optString("target").isEmpty()&&!old.optString("target").equals(target))throw new IllegalArgumentException("Этот образец ожидает отправки в другом подключении");
        String id=UUID.randomUUID().toString().replace("-","");samples.put(text,new JSONObject().put("id",id).put("text",text).put("strokes",new JSONArray(strokes.toString())).put("target",target).put("status","pending"));c.getJSONObject("drafts").remove(text);write(c);return id;
    }
    synchronized JSONObject next(String target)throws Exception{
        if(target==null||target.isEmpty())return null;JSONObject c=copy(),samples=c.getJSONObject("samples");Iterator<String> names=samples.keys();
        while(names.hasNext()){String name=names.next();JSONObject s=samples.getJSONObject(name);if(!"pending".equals(s.optString("status")))continue;String bound=s.optString("target");if(!bound.isEmpty()&&!bound.equals(target))continue;if(bound.isEmpty()){s.put("target",target);write(c);}return new JSONObject(s.toString());}
        return null;
    }
    synchronized boolean acknowledge(String id)throws Exception{
        JSONObject c=copy(),samples=c.getJSONObject("samples");Iterator<String> names=samples.keys();while(names.hasNext()){JSONObject s=samples.getJSONObject(names.next());if(id.equals(s.optString("id"))&&"pending".equals(s.optString("status"))){s.put("status","synced");write(c);return true;}}return false;
    }
    synchronized int pendingOther(String target)throws Exception{ensure();int n=0;JSONObject samples=root.getJSONObject("samples");Iterator<String> names=samples.keys();while(names.hasNext()){JSONObject s=samples.getJSONObject(names.next());String bound=s.optString("target");if("pending".equals(s.optString("status"))&&!bound.isEmpty()&&!bound.equals(target))n++;}return n;}
    synchronized int movePending(String target)throws Exception{
        if(target==null||target.isEmpty()||target.length()>512)throw new IllegalArgumentException("Сначала подключите новый аккаунт");JSONObject c=copy(),samples=c.getJSONObject("samples");Iterator<String> names=samples.keys();int moved=0;
        while(names.hasNext()){JSONObject s=samples.getJSONObject(names.next());String bound=s.optString("target");if("pending".equals(s.optString("status"))&&!bound.isEmpty()&&!bound.equals(target)){s.put("target",target);s.put("id",UUID.randomUUID().toString().replace("-",""));moved++;}}
        if(moved>0)write(c);return moved;
    }
}
