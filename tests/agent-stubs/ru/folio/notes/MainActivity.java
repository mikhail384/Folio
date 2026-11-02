package ru.folio.notes;
import android.content.*;import android.widget.*;import android.view.*;import java.lang.reflect.*;
class MainActivity {
    public void startActivity(Intent intent){}
    SharedPreferences prefs=(SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class[]{SharedPreferences.class},(o,m,args)->{if(m.getName().equals("getBoolean"))return true;if(m.getName().equals("getString")&&args[0].equals("agent_cloud"))return "";if(m.getName().equals("getString"))return "ttttttttttttttttttttttttttttttttttttttttttt";return null;});
    Ink.Doc current=new Ink.Doc();NotesView canvas=new NotesView(this);Object intro,launch;boolean resumed=true;long revision;int saves;
    final Ui ui=new Ui();static class Ui{void post(Runnable task){task.run();}}
    boolean isDestroyed(){return false;}void saveNow(){saves++;}
    int dp(float n){return (int)n;}LinearLayout column(){return null;}TextView text(String s,float n,int c,boolean b){return null;}
    TextView pill(String s,boolean b,Runnable r){return null;}void pad(View v,int x,int y){}void toast(String s){}public Object getSystemService(String s){return null;}
}
