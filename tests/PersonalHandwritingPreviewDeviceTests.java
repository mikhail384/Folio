package ru.folio.notes;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.FileWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Reads the installed private profile and renders a preview without touching notebook content. */
public final class PersonalHandwritingPreviewDeviceTests extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}

    @Override public void onStart(){
        Bundle result=new Bundle();int outcome=Activity.RESULT_CANCELED;
        try{
            Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            MainActivity activity=(MainActivity)startActivitySync(intent);
            for(int i=0;i<100&&!activity.ready;i++)SystemClock.sleep(100);
            if(!activity.ready)throw new IllegalStateException("Folio did not finish loading");

            PersonalHandwriting.Model model=PersonalHandwriting.load(activity);
            int localLetterSamples=0,localWordSamples=0,permittedNotebooks=0,permittedStrokes=0;
            for(HandwritingTraining.Group group:HandwritingTraining.GROUPS)for(String prompt:group.prompts){
                JSONObject sample=activity.handwritingTraining.info(prompt);
                if(sample!=null){if(prompt.codePointCount(0,prompt.length())==1)localLetterSamples++;else localWordSamples++;}
            }
            for(Ink.Doc doc:activity.docs)if(AiLearningPermissions.allowed(activity.prefs,doc.id)){
                permittedNotebooks++;for(Ink.Page page:doc.pages)for(Ink.Stroke stroke:page.strokes)if(!stroke.action.startsWith("ai:")&&!"eraser".equals(stroke.tool)&&!"text".equals(stroke.tool))permittedStrokes++;
            }
            String[] candidates={
                "Съешь ещё этих мягких французских булок, да выпей же чаю.",
                "Сегодня хорошая погода, завтра будет новый день.",
                "Спасибо, тетрадь."
            };
            String selected="";PersonalHandwriting.Result generated=null;
            for(String candidate:candidates){
                PersonalHandwriting.Result attempt=model.layout(candidate,48,108,26,1240,0xff202b3d);
                if(attempt.complete()){selected=candidate;generated=attempt;break;}
            }

            File directory=new File(activity.getExternalFilesDir(null),"qa");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create QA output folder");
            String exported=exportAllowedInk(activity,directory).getAbsolutePath();
            File report=new File(directory,"personal-handwriting-preview.txt");
            try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(report),StandardCharsets.UTF_8))){
                out.println(model.summary());
                out.println("localCalibrationLetters="+localLetterSamples+" localCalibrationWords="+localWordSamples+" permittedNotebooks="+permittedNotebooks+" permittedInkStrokes="+permittedStrokes);
                if(!exported.isEmpty())out.println("authorizedInkExport="+exported);
                out.println("xHeight="+model.xHeight+" letterGap="+model.letterGap);
                if(generated==null){out.println("No complete preview phrase; missing="+model.missingFor(candidates[0]));}
                else {out.println("Generated from fresh glyph variants: "+selected);out.println("strokeCount="+generated.strokes.size()+" bounds="+generated.left+","+generated.top+","+generated.right+","+generated.bottom);}
            }
            if(generated==null){result.putString("stream","PROFILE: "+model.summary()+"\nLOCAL CALIBRATION: "+localLetterSamples+" letters, "+localWordSamples+" words\nPERMITTED NOTEBOOKS: "+permittedNotebooks+"; ink strokes: "+permittedStrokes+"\nMISSING: "+model.missingFor(candidates[0])+(exported.isEmpty()?"":"\nEXPORTED PERMITTED INK: "+exported)+"\nREPORT: "+report.getAbsolutePath());finish(outcome,result);return;}

            Bitmap preview=Bitmap.createBitmap(3900,450,Bitmap.Config.ARGB_8888);
            try{
                Canvas canvas=new Canvas(preview);canvas.drawColor(0xfffaf9f5);canvas.save();canvas.scale(3,3);
                android.graphics.Paint rule=new android.graphics.Paint(3);rule.setColor(0xffe4e1da);rule.setStrokeWidth(.6f);canvas.drawLine(28,108,1270,108,rule);
                for(Ink.Stroke stroke:generated.strokes)InkRenderer.stroke(canvas,stroke);
                canvas.restore();
                try(FileOutputStream output=new FileOutputStream(new File(directory,"personal-handwriting-preview.png"))){preview.compress(Bitmap.CompressFormat.PNG,100,output);}
            }finally{preview.recycle();}
            result.putString("stream","PASS: private corpus loaded; generated a new vector phrase without writing to a notebook.\nPROFILE: "+model.summary()+"\nLOCAL CALIBRATION: "+localLetterSamples+" letters, "+localWordSamples+" words\nPERMITTED NOTEBOOKS: "+permittedNotebooks+"; ink strokes: "+permittedStrokes+"\nGAP: "+model.letterGap+" / xHeight "+model.xHeight+"\nTEXT: "+selected+"\nSTROKES: "+generated.strokes.size()+"\nPNG: "+new File(directory,"personal-handwriting-preview.png").getAbsolutePath());
            outcome=Activity.RESULT_OK;
        }catch(Throwable failure){StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));result.putString("stream","FAIL: "+trace);}
        finish(outcome,result);
    }

    private static File exportAllowedInk(MainActivity activity,File directory)throws Exception{
        JSONObject root=new JSONObject().put("format","folio-approved-handwriting-v1");org.json.JSONArray documents=new org.json.JSONArray();int strokesWritten=0;
        for(String id:activity.store.learningIds(activity.prefs)){
            Ink.Doc doc=activity.store.loadForLearning(id,activity.prefs);if(doc==null)continue;
            JSONObject notebook=new JSONObject().put("id",id);org.json.JSONArray pages=new org.json.JSONArray();
            for(Ink.Page page:doc.pages){org.json.JSONArray ink=new org.json.JSONArray();
                for(Ink.Stroke stroke:page.strokes){if(stroke.action.startsWith("ai:")||"eraser".equals(stroke.tool)||"text".equals(stroke.tool))continue;if(!"pen".equals(stroke.tool)&&!"fountain".equals(stroke.tool)&&!"pencil".equals(stroke.tool)&&!"brush".equals(stroke.tool))continue;ink.put(stroke.json());strokesWritten++;}
                if(ink.length()>0)pages.put(new JSONObject().put("id",page.id).put("width",page.width).put("height",page.height).put("infinite",page.infinite).put("strokes",ink));
            }
            if(pages.length()>0){notebook.put("pages",pages);documents.put(notebook);}
        }
        root.put("documents",documents).put("stroke_count",strokesWritten);
        File output=new File(directory,"allowed-handwriting-ink.json");try(FileWriter writer=new FileWriter(output,false)){writer.write(root.toString());writer.flush();}
        return output;
    }
}
