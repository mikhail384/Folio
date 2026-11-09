package ru.folio.notes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;

public final class PersonalHandwritingTests {
    private static int checks;

    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Ink.Stroke path(float[][] points){
        Ink.Stroke result=new Ink.Stroke();result.width=4;result.tool="fountain";
        for(float[] p:points)result.points.add(new Ink.Pt(p[0],p[1],.4f,0,0));
        return result;
    }
    private static int field(Class<?> type,Object object,String name)throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.getInt(object);
    }
    private static boolean boolField(Class<?> type,Object object,String name)throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field.getBoolean(object);
    }
    private static Object glyph(ArrayList<Ink.Stroke> paths,float baseline)throws Exception{
        Class<?> type=Class.forName("ru.folio.notes.PersonalHandwriting$Glyph");
        Constructor<?> constructor=type.getDeclaredConstructor(int.class,ArrayList.class,float.class);
        constructor.setAccessible(true);return constructor.newInstance((int)'й',paths,baseline);
    }

    public static void main(String[] args)throws Exception{
        ArrayList<Ink.Stroke> bodyThenBreve=new ArrayList<>();
        bodyThenBreve.add(path(new float[][]{{10,100},{15,88},{22,100},{30,100}}));
        bodyThenBreve.add(path(new float[][]{{17,67},{20,63},{24,67}}));
        Object glyph=glyph(bodyThenBreve,100);
        Class<?> type=glyph.getClass();
        check(field(type,glyph,"entryPath")==0,"entry uses the baseline body, not the breve");
        check(field(type,glyph,"exitPath")==0,"exit uses the baseline body even when a diacritic was drawn last");
        check(boolField(type,glyph,"entryReady")&&boolField(type,glyph,"exitReady"),"multi-stroke cursive glyph remains joinable");

        ArrayList<Ink.Stroke> separateEntryAndExit=new ArrayList<>();
        separateEntryAndExit.add(path(new float[][]{{5,100},{10,92},{14,83}}));
        separateEntryAndExit.add(path(new float[][]{{24,83},{28,92},{34,100}}));
        Object split=glyph(separateEntryAndExit,100);
        check(field(type,split,"entryPath")==0&&field(type,split,"exitPath")==1,"entry and exit can belong to different body strokes");

        float learned=PersonalHandwriting.letterGapSample(116,100,5,20);
        check(Math.abs(learned-4)<.001f,"word samples learn natural inter-letter spacing");
        float tight=PersonalHandwriting.letterGapSample(80,100,5,20);
        check(Math.abs(tight+2.4f)<.001f,"tight spacing is bounded before it can fold letters over each other");
        check(Float.isNaN(PersonalHandwriting.letterGapSample(300,100,3,20)),"implausible word-width observations are ignored");
        check(Float.isNaN(PersonalHandwriting.letterGapSample(90,80,2,20)),"a two-letter word cannot destabilize spacing calibration");
        check(AgentTextCheck.exact("Увеличивается.","увеличивается"),"transcription ignores case and punctuation");
        check(!AgentTextCheck.exact("увеличивается","увюличивается"),"transcription detects е/ю substitution");
        check(AgentTextCheck.readable("Сегодня хорошая погода","Сегодня хорошая погода",99),"high-confidence matching OCR is readable");
        check(!AgentTextCheck.readable("Сегодня хорошая погода","Сегодня хорошая погода",20),"low OCR confidence requests visual review");
        check(AgentTextCheck.sizeMatches(20.9f,22f)&&!AgentTextCheck.sizeMatches(48f,22f),"word height is checked against the requested size");
        System.out.println("PASS "+checks+" personal-handwriting geometry checks");
    }
}
