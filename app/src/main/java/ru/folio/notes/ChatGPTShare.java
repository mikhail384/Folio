package ru.folio.notes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Sends a user-composed prompt through Android's official ChatGPT share target. */
final class ChatGPTShare {
    private static final String CHATGPT_PACKAGE="com.openai.chatgpt";
    private static final long PREVIEW_RETENTION_MS=7L*24*60*60*1000;

    static void compose(MainActivity a){
        final boolean pageAvailable=a.canvas!=null&&a.canvas.page!=null;
        LinearLayout box=a.column();a.pad(box,18,12);
        TextView note=a.text("Folio откроет приложение ChatGPT с подготовленным вопросом. Ответ появится в ChatGPT. Ничего не отправится, пока вы не подтвердите отправку там.",13,Studio.MUTED,false);
        note.setLineSpacing(a.dp(4),1);box.addView(note);
        EditText question=new EditText(a);
        question.setTextColor(Studio.TEXT);question.setHintTextColor(Studio.MUTED);question.setHint("Напишите вопрос…");question.setTextSize(16);
        question.setPadding(a.dp(15),a.dp(12),a.dp(15),a.dp(12));question.setBackground(Studio.ripple(a,Studio.CARD,15));
        question.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        question.setSingleLine(false);question.setMinLines(3);question.setMaxLines(7);question.setGravity(android.view.Gravity.TOP|android.view.Gravity.START);question.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8000)});
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,-2);qp.topMargin=a.dp(15);box.addView(question,qp);
        CheckBox attach=new CheckBox(a);attach.setText("Прикрепить снимок открытой страницы");attach.setTextColor(Studio.TEXT);attach.setTextSize(14);
        attach.setButtonTintList(ColorStateList.valueOf(Studio.ACCENT));attach.setChecked(false);attach.setEnabled(pageAvailable);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=a.dp(8);box.addView(attach,ap);
        if(!pageAvailable){TextView noPage=a.text("Откройте тетрадь, чтобы добавить страницу как изображение.",12,Studio.MUTED,false);box.addView(noPage);}
        Studio.Sheet dialog=new Studio.Builder(a).setTitle("Написать в ChatGPT").setView(box).width(360).setNegativeButton("Отмена",null).setPositiveButton("Открыть ChatGPT",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(-1).setOnClickListener(button->{
                String text=question.getText().toString().trim();
                if(text.isEmpty()){question.setError("Напишите вопрос");question.requestFocus();return;}
                boolean includePage=attach.isChecked()&&pageAvailable;
                Ink.Page page=includePage?snapshot(a.canvas.page):null;
                dialog.dismiss();
                if(includePage)shareWithPage(a,text,page);else openChatGPT(a,text,null);
            });
            question.requestFocus();
            if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        dialog.show();
    }

    private static Ink.Page snapshot(Ink.Page source){
        // Committed strokes are immutable; Page.copy() clones the list and metadata only.
        return source.copy();
    }

    private static void shareWithPage(MainActivity a,String text,Ink.Page page){
        final Studio.Sheet progress=Studio.busy(a,"Готовим страницу","Создаём снимок для ChatGPT…");
        try{
            a.store.io.execute(()->{
                File image=null;
                try{
                    File dir=new File(a.getCacheDir(),"chatgpt-share");
                    if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Не удалось создать временную папку");
                    cleanOldPreviews(dir);
                    final File outputFile=new File(dir,UUID.randomUUID().toString()+".png");image=outputFile;
                    try(OutputStream out=new FileOutputStream(outputFile)){a.store.exportPng(page,out);}
                    final Uri uri=Uri.parse("content://"+ChatGPTShareProvider.AUTHORITY+"/"+outputFile.getName());
                    a.runOnUiThread(()->{
                        progress.dismiss();
                        if(a.isFinishing()||a.isDestroyed()){outputFile.delete();return;}
                        openChatGPT(a,text,uri);
                    });
                }catch(Exception|OutOfMemoryError error){
                    if(image!=null)image.delete();
                    a.runOnUiThread(()->{progress.dismiss();if(!a.isFinishing()&&!a.isDestroyed())a.error("Не удалось подготовить страницу",error);});
                }
            });
        }catch(RuntimeException error){progress.dismiss();a.error("Не удалось подготовить страницу",error);}
    }

    private static void cleanOldPreviews(File directory){
        File[] files=directory.listFiles();if(files==null)return;long cutoff=System.currentTimeMillis()-PREVIEW_RETENTION_MS;
        for(File file:files)if(file.isFile()&&file.lastModified()<cutoff)file.delete();
    }

    private static void openChatGPT(MainActivity a,String text,Uri page){
        Intent send=new Intent(Intent.ACTION_SEND);send.setPackage(CHATGPT_PACKAGE);send.setType(page==null?"text/plain":"image/png");send.putExtra(Intent.EXTRA_TEXT,text);
        if(page!=null){send.putExtra(Intent.EXTRA_STREAM,page);send.setClipData(ClipData.newUri(a.getContentResolver(),"Folio page",page));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        PackageManager pm=a.getPackageManager();
        try{
            if(send.resolveActivity(pm)!=null){a.startActivity(send);a.toast("Вопрос открыт в ChatGPT — проверьте его и нажмите «Отправить»");return;}
            Intent chooser=new Intent(Intent.ACTION_SEND);chooser.setType(page==null?"text/plain":"image/png");chooser.putExtra(Intent.EXTRA_TEXT,text);
            if(page!=null){chooser.putExtra(Intent.EXTRA_STREAM,page);chooser.setClipData(ClipData.newUri(a.getContentResolver(),"Folio page",page));chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            if(chooser.resolveActivity(pm)!=null){a.startActivity(Intent.createChooser(chooser,"Выберите ChatGPT"));return;}
        }catch(RuntimeException ignored){}
        ClipboardManager clipboard=(ClipboardManager)a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Folio · ChatGPT",text));
        try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://chatgpt.com")));a.toast(page==null?"Текст скопирован — вставьте его в ChatGPT":"Текст скопирован; снимок страницы выберите в ChatGPT вручную");}
        catch(RuntimeException error){a.toast("Не удалось открыть ChatGPT. Текст скопирован.");}
    }
}
