package ru.folio.notes;

import android.view.*;
import android.widget.*;

/** Creation keeps an empty title (date naming) and consent independent for each notebook. */
final class NewNotebookStudio {
    private NewNotebookStudio(){}
    static void show(MainActivity a){
        LinearLayout box=a.column();a.pad(box,8,10);
        TextView label=a.text("НАЗВАНИЕ",10,Studio.ACCENT,true);label.setLetterSpacing(.12f);box.addView(label);
        EditText name=Studio.input(a,"","Пустое поле — дата");name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(-1,a.dp(54));nameParams.topMargin=a.dp(10);box.addView(name,nameParams);
        LinearLayout row=Studio.row(a,"pen","Обучение почерку","Поиск образцов на планшете: рукописный текст может распознаваться с ошибками. При включённом облачном доступе образцы отправятся в профиль. Разрешение можно отозвать.",null);
        final boolean[] allowed={false};Studio.Toggle toggle=new Studio.Toggle(a,false,on->allowed[0]=on);toggle.setContentDescription("Разрешить обучение почерку для этой тетради");
        row.addView(toggle,new LinearLayout.LayoutParams(a.dp(50),a.dp(48)));row.setOnClickListener(v->toggle.performClick());
        LinearLayout.LayoutParams option=new LinearLayout.LayoutParams(-1,-2);option.topMargin=a.dp(18);box.addView(row,option);
        Studio.Sheet dialog=new Studio.Builder(a).setTitle("Новая тетрадь").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Создать",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{
            String title=name.getText().toString().trim();if(title.length()>120){name.setError("Не более 120 символов");return;}
            Ink.Doc doc=a.blankNotebook(title);AiLearningPermissions.set(a.prefs,doc.id,allowed[0]);a.docs.add(0,doc);a.persist(doc);dialog.dismissThen(()->{a.open(doc);if(allowed[0])AiLearningService.enableFromUser(a);});
        }));dialog.show();
    }
}
