package ru.folio.notes;

public final class CanvasWriteCommandTests {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void text(String request,String previous,String expected,boolean reused){CanvasWriteCommand.Parsed parsed=CanvasWriteCommand.parse(request,previous);check(parsed!=null,"recognized command: "+request);check(expected.equals(parsed.text),"expected exact canvas text for: "+request+" got: "+parsed.text);check(reused==parsed.reusedPrevious,"previous-answer marker for: "+request);}
    public static void main(String[] args){
        text("Напиши на холсте привет",null,"привет",false);
        text("Напиши привет на холсте",null,"привет",false);
        text("Пожалуйста, напиши на холсте: «привет, мир!»",null,"привет, мир!",false);
        text("Добавь на холст этот текст: E = mc²",null,"E = mc²",false);
        text("На холсте в Фолио","привет","привет",true);
        text("Напиши предыдущий ответ на холст","Съешь ещё этих мягких булок.","Съешь ещё этих мягких булок.",true);
        text("Write hello on the canvas",null,"hello",false);
        text("Напиши на холсте",null,"",false);
        check(CanvasWriteCommand.parse("Что у тебя на холсте?","не писать") == null,"canvas question is not a write command");
        check(CanvasWriteCommand.parse("Напиши привет в блокнот",null) == null,"other destinations are not intercepted");
        check(CanvasWriteCommand.parse("Расскажи, как писать на холсте",null) == null,"general instructions are not intercepted");
        System.out.println("PASS "+checks+" direct canvas-write command checks");
    }
}
