package ru.folio.notes;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.Base64;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicitly enabled, foreground-only LAN API. All document access stays on the UI thread. */
final class AgentBridge {
    static final int PORT=18765,MAX_BODY=2*1024*1024;
    final MainActivity a;final AgentCloud cloud;
    ServerSocket server;ThreadPoolExecutor workers;volatile int epoch;
    final LinkedHashMap<String,String> receipts=new LinkedHashMap<>();
    String status="Выключено";
    AgentBridge(MainActivity a){this.a=a;cloud=new AgentCloud(this);}
    String token(){String value=a.prefs.getString("agent_token","");if(value.isEmpty()){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);value=Base64.encodeToString(bytes,Base64.NO_WRAP|Base64.URL_SAFE|Base64.NO_PADDING);a.prefs.edit().putString("agent_token",value).apply();}return value;}
    boolean enabled(){return a.prefs.getBoolean("agent_enabled",false);}
    void resume(){if(!enabled()||server!=null||cloud.running)return;if(cloud.configured()){cloud.start(++epoch);return;}try{
        token();ServerSocket socket=new ServerSocket();socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));server=socket;final int generation=++epoch;
        workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
        final ThreadPoolExecutor executor=workers;Thread accept=new Thread(()->{while(!socket.isClosed())try{Socket client=socket.accept();client.setSoTimeout(5000);try{executor.execute(()->serve(client,generation));}catch(RejectedExecutionException busy){client.close();}}catch(IOException closed){break;}},"folio-agent-lan");accept.setDaemon(true);accept.start();status="Подключение включено · только открытый холст";
    }catch(IOException e){status="Не удалось открыть подключение: "+e.getClass().getSimpleName();}}
    void pause(){epoch++;cloud.stop();if(server!=null)try{server.close();}catch(IOException ignored){}server=null;if(workers!=null)workers.shutdownNow();workers=null;status=enabled()?"Ожидание открытого Folio":"Выключено";}
    String url(){if(cloud.configured())try{return cloud.profile().getString("url");}catch(Exception ignored){return AgentCloud.ORIGIN;}try{for(NetworkInterface face:Collections.list(NetworkInterface.getNetworkInterfaces()))if(face.isUp()&&!face.isLoopback())for(InetAddress ip:Collections.list(face.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()&&face.getName().startsWith("wlan"))return "http://"+ip.getHostAddress()+":"+PORT;
        for(NetworkInterface face:Collections.list(NetworkInterface.getNetworkInterfaces()))if(face.isUp()&&!face.isLoopback())for(InetAddress ip:Collections.list(face.getInetAddresses()))if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())return "http://"+ip.getHostAddress()+":"+PORT;
    }catch(SocketException ignored){}return "http://<IP планшета в Wi-Fi>:"+PORT;}
    static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int n;while((n=in.read())!=-1){if(n=='\n')break;if(n!='\r')b.write(n);if(b.size()>4096)throw new IOException("Header too long");}return b.toString("US-ASCII");}
    void serve(Socket client,int generation){int code=200;String body;try(Socket socket=client){try{
        InetAddress ip=socket.getInetAddress();if(!ip.isLoopbackAddress()&&!ip.isSiteLocalAddress()&&!ip.isLinkLocalAddress())throw new Fault(403,"Local network only");
        InputStream in=new BufferedInputStream(socket.getInputStream());String[] first=line(in).split(" ");if(first.length!=3||!first[0].equals("POST"))throw new Fault(405,"Use POST");
        HashMap<String,String> headers=new HashMap<>();for(int i=0;i<32;i++){String h=line(in);if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<1)throw new Fault(400,"Invalid header");String key=h.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.containsKey(key))throw new Fault(400,"Duplicate header");headers.put(key,h.substring(colon+1).trim());if(i==31)throw new Fault(400,"Too many headers");}
        if(headers.containsKey("origin")||headers.containsKey("transfer-encoding"))throw new Fault(403,"Browser origins and chunked bodies are not accepted");
        if(!MessageDigest.isEqual(("Bearer "+token()).getBytes(StandardCharsets.UTF_8),headers.getOrDefault("authorization","").getBytes(StandardCharsets.UTF_8)))throw new Fault(401,"Invalid Folio pairing key");
        if(!headers.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new Fault(415,"Use application/json");
        int length=Integer.parseInt(headers.getOrDefault("content-length","-1"));if(length<0||length>MAX_BODY)throw new Fault(413,"Request too large");byte[] bytes=new byte[length];int pos=0,n;while(pos<length&&(n=in.read(bytes,pos,length-pos))>0)pos+=n;if(pos!=length)throw new Fault(400,"Incomplete request");
        JSONObject request=new JSONObject(new String(bytes,StandardCharsets.UTF_8));String route=first[1];FutureTask<JSONObject> task=new FutureTask<>(()->handle(route,request,generation));a.ui.post(task);
        try{body=task.get(15,TimeUnit.SECONDS).toString();}catch(TimeoutException timeout){task.cancel(false);throw new Fault(503,"Canvas busy; read its state before retrying");}catch(ExecutionException fail){Throwable cause=fail.getCause();if(cause instanceof Fault)throw (Fault)cause;throw new Fault(400,cause.getMessage()==null?"Invalid request":cause.getMessage());}
        }catch(Fault fault){code=fault.code;body="{\"error\":"+JSONObject.quote(fault.getMessage())+",\"status\":"+code+"}";}catch(Exception error){code=400;body="{\"error\":\"Invalid request\"}";}
        byte[] data=body.getBytes(StandardCharsets.UTF_8);OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+code+" Result\r\nContent-Type: application/json; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Length: "+data.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
    }catch(IOException ignored){} }
    static final class Fault extends Exception{final int code;Fault(int code,String message){super(message);this.code=code;}}
    String revision(){return a.current.modified+":"+a.revision;}
    JSONObject state()throws Exception{
        NotesView v=a.canvas;Ink.Page p=v.page;JSONArray ops=new JSONArray();HashSet<String> seen=new HashSet<>();for(Ink.Stroke s:p.strokes)if(s.action.startsWith("ai:")&&seen.add(s.action))ops.put(s.action.split(":")[1]);
        return new JSONObject().put("protocol",1).put("document_id",a.current.id).put("page_id",p.id).put("title",a.current.title).put("revision",revision()).put("stroke_count",p.strokes.size()).put("active_layer",p.activeLayer).put("writable",p.writable()).put("viewport",new JSONArray(new double[]{v.px(0),v.py(0),v.px(v.getWidth()),v.py(v.getHeight())})).put("agent_operations",ops).put("selected_strokes",v.selection.size());
    }
    JSONObject handle(String route,JSONObject request,int generation)throws Exception {
        if(generation!=epoch||!enabled()||!a.resumed||a.isDestroyed())throw new Fault(409,"Folio connection is inactive");
        if(a.current==null||a.canvas==null||a.intro!=null||a.launch!=null||a.setupOpen)throw new Fault(409,"Open a notebook in Folio");
        NotesView v=a.canvas;Ink.Page page=v.page;
        if(route.equals("/v1/state"))return state();
        if(v.penId!=-1||v.draft!=null||v.gestureBefore!=null||v.moving)throw new Fault(409,"Finish the current pen gesture first");
        AgentNotebook notebooks=new AgentNotebook(a);
        if(route.equals("/v1/notebooks"))return notebooks.library();
        if(route.equals("/v1/notebook_pages"))return notebooks.pages(request);
        if(route.equals("/v1/notebook_scan"))return notebooks.scan(request);
        if(route.equals("/v1/notebook_samples"))return notebooks.samples(request);
        if(route.equals("/v1/snapshot")){
            if(v.getWidth()<1||v.getHeight()<1)throw new Fault(409,"Canvas has no visible size");float factor=Math.min(1,1400f/Math.max(v.getWidth(),v.getHeight()));Bitmap b=Bitmap.createBitmap(Math.max(1,Math.round(v.getWidth()*factor)),Math.max(1,Math.round(v.getHeight()*factor)),Bitmap.Config.ARGB_8888);
            try{Canvas c=new Canvas(b);c.scale(factor,factor);v.draw(c);ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);return state().put("image_png",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));}finally{b.recycle();}
        }
        if(!request.optString("document_id").equals(a.current.id)||!request.optString("page_id").equals(page.id))throw new Fault(409,"The open document/page changed; read canvas again");
        if(route.equals("/v1/selection")){
            int samplePoints=0;JSONArray strokes=new JSONArray();for(Ink.Stroke s:page.strokes)if(v.selection.contains(s)&&page.visible(s)){if(!s.cuts.isEmpty()||!s.text.isEmpty())throw new Fault(400,"Select ordinary uncut ink strokes for a handwriting sample");samplePoints+=s.points.size();if(samplePoints>16000)throw new Fault(413,"Select a smaller handwriting sample");strokes.put(s.json());}if(strokes.length()==0||strokes.length()>128)throw new Fault(400,"Select 1..128 strokes with the lasso");return state().put("strokes",strokes);
        }
        String op=request.getString("operation_id");if(!op.matches("[A-Za-z0-9_-]{8,80}"))throw new Fault(400,"Invalid operation_id");String receipt=a.current.id+":"+page.id+":"+op;
        if(route.equals("/v1/ink")){
            JSONArray raw=request.getJSONArray("strokes");String action=AgentInk.action(op,raw),existing=AgentInk.existing(page,op),known=receipts.get(receipt);
            if(existing!=null||known!=null){if(!(existing!=null?existing:known).equals(action))throw new Fault(409,"operation_id was already used for different strokes");return state().put("operation_id",op).put("already_applied",true);}
            if(!request.optString("revision").equals(revision()))throw new Fault(409,"Canvas changed; read canvas before writing");if(!page.writable())throw new Fault(409,"The active layer is hidden or locked");
            ArrayList<Ink.Stroke> strokes=AgentInk.prepare(raw,action,page.activeLayer);if(page.strokes.size()+strokes.size()>100000)throw new Fault(413,"Page stroke limit exceeded");
            v.checkpoint();page.strokes.addAll(strokes);page.order();v.indexAll();v.rebuild();v.changed();a.saveNow();receipts.put(receipt,action);while(receipts.size()>128)receipts.remove(receipts.keySet().iterator().next());return state().put("operation_id",op).put("added_strokes",strokes.size()).put("autosave","queued");
        }
        if(route.equals("/v1/undo")){
            if(!request.optString("revision").equals(revision()))throw new Fault(409,"Canvas changed; read canvas before undoing");String action=AgentInk.existing(page,op);if(action==null)return state().put("removed_strokes",0);
            for(Ink.Stroke s:page.strokes)if(s.action.equals(action)&&!page.editable(s))throw new Fault(409,"AI ink is on a locked or hidden layer");
            v.checkpoint();int count=AgentInk.remove(page,action);receipts.put(receipt,action);while(receipts.size()>128)receipts.remove(receipts.keySet().iterator().next());v.indexAll();v.rebuild();v.changed();a.saveNow();return state().put("operation_id",op).put("removed_strokes",count).put("autosave","queued");
        }throw new Fault(404,"Unknown Folio operation");
    }
    String displayStatus(){return cloud.configured()?cloud.status:status;}
    String trainingTarget(){try{if(!cloud.configured())return "";JSONObject p=AgentCloud.validate(a.prefs.getString("agent_cloud",""));return p.getString("url")+"|"+p.getString("room");}catch(Exception ignored){return "";}}
    boolean importConnection(boolean enable){
        try{ClipboardManager clip=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);if(clip==null||!clip.hasPrimaryClip()||clip.getPrimaryClip().getItemCount()==0)throw new IllegalArgumentException();CharSequence value=clip.getPrimaryClip().getItemAt(0).getText();if(value==null)throw new IllegalArgumentException();JSONObject config=AgentCloud.validate(value.toString());pause();SharedPreferences.Editor edit=a.prefs.edit().putString("agent_cloud",config.toString());if(enable)edit.putBoolean("agent_enabled",true);edit.apply();if(enabled()&&a.resumed)resume();a.toast(enabled()?"Облачное подключение сохранено":"Подключение сохранено. Включите доступ к холсту");return true;}catch(Exception error){a.toast("Скопируйте подключение на странице Folio Cloud и вставьте его целиком");return false;}
    }
    void show(){
        LinearLayout box=a.column();Studio.addCard(a,box,Studio.row(a,"pen","ChatGPT / Codex","Чтение открытого холста, рукописные записи и рисунки",null));
        LinearLayout row=Studio.row(a,"cloud","Доступ к холсту","Для разных сетей подключите облако. VPN можно оставить включённым. Доступ действует, пока Folio открыт.",null);
        TextView info=a.text(displayStatus()+"\n"+url(),14,Studio.MUTED,false);info.setTextIsSelectable(true);
        Studio.Toggle toggle=new Studio.Toggle(a,enabled(),on->{a.prefs.edit().putBoolean("agent_enabled",on).apply();if(on)resume();else pause();info.setText(displayStatus()+"\n"+url());});row.addView(toggle,new LinearLayout.LayoutParams(a.dp(50),a.dp(48)));Studio.addCard(a,box,row);a.pad(info,0,16);box.addView(info);
        info.postDelayed(new Runnable(){public void run(){if(info.isAttachedToWindow()){info.setText(displayStatus()+"\n"+url());info.postDelayed(this,1000);}}},1000);
        box.addView(a.pill("Открыть страницу подключения",true,()->a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(cloud.configured()?url():AgentCloud.ORIGIN)))));
        box.addView(a.pill("Вставить облачное подключение",false,()->{importConnection(false);info.setText(displayStatus()+"\n"+url());}));
        box.addView(a.pill("Скопировать подключение для ПК",false,()->{try{String config=cloud.configured()?cloud.profile().toString():new JSONObject().put("url",url()).put("token",token()).toString();((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Folio connection",config));a.toast("Вставьте подключение в настройку плагина на ПК");}catch(Exception ignored){}}));
        box.addView(a.pill("Новый ключ подключения",false,()->{if(cloud.configured()){a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url())));return;}pause();a.prefs.edit().remove("agent_token").apply();token();if(enabled())resume();info.setText(displayStatus()+"\n"+url());a.toast("Старое подключение отключено");}));
        TextView help=a.text("Облачное подключение работает по HTTPS из разных сетей. Общая Wi-Fi сеть и отключение VPN не нужны.\n\nВ плагине сначала прочитайте холст, затем задайте место и текст. Мои записи состоят из обычных штрихов Folio и отменяются отдельной командой.\n\nДля личного почерка напишите букву или слово, выделите лассо и попросите плагин запомнить образец.",13,Studio.MUTED,false);a.pad(help,0,16);box.addView(help);
        new Studio.Builder(a).setTitle("Folio × ИИ").setView(box).setPositiveButton("Готово",null).show();
    }
}
