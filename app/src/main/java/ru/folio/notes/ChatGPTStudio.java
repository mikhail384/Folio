package ru.folio.notes;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** ChatGPT conversation surface inside Folio; requests use the user-authorized ChatGPT plan. */
final class ChatGPTStudio {
    private static final String RESPONSES="https://api.openai.com/v1/responses";
    private static final ArrayList<Message> HISTORY=new ArrayList<>();
    private static String HISTORY_SUBJECT="";
    private ChatGPTStudio(){}

    static void open(MainActivity a){new Window(a).show();}

    private static final class Message {
        final String role,text,image;
        final boolean localCanvasAction;
        Message(String role,String text,String image){this(role,text,image,false);}
        Message(String role,String text,String image,boolean localCanvasAction){this.role=role;this.text=text;this.image=image;this.localCanvasAction=localCanvasAction;}
    }

    private static final class Window {
        final MainActivity a;
        final LinearLayout root,transcript;
        final ScrollView transcriptScroll;
        final TextView accountState,modelButton,connectionHint,switchAccountButton;
        final EditText input;
        final CheckBox attachCanvas;
        final Studio.Toggle writeOnCanvas;
        Studio.Toggle handwritingOnCanvas;
        final TextView handwritingStatus;
        final Studio.Sheet sheet;
        final ArrayList<ChatGPTAuth.Model> models=new ArrayList<>();
        String selectedModel="";
        boolean sending;
        boolean handwritingLoading;
        PersonalHandwriting.Model handwritingModel;
        String canvasWriteFailure="";
        String pendingDocument="",pendingPage="";
        TextView activeReply;

        Window(MainActivity a){
            this.a=a;
            root=a.column();a.pad(root,0,2);

            LinearLayout account=a.row();account.setGravity(Gravity.CENTER_VERTICAL);
            TextView plan=a.text("ПЛАН CHATGPT",10,Studio.ACCENT,true);plan.setLetterSpacing(.08f);account.addView(plan,new LinearLayout.LayoutParams(0,-2,1));
            accountState=a.text("Проверяем подключение…",11,Studio.MUTED,false);accountState.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);account.addView(accountState);
            root.addView(account);

            switchAccountButton=Studio.button(a,"Выбрать аккаунт ChatGPT",false,this::switchAccount);switchAccountButton.setTextSize(10);LinearLayout.LayoutParams switchLp=new LinearLayout.LayoutParams(-2,-2);switchLp.topMargin=a.dp(4);root.addView(switchAccountButton,switchLp);

            LinearLayout mode=a.row();mode.setGravity(Gravity.CENTER_VERTICAL);a.pad(mode,12,9);mode.setBackground(Studio.surface(Studio.CARD,16,a.getResources().getDisplayMetrics().density));
            LinearLayout modeText=a.column();modeText.addView(a.text("Писать ответы на холст",13,Studio.TEXT,true));TextView modeHint=a.text("Выкл. — ответ здесь · вкл. — на странице. «Напиши на холсте…» срабатывает отдельно.",10,Studio.MUTED,false);modeHint.setLineSpacing(a.dp(2),1);LinearLayout.LayoutParams mh=new LinearLayout.LayoutParams(-1,-2);mh.topMargin=a.dp(3);modeText.addView(modeHint,mh);mode.addView(modeText,new LinearLayout.LayoutParams(0,-2,1));
            writeOnCanvas=new Studio.Toggle(a,a.prefs.getBoolean("chatgpt_canvas_output",false),on->{a.prefs.edit().putBoolean("chatgpt_canvas_output",on).apply();if(handwritingOnCanvas!=null){handwritingOnCanvas.setEnabled(on&&a.canvas!=null&&a.canvas.page!=null);if(on&&handwritingOnCanvas.checked)ensureHandwritingModel();}});
            writeOnCanvas.setEnabled(a.canvas!=null&&a.canvas.page!=null);mode.addView(writeOnCanvas,new LinearLayout.LayoutParams(a.dp(50),a.dp(44)));root.addView(mode);
            LinearLayout.LayoutParams mp=(LinearLayout.LayoutParams)mode.getLayoutParams();mp.topMargin=a.dp(9);mode.setLayoutParams(mp);

            LinearLayout handwriting=a.row();handwriting.setGravity(Gravity.CENTER_VERTICAL);a.pad(handwriting,12,8);handwriting.setBackground(Studio.surface(Studio.CARD,16,a.getResources().getDisplayMetrics().density));
            LinearLayout handwritingText=a.column();handwritingText.addView(a.text("Мой почерк",13,Studio.TEXT,true));handwritingStatus=a.text("Создаёт новые штрихи по сохранённым буквам",10,Studio.MUTED,false);handwritingStatus.setLineSpacing(a.dp(2),1);LinearLayout.LayoutParams hs=new LinearLayout.LayoutParams(-1,-2);hs.topMargin=a.dp(3);handwritingText.addView(handwritingStatus,hs);handwriting.addView(handwritingText,new LinearLayout.LayoutParams(0,-2,1));
            handwritingOnCanvas=new Studio.Toggle(a,a.prefs.getBoolean("chatgpt_personal_handwriting",false),on->{a.prefs.edit().putBoolean("chatgpt_personal_handwriting",on).apply();if(on)ensureHandwritingModel();else handwritingStatus.setText("Будет использован обычный текстовый инструмент");});
            handwritingOnCanvas.setEnabled(writeOnCanvas.checked&&a.canvas!=null&&a.canvas.page!=null);handwriting.addView(handwritingOnCanvas,new LinearLayout.LayoutParams(a.dp(50),a.dp(44)));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=a.dp(7);root.addView(handwriting,hp);

            modelButton=Studio.button(a,"Модель не выбрана",false,this::chooseModel);modelButton.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);modelButton.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));modelButton.setMinHeight(a.dp(42));
            LinearLayout.LayoutParams modelLp=new LinearLayout.LayoutParams(-1,-2);modelLp.topMargin=a.dp(8);root.addView(modelButton,modelLp);

            transcript=new LinearLayout(a);transcript.setOrientation(LinearLayout.VERTICAL);transcript.setPadding(a.dp(2),a.dp(8),a.dp(2),a.dp(8));
            transcriptScroll=new ScrollView(a);transcriptScroll.setFillViewport(false);transcriptScroll.setVerticalScrollBarEnabled(false);transcriptScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);transcriptScroll.addView(transcript,new ScrollView.LayoutParams(-1,-2));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,a.dp(250));tp.topMargin=a.dp(4);root.addView(transcriptScroll,tp);

            for(Message message:HISTORY)addBubble(message.role,message.text,null);
            if(HISTORY.isEmpty())addBubble("assistant","Задайте вопрос по тетради или попросите объяснить тему. Если отметить снимок холста, он будет отправлен вместе с вопросом.",null);

            input=new EditText(a);input.setTextColor(Studio.TEXT);input.setHintTextColor(Studio.MUTED);input.setHint("Напишите вопрос…");input.setTextSize(15);input.setPadding(a.dp(13),a.dp(11),a.dp(13),a.dp(11));input.setBackground(Studio.ripple(a,Studio.CARD,15));input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setSingleLine(false);input.setMinLines(2);input.setMaxLines(5);input.setGravity(Gravity.TOP|Gravity.LEFT);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12000)});
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.topMargin=a.dp(8);root.addView(input,il);

            attachCanvas=new CheckBox(a);attachCanvas.setText("Прикрепить текущий вид холста");attachCanvas.setTextColor(Studio.MUTED);attachCanvas.setTextSize(12);attachCanvas.setButtonTintList(ColorStateList.valueOf(Studio.ACCENT));attachCanvas.setChecked(false);attachCanvas.setEnabled(a.canvas!=null&&a.canvas.page!=null);root.addView(attachCanvas);
            connectionHint=a.text("Запрос отправится в OpenAI после нажатия кнопки. API-ключ не нужен.",10,Studio.MUTED,false);connectionHint.setLineSpacing(a.dp(2),1);root.addView(connectionHint);

            LinearLayout actions=a.row();actions.setGravity(Gravity.CENTER_VERTICAL);TextView connect=Studio.button(a,"Продолжить с ChatGPT",false,this::connect);connect.setTextSize(11);connect.setPadding(a.dp(10),a.dp(9),a.dp(10),a.dp(9));actions.addView(connect,new LinearLayout.LayoutParams(0,-2,1));TextView send=Studio.button(a,"Спросить",true,this::send);LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(-2,-2);sendLp.leftMargin=a.dp(8);actions.addView(send,sendLp);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2);al.topMargin=a.dp(8);root.addView(actions,al);

            int chatHeight=Math.min(760,Math.max(340,a.getResources().getConfiguration().screenHeightDp-210));
            sheet=new Studio.Builder(a).setTitle("ChatGPT в Folio").setView(root).width(Math.min(510,a.getResources().getConfiguration().screenWidthDp-28)).contentHeight(chatHeight).setPositiveButton("Готово",null).create();
        }

        void show(){sheet.show();loadConnection();if(handwritingOnCanvas.checked)ensureHandwritingModel();}

        void ensureHandwritingModel(){
            if(handwritingModel!=null){if(handwritingStatus!=null)handwritingStatus.setText(handwritingModel.summary());return;}
            if(handwritingLoading)return;handwritingLoading=true;if(handwritingStatus!=null)handwritingStatus.setText("Собираем личный профиль из ваших образцов…");
            new Thread(()->{PersonalHandwriting.Model loaded=PersonalHandwriting.load(a);a.ui.post(()->{handwritingLoading=false;handwritingModel=loaded;if(sheet.isShowing()&&handwritingStatus!=null)handwritingStatus.setText(loaded.empty()?"Пока нет образцов отдельных букв. Пройдите калибровку почерка.":loaded.summary());});},"folio-personal-handwriting").start();
        }

        void loadConnection(){
            ChatGPTAuth.Account saved=ChatGPTAuth.account(a);
            int accountCount=ChatGPTAuth.accounts(a).size();
            switchAccountButton.setVisibility(View.VISIBLE);
            switchAccountButton.setText(accountCount==0?"Выбрать аккаунт ChatGPT":accountCount==1?"Добавить второй аккаунт":"Сменить аккаунт · "+accountCount);
            if(saved==null){accountState.setText("Не подключено");modelButton.setText("Доступные модели появятся после входа");return;}
            String accountKey=saved.subject()+"|"+saved.clientId();
            if(!HISTORY_SUBJECT.isEmpty()&&!HISTORY_SUBJECT.equals(accountKey)){HISTORY.clear();transcript.removeAllViews();addBubble("assistant","Начните новый диалог для этой учётной записи.",null);}
            HISTORY_SUBJECT=accountKey;
            accountState.setText(saved.email().isEmpty()?"Подключено":"Подключено · "+saved.email());
            if(!saved.canUsePlan()){connectionHint.setText("Вход сохранён, но доступ к плану ChatGPT не выдан. Нажмите кнопку выбора аккаунта и выберите активный аккаунт, чтобы повторить запрос разрешения.");return;}
            if(!models.isEmpty())return;
            modelButton.setText("Загружаем модели…");connectionHint.setText("Используется разрешение вашего плана ChatGPT.");
            new Thread(()->{
                try{List<ChatGPTAuth.Model> found=ChatGPTAuth.models(a);a.ui.post(()->{if(sheet.isShowing()){models.clear();models.addAll(found);String preferred=a.prefs.getString("chatgpt_model","");for(ChatGPTAuth.Model m:models)if(m.slug.equals(preferred))selectedModel=m.slug;if(selectedModel.isEmpty())selectedModel=models.get(0).slug;refreshModelLabel();}});}
                catch(Exception error){a.ui.post(()->{if(sheet.isShowing()){modelButton.setText("Не удалось загрузить модели");connectionHint.setText(error.getMessage()==null?"Проверьте сетевое подключение и повторите вход.":error.getMessage());}});}
            },"folio-chatgpt-models").start();
        }

        void refreshModelLabel(){for(ChatGPTAuth.Model model:models)if(model.slug.equals(selectedModel)){modelButton.setText("Модель · "+model.name+"  ▾");return;}modelButton.setText("Выбрать модель  ▾");}
        void chooseModel(){if(models.isEmpty()){loadConnection();return;}CharSequence[] names=new CharSequence[models.size()];for(int i=0;i<models.size();i++)names[i]=models.get(i).name+" · "+models.get(i).slug;new Studio.Builder(a).setTitle("Модель ChatGPT").setItems(names,(dialog,index)->{selectedModel=models.get(index).slug;a.prefs.edit().putString("chatgpt_model",selectedModel).apply();refreshModelLabel();}).width(420).show();}

        void connect(){if(ChatGPTAuth.account(a)==null)beginSignIn(true);else switchAccount();}
        void switchAccount(){
            List<ChatGPTAuth.Account> saved=ChatGPTAuth.accounts(a);ChatGPTAuth.Account active=ChatGPTAuth.account(a);CharSequence[] choices=new CharSequence[saved.size()+1];
            for(int i=0;i<saved.size();i++){ChatGPTAuth.Account item=saved.get(i);String label=item.email().isEmpty()?"Аккаунт "+(i+1):item.email();if(item.subject().equals(active==null?"":active.subject())&&item.clientId().equals(active==null?"":active.clientId()))label+=" · активен";else if(saved.size()>1&&label.equals(saved.get(0).email()))label+=" · регистрация "+(i+1);choices[i]=label;}
            choices[saved.size()]="Добавить аккаунт…";
            new Studio.Builder(a).setTitle("Аккаунты ChatGPT").setItems(choices,(dialog,index)->{
                if(index==saved.size()){beginSignIn(true);return;}
                ChatGPTAuth.Account picked=saved.get(index);if(active!=null&&picked.subject().equals(active.subject())&&picked.clientId().equals(active.clientId())){beginSignIn(false);return;}
                if(!ChatGPTAuth.activateAccount(a,picked.subject(),picked.clientId())){connectionHint.setText("Не удалось переключить сохранённый аккаунт.");return;}
                models.clear();selectedModel="";a.prefs.edit().remove("chatgpt_model").apply();loadConnection();a.toast("Аккаунт ChatGPT переключён");
            }).width(420).show();
        }
        void beginSignIn(boolean selectDifferentAccount){
            accountState.setText(selectDifferentAccount?"Выбираем аккаунт…":"Ожидаем подтверждения входа…");
            connectionHint.setText(selectDifferentAccount?"Выберите нужный аккаунт в браузере. Сохранённые входы останутся, если вы отмените подключение. Если OpenAI сообщает, что аккаунту недоступно приложение или рабочая область, доступ должен разрешить владелец этой рабочей области.":"Подтвердите вход в браузере — после этого Folio откроется автоматически.");
            ChatGPTAuth.signIn(a,selectDifferentAccount,(account,error)->{
                if(error!=null){ChatGPTAuth.Account current=ChatGPTAuth.account(a);accountState.setText(current==null?"Не подключено":current.email().isEmpty()?"Подключено":"Подключено · "+current.email());connectionHint.setText(error.getMessage()==null?"Не удалось подключить ChatGPT.":error.getMessage());switchAccountButton.setVisibility(current==null?View.GONE:View.VISIBLE);a.toast("Не удалось подключить ChatGPT");return;}
                accountState.setText(account.email().isEmpty()?"Подключено":"Подключено · "+account.email());connectionHint.setText(account.canUsePlan()?"Вход защищённо сохранён на этом устройстве.":"Вход сохранён, но разрешение на использование плана ChatGPT не выдано. Откройте выбор аккаунта, выберите эту запись и повторите запрос разрешения.");models.clear();
                if(selectDifferentAccount){selectedModel="";a.prefs.edit().remove("chatgpt_model").apply();}
                loadConnection();a.toast(selectDifferentAccount?"Аккаунт ChatGPT изменён":"ChatGPT подключён к Folio");
            });
        }

        void send(){
            if(sending)return;String question=input.getText().toString().trim();if(question.isEmpty()){input.setError("Напишите вопрос");input.requestFocus();return;}
            CanvasWriteCommand.Parsed canvasCommand=CanvasWriteCommand.parse(question,lastAssistantText());
            if(canvasCommand!=null){
                if(canvasCommand.text.trim().isEmpty()){input.setError("Напишите текст для холста или сначала получите ответ ChatGPT");input.requestFocus();return;}
                if(a.canvas==null||a.canvas.page==null){connectionHint.setText("Откройте тетрадь Folio, чтобы записать текст на холст.");return;}
                if(!a.canvas.page.writable()){connectionHint.setText("Выберите незаблокированный слой, чтобы записать текст.");return;}
                boolean personal=a.prefs.getBoolean("chatgpt_personal_handwriting",false);
                if(personal&&(handwritingLoading||handwritingModel==null)){if(!handwritingLoading)ensureHandwritingModel();connectionHint.setText("Загружаем профиль почерка. Повторите команду через секунду.");return;}
                executeCanvasCommand(question,canvasCommand,personal);return;
            }
            if(a.prefs.getBoolean("chatgpt_canvas_output",false)&&handwritingOnCanvas.checked){if(handwritingLoading){connectionHint.setText("Профиль почерка ещё загружается. Подождите немного и отправьте запрос ещё раз.");return;}if(handwritingModel==null){ensureHandwritingModel();connectionHint.setText("Загружаем профиль почерка. Отправьте запрос ещё раз через секунду.");return;}}
            ChatGPTAuth.Account account=ChatGPTAuth.account(a);if(account==null||!account.canUsePlan()){connectionHint.setText("Сначала подключите ChatGPT и подтвердите доступ к плану.");return;}
            if(selectedModel.isEmpty()){connectionHint.setText("Подождите, пока загрузится список моделей.");loadConnection();return;}
            String image=null;if(attachCanvas.isChecked()){try{image=captureCanvas();}catch(Exception error){connectionHint.setText("Не удалось подготовить снимок: "+error.getMessage());return;}}
            final String attached=image;
            input.setText("");input.clearFocus();try{((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Exception ignored){}
            pendingDocument=a.current==null?"":a.current.id;pendingPage=a.canvas==null||a.canvas.page==null?"":a.canvas.page.id;
            HISTORY.add(new Message("user",question,attached));addBubble("user",question,attached);
            activeReply=addBubble("assistant","",null);sending=true;connectionHint.setText("Ответ формируется через Responses API…");
            ArrayList<Message> requestHistory=new ArrayList<>();int start=Math.max(0,HISTORY.size()-24);requestHistory.addAll(HISTORY.subList(start,HISTORY.size()));String model=selectedModel;
            new Thread(()->request(model,requestHistory),"folio-chatgpt-response").start();
        }

        String lastAssistantText(){for(int i=HISTORY.size()-1;i>=0;i--){Message message=HISTORY.get(i);if(!message.role.equals("assistant")||message.localCanvasAction)continue;String text=message.text.trim();if(text.isEmpty())continue;String lower=text.toLowerCase(java.util.Locale.ROOT);if(lower.contains("не могу управлять folio")||lower.contains("не могу рисовать")||lower.contains("can't control folio")||lower.contains("cannot draw on this canvas"))continue;return text;}return "";}

        void executeCanvasCommand(String question,CanvasWriteCommand.Parsed command,boolean personal){
            input.setText("");input.clearFocus();try{((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}catch(Exception ignored){}
            HISTORY.add(new Message("user",question,null));addBubble("user",question,null);
            String response;
            if(writeToCanvas(command.text,personal)){response=command.reusedPrevious?"Перенёс предыдущий ответ на холст.":"Записал текст на холст.";connectionHint.setText("Команда выполнена непосредственно в Folio.");}
            else {response=canvasWriteFailure.isEmpty()?"Не удалось записать текст на холст.":canvasWriteFailure;connectionHint.setText(response);}
            addBubble("assistant",response,null);HISTORY.add(new Message("assistant",response,null,true));
        }

        TextView addBubble(String role,String text,String image){
            LinearLayout bubble=a.column();a.pad(bubble,12,10);boolean user=role.equals("user");bubble.setBackground(Studio.surface(user?0xff383448:Studio.CARD,16,a.getResources().getDisplayMetrics().density));
            TextView who=a.text(user?"ВЫ":"CHATGPT",9,user?Studio.ACCENT:Studio.MUTED,true);who.setLetterSpacing(.08f);bubble.addView(who);
            TextView body=a.text(text,13,Studio.TEXT,false);body.setTextIsSelectable(true);body.setLineSpacing(a.dp(3),1);body.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=a.dp(5);bubble.addView(body,bp);
            if(image!=null){TextView imageTag=a.text("СНИМОК ХОЛСТА ПРИКРЕПЛЁН",9,Studio.ACCENT,true);imageTag.setLetterSpacing(.05f);LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(-1,-2);imageLp.topMargin=a.dp(6);bubble.addView(imageTag,imageLp);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=a.dp(8);if(user)lp.leftMargin=a.dp(28);else lp.rightMargin=a.dp(14);transcript.addView(bubble,lp);bubble.setTag(body);transcriptScroll.post(()->transcriptScroll.fullScroll(View.FOCUS_DOWN));return body;
        }

        void request(String model,List<Message> history){
            StringBuilder answer=new StringBuilder();boolean completed=false;HttpURLConnection con=null;
            try{
                JSONObject body=new JSONObject();body.put("model",model);body.put("instructions",a.prefs.getBoolean("chatgpt_canvas_output",false)?"You are ChatGPT inside Folio. The app can place your final answer on the currently open Folio canvas when canvas output is enabled. Never claim you cannot control Folio or write on the canvas. When the user asks for exact text to be written, respond with only that text unless they ask for an explanation; the app will perform the insertion after the answer. Do not claim the text was inserted before the app confirms it.":"You are ChatGPT inside Folio. Canvas output is currently off, but the app can write on the open Folio page when the user explicitly asks. Never claim you cannot control Folio or write on its canvas. Answer normally unless the user asks for a direct canvas action; in that case, provide only the requested text and do not claim it was inserted." );body.put("store",false);body.put("stream",true);JSONArray input=new JSONArray();
                for(Message message:history){JSONObject item=new JSONObject().put("role",message.role);if(message.image!=null&&!message.image.isEmpty()){JSONArray content=new JSONArray();content.put(new JSONObject().put("type","input_text").put("text",message.text));content.put(new JSONObject().put("type","input_image").put("image_url",message.image));item.put("content",content);}else item.put("content",message.text);input.put(item);}body.put("input",input);
                con=(HttpURLConnection)new URL(RESPONSES).openConnection();con.setRequestMethod("POST");con.setConnectTimeout(25000);con.setReadTimeout(120000);con.setDoOutput(true);con.setUseCaches(false);con.setRequestProperty("Authorization","Bearer "+ChatGPTAuth.accessToken(a));con.setRequestProperty("Content-Type","application/json; charset=utf-8");con.setRequestProperty("Accept","text/event-stream");con.setRequestProperty("Cache-Control","no-store");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);con.setFixedLengthStreamingMode(bytes.length);try(java.io.OutputStream out=con.getOutputStream()){out.write(bytes);}
                int status=con.getResponseCode();if(status<200||status>=300){String error=ChatGPTAuth.readUtf8(con.getErrorStream(),1024*1024);throw ChatGPTAuth.httpError(status,error);}
                try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){if(!line.startsWith("data:"))continue;String data=line.substring(5).trim();if(data.isEmpty()||data.equals("[DONE]"))continue;JSONObject event;try{event=new JSONObject(data);}catch(Exception ignored){continue;}String type=event.optString("type");
                    if(type.equals("response.output_text.delta")){String delta=event.optString("delta","");if(!delta.isEmpty()){answer.append(delta);publishDelta(answer.toString());}}
                    else if(type.equals("response.failed")){JSONObject response=event.optJSONObject("response"),problem=response==null?null:response.optJSONObject("error");String code=problem==null?"unknown_error":problem.optString("code","unknown_error"),detail=problem==null?"":problem.optString("message","");if(code.contains("usage_limit_exceeded")||code.contains("usage_limit"))throw new IOException("Лимит использования плана ChatGPT достигнут. Попробуйте позже.");if(code.contains("usage_unavailable")||code.contains("user_unavailable"))throw new IOException("OpenAI временно не подтвердил доступ к аккаунту или рабочей области. Попробуйте позже.");if(code.contains("subscription_sharing_user_not_eligible")||code.contains("chatpass_v2_scope_not_authorized")||code.contains("chatpass_v2_invalid_authorization_context"))throw new IOException("Выбранный аккаунт или рабочая область не разрешает запросы через план ChatGPT. Повторный вход это ограничение не снимет.");if(code.contains("subscription_sharing_invalid_user"))throw new IOException("OpenAI не подтвердил выбранную учётную запись. Проверьте активный аккаунт и разрешения.");if(code.contains("subscription_sharing_route_not_supported"))throw new IOException("Для доступа через план ChatGPT этот адрес API не поддерживается.");if(code.contains("subscription_sharing_unsupported_capability"))throw new IOException("Эта модель, функция или тип входных данных пока не поддерживается планом ChatGPT.");if(detail.length()>220)detail=detail.substring(0,220);throw new IOException("Запрос завершился с ошибкой ("+code+")"+(detail.isEmpty()?"":" · "+detail));}
                    else if(type.equals("response.incomplete")){JSONObject response=event.optJSONObject("response");String reason=response==null?"":response.optJSONObject("incomplete_details")==null?"":response.optJSONObject("incomplete_details").optString("reason","");throw new IOException("Ответ не завершён"+(reason.isEmpty()?"":" · "+reason)+".");}
                    else if(type.equals("response.completed")){completed=true;break;}
                }}
                if(!completed)throw new IOException("Поток завершился до события response.completed.");
                String result=answer.toString().trim();if(result.isEmpty())throw new IOException("ChatGPT вернул пустой ответ.");
                HISTORY.add(new Message("assistant",result,null));finishReply(result,null);
            }catch(Exception error){finishReply(answer.toString(),error);}finally{if(con!=null)con.disconnect();}
        }

        void publishDelta(String text){a.ui.post(()->{if(activeReply!=null&&sheet.isShowing()){activeReply.setText(text);transcriptScroll.post(()->transcriptScroll.fullScroll(View.FOCUS_DOWN));}});}
        void finishReply(String result,Exception error){a.ui.post(()->{
            if(error==null){connectionHint.setText("Ответ завершён.");boolean canvasMode=a.prefs.getBoolean("chatgpt_canvas_output",false);if(canvasMode){boolean samePage=a.current!=null&&a.canvas!=null&&a.canvas.page!=null&&pendingDocument.equals(a.current.id)&&pendingPage.equals(a.canvas.page.id);boolean personal=a.prefs.getBoolean("chatgpt_personal_handwriting",false);if(samePage&&writeToCanvas(result,personal)){if(activeReply!=null)activeReply.setText("Ответ записан на холст.");}else {if(activeReply!=null)activeReply.setText(result);if(samePage&&!canvasWriteFailure.isEmpty())connectionHint.setText(canvasWriteFailure);if(!samePage)a.toast("Открытая страница изменилась. Ответ оставлен в окне ChatGPT.");}}else if(activeReply!=null)activeReply.setText(result);}
            else {String partial=result==null?"":result.trim();if(activeReply!=null)activeReply.setText((partial.isEmpty()?"":partial+"\n\n")+"Не завершено: "+(error.getMessage()==null?"ошибка запроса":error.getMessage()));connectionHint.setText("Запрос не завершён. Проверьте соединение или повторите позже.");}
            sending=false;activeReply=null;
        });}

        String captureCanvas()throws Exception{
            NotesView view=a.canvas;if(view==null||view.page==null||view.getWidth()<2||view.getHeight()<2)throw new IOException("Откройте тетрадь перед прикреплением снимка.");
            float scale=Math.min(1f,1300f/Math.max(view.getWidth(),view.getHeight()));int w=Math.max(1,Math.round(view.getWidth()*scale)),h=Math.max(1,Math.round(view.getHeight()*scale));Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            try{Canvas canvas=new Canvas(bitmap);canvas.drawColor(view.page.paper);canvas.scale(scale,scale);view.draw(canvas);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,bytes);return "data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);}finally{bitmap.recycle();}
        }

        boolean writeToCanvas(String text,boolean personal){
            NotesView view=a.canvas;if(view==null||view.page==null||!view.page.writable()){a.toast("Ответ готов, но для записи откройте незаблокированный слой тетради");return false;}
            Ink.Page page=view.page;canvasWriteFailure="";float font=26,available=page.infinite?Math.max(320,view.getWidth()/Math.max(.02f,view.scale())-64):Math.max(160,page.width-48);
            float x,y;if(page.infinite){x=view.px(a.dp(28));float bottom=Float.NEGATIVE_INFINITY;for(Ink.Stroke stroke:page.strokes)if(page.visible(stroke)&&!stroke.points.isEmpty())bottom=Math.max(bottom,stroke.bounds()[3]);if(!Float.isFinite(bottom))bottom=view.py(Math.max(a.dp(170),view.getHeight()-a.dp(170)));y=bottom+font*1.5f;}
            else{x=24;float bottom=24;for(Ink.Stroke stroke:page.strokes)if(page.visible(stroke)&&!stroke.points.isEmpty())bottom=Math.max(bottom,stroke.bounds()[3]);y=bottom+font*1.2f;}
            if(personal){
                if(handwritingModel==null||handwritingLoading){canvasWriteFailure="Профиль почерка ещё загружается. Ответ оставлен в окне ChatGPT.";return false;}
                if(text.length()>4000){canvasWriteFailure="Ответ длиннее текущего лимита генератора почерка; он оставлен в окне ChatGPT.";return false;}
                PersonalHandwriting.Result generated=handwritingModel.layout(text,x,y,font,available,view.color);
                if(!generated.complete()){String missing=handwritingModel.missingFor(text);canvasWriteFailure=generated.missing.contains("слишком длинный текст")?"Ответ слишком длинный для записи личным почерком.":"Не хватает образцов символов"+(missing.isEmpty()?".":": "+missing+".");handwritingStatus.setText(canvasWriteFailure);return false;}
                if(!page.infinite&&(generated.top<8||generated.bottom>page.height-8)){canvasWriteFailure="На странице не хватило места для ответа личным почерком.";return false;}
                if(!view.addInkStrokes(generated.strokes)){canvasWriteFailure="Не удалось добавить штрихи на текущий слой.";return false;}
                if(page.infinite){float centerX=(generated.left+generated.right)/2,centerY=(generated.top+generated.bottom)/2;view.tx=view.getWidth()/2f-centerX*view.scale();view.ty=view.getHeight()/2f-centerY*view.scale();view.invalidate();}
            }else{
                if(text.length()>18000)text=text.substring(0,18000)+"…";int columns=Math.max(18,Math.min(110,(int)(available/(font*.65f))));String wrapped=wrap(text,columns);String[] lines=wrapped.split("\n",-1);if(lines.length>200){wrapped=String.join("\n",Arrays.copyOf(lines,200))+"\n…";lines=wrapped.split("\n",-1);}
                if(!page.infinite){float needed=lines.length*font*1.25f;if(y+needed>page.height-12)y=Math.max(12,page.height-needed-12);if(y+needed>page.height){a.toast("На странице не хватило места для всего ответа");return false;}}
                view.addText(wrapped,x,y,font);if(page.infinite){float centerY=y+lines.length*font*.62f;view.tx=view.getWidth()/2f-(x+Math.min(available*.45f,180))*view.scale();view.ty=view.getHeight()/2f-centerY*view.scale();view.invalidate();}
            }
            a.toast("Ответ добавлен на холст");return true;
        }

        String wrap(String text,int columns){StringBuilder result=new StringBuilder();for(String paragraph:text.replace("\r","").split("\n",-1)){String line="";for(String word:paragraph.split("(?<=\\s)")){if(word.length()>columns){if(!line.isEmpty()){appendLine(result,line);line="";}for(int p=0;p<word.length();p+=columns){int end=Math.min(word.length(),p+columns);String piece=word.substring(p,end);if(end<word.length())appendLine(result,piece);else line=piece;}}else if(!line.isEmpty()&&line.length()+word.length()>columns){appendLine(result,line);line=word;}else line+=word;}appendLine(result,line);}return result.toString();}
        void appendLine(StringBuilder result,String line){if(result.length()>0)result.append('\n');result.append(line);}
    }
}
