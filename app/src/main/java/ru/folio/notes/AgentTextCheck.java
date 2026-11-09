package ru.folio.notes;

import java.text.Normalizer;
import java.util.Locale;

/** Small, deterministic text checks shared by Folio's on-device OCR verification. */
final class AgentTextCheck {
    private AgentTextCheck(){}

    static String normalize(String value){
        String text=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е');
        StringBuilder out=new StringBuilder(text.length());boolean space=true;
        for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isLetterOrDigit(cp)){out.appendCodePoint(cp);space=false;}
            else if(!space){out.append(' ');space=true;}
        }
        int n=out.length();if(n>0&&out.charAt(n-1)==' ')out.setLength(n-1);
        return out.toString();
    }

    static double similarity(String expected,String actual){
        String a=normalize(expected),b=normalize(actual);
        if(a.isEmpty()||b.isEmpty())return a.equals(b)?1:0;
        if(a.length()>2000||b.length()>2000)return 0;
        int[] previous=new int[b.length()+1],current=new int[b.length()+1];
        for(int j=0;j<=b.length();j++)previous[j]=j;
        for(int i=1;i<=a.length();i++){
            current[0]=i;char ca=a.charAt(i-1);
            for(int j=1;j<=b.length();j++)current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+(ca==b.charAt(j-1)?0:1));
            int[] swap=previous;previous=current;current=swap;
        }
        return Math.max(0,1-(double)previous[b.length()]/Math.max(a.length(),b.length()));
    }

    static boolean exact(String expected,String actual){String a=normalize(expected),b=normalize(actual);return !a.isEmpty()&&a.equals(b);}
    static boolean readable(String expected,String actual){return !normalize(actual).isEmpty()&&similarity(expected,actual)>=.90;}
    static boolean readable(String expected,String actual,int meanConfidence){return meanConfidence>=35&&readable(expected,actual);}
    static boolean sizeMatches(float observedHeight,float targetHeight){
        if(!Float.isFinite(observedHeight)||!Float.isFinite(targetHeight)||observedHeight<=0||targetHeight<=0)return false;
        float ratio=observedHeight/targetHeight;return ratio>=.7f&&ratio<=1.3f;
    }
}
