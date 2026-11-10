package ru.folio.notes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes explicit requests to put text on Folio's currently open canvas. */
final class CanvasWriteCommand {
    static final class Parsed {
        final String text;
        final boolean reusedPrevious;
        Parsed(String text,boolean reusedPrevious){this.text=text;this.reusedPrevious=reusedPrevious;}
    }

    private static final Pattern CANVAS=Pattern.compile("(?iu)\\b(?:на\\s+(?:(?:этом|текущем)\\s+)?холст(?:е|а)?|на\\s+страниц(?:е|у)|на\\s+лист(?:е|а)?|on(?:to)?\\s+(?:the\\s+)?canvas|in\\s+folio)\\b");
    private static final Pattern ACTION=Pattern.compile("(?iu)^\\s*(?:пожалуйста[,!]?\\s*)?(?:напиши(?:те)?|запиши(?:те)?|добавь(?:те)?|перенеси(?:те)?|скопируй(?:те)?|выведи(?:те)?|write|put|add|copy|move|place)\\b\\s*");
    private static final Pattern FOLIO_ONLY=Pattern.compile("(?iu)^\\s*(?:в\\s+фолио|in\\s+folio)\\s*$");
    private static final Pattern STARTS_WITH_CANVAS=Pattern.compile("(?iu)^\\s*(?:на\\s+(?:(?:этом|текущем)\\s+)?холст(?:е|а)?|на\\s+страниц(?:е|у)|на\\s+лист(?:е|а)?|on(?:to)?\\s+(?:the\\s+)?canvas|in\\s+folio)\\b");

    private CanvasWriteCommand(){}

    static Parsed parse(String request,String previousAssistant){
        if(request==null)return null;
        Matcher anchor=CANVAS.matcher(request);
        if(!anchor.find())return null;
        boolean action=ACTION.matcher(request).find();
        boolean startsWithCanvas=STARTS_WITH_CANVAS.matcher(request).find();
        if(!action&&!startsWithCanvas)return null;

        StringBuilder cleaned=new StringBuilder(request);
        cleaned.delete(anchor.start(),anchor.end());
        Matcher actionAfterRemoval=ACTION.matcher(cleaned);
        if(actionAfterRemoval.find())cleaned.delete(actionAfterRemoval.start(),actionAfterRemoval.end());
        String text=cleaned.toString().trim();
        text=FOLIO_ONLY.matcher(text).replaceAll("").trim();
        text=text.replaceFirst("(?iu)^\\s*(?::|：|[-—])\\s*","").trim();
        text=text.replaceFirst("(?iu)^\\s*(?:(?:этот|эту|такой)\\s+)?(?:текст|фразу|слово|надпись)\\s*[:：]?\\s*","").trim();
        if(isPreviousAnswerCue(text))text="";
        text=unwrap(text);
        if(text.isEmpty()){
            String previous=previousAssistant==null?"":previousAssistant.trim();
            return new Parsed(previous,!previous.isEmpty());
        }
        return new Parsed(text,false);
    }

    private static boolean isPreviousAnswerCue(String text){
        return text.matches("(?iu)(?:это|его|её|ее|ответ|предыдущий\\s+ответ|последний\\s+ответ|то\\s+же(?:\\s+самое)?|туда|всё\\s+это|все\\s+это)[.!?…]*");
    }

    private static String unwrap(String text){
        if(text.length()<2)return text;
        char first=text.charAt(0),last=text.charAt(text.length()-1);
        if((first=='«'&&last=='»')||(first=='“'&&last=='”')||(first=='\"'&&last=='\"')||(first=='\''&&last=='\''))return text.substring(1,text.length()-1).trim();
        return text;
    }
}
