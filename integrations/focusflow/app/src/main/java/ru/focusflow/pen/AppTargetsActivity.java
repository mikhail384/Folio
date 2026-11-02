package ru.focusflow.pen;
import android.app.*;import android.content.*;import android.content.pm.*;import android.content.res.*;import android.os.*;import android.util.*;import android.widget.*;import org.json.*;import org.xmlpull.v1.XmlPullParser;import java.util.*;

/** Available application shortcut factories, manifest shortcuts and exported activity targets. */
public final class AppTargetsActivity extends Activity {
    private static final String NS="http://schemas.android.com/apk/res/android";private static final int CREATE=20;
    private String currentPackage;
    @Override public void onCreate(Bundle b){super.onCreate(b);apps();}
    private void apps(){currentPackage=null;LinearLayout p=NativeUi.page(this,"Ярлыки действий приложений");NativeUi.text(this,p,"Выберите приложение, затем готовый ярлык или доступный экран.");EditText search=new EditText(this);search.setSingleLine();search.setTextColor(-1);search.setHintTextColor(0xff8991a8);search.setHint("Найти приложение");p.addView(search);LinearLayout list=NativeUi.list(this,p);List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);apps.sort((a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        Runnable render=()->{list.removeAllViews();Set<String> seen=new HashSet<>();String q=search.getText().toString().toLowerCase(Locale.ROOT);for(ResolveInfo r:apps){String pkg=r.activityInfo.packageName,name=r.loadLabel(getPackageManager()).toString();if(seen.add(pkg)&&(name+pkg).toLowerCase(Locale.ROOT).contains(q))NativeUi.button(this,list,name,()->targets(pkg,name));}};search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){render.run();}public void afterTextChanged(android.text.Editable e){}});render.run();
    }
    private void targets(String pkg,String name){currentPackage=pkg;LinearLayout p=NativeUi.page(this,name);NativeUi.button(this,p,"← Приложения",this::apps);NativeUi.text(this,p,"Готовые ярлыки и экраны, которые это приложение разрешает открывать. Некоторые действия требуют настройки в самом приложении.");LinearLayout list=NativeUi.list(this,p);
        List<ResolveInfo> creators=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg),0);for(ResolveInfo r:creators)NativeUi.button(this,list,"Создать ярлык · "+r.loadLabel(getPackageManager()),()->{try{startActivityForResult(new Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(new ComponentName(pkg,r.activityInfo.name)),CREATE);}catch(Exception e){Toast.makeText(this,"Это действие недоступно",0).show();}});
        try{PackageInfo pi=getPackageManager().getPackageInfo(pkg,PackageManager.GET_ACTIVITIES|PackageManager.GET_META_DATA);Resources res=getPackageManager().getResourcesForApplication(pkg);Set<Integer> parsed=new HashSet<>();if(pi.activities!=null)for(ActivityInfo info:pi.activities){
                int xml=info.metaData==null?0:info.metaData.getInt("android.app.shortcuts");if(xml!=0&&parsed.add(xml))readShortcuts(res,xml,list);
            }
            NativeUi.text(this,list,"Экраны приложения");if(pi.activities!=null)for(ActivityInfo a:pi.activities)if(a.enabled&&a.exported&&(a.permission==null||checkSelfPermission(a.permission)==PackageManager.PERMISSION_GRANTED)){String title=a.loadLabel(getPackageManager()).toString();String component=new ComponentName(pkg,a.name).flattenToString();NativeUi.button(this,list,title+"\n"+a.name.substring(a.name.lastIndexOf('.')+1),()->done("app_activity",title,component));}
        }catch(Exception e){NativeUi.text(this,list,"Список экранов недоступен.");}
    }
    private void readShortcuts(Resources res,int id,LinearLayout list){try(XmlResourceParser parser=res.getXml(id)){
        String title=null;JSONArray intents=null;
        for(int event=parser.getEventType();event!=XmlPullParser.END_DOCUMENT;event=parser.next()){
            if(event==XmlPullParser.START_TAG&&parser.getName().equals("shortcut")){
                if(!parser.getAttributeBooleanValue(NS,"enabled",true)){title=null;intents=null;continue;}
                int label=parser.getAttributeResourceValue(NS,"shortcutShortLabel",0);title=label==0?parser.getAttributeValue(NS,"shortcutId"):res.getString(label);intents=new JSONArray();
            }else if(event==XmlPullParser.START_TAG&&parser.getName().equals("intent")&&intents!=null){Intent intent=Intent.parseIntent(res,parser,Xml.asAttributeSet(parser));ResolveInfo target=getPackageManager().resolveActivity(intent,0);if(target!=null&&target.activityInfo.exported)intents.put(intent.toUri(Intent.URI_INTENT_SCHEME));}
            else if(event==XmlPullParser.END_TAG&&parser.getName().equals("shortcut")&&intents!=null&&intents.length()>0){final String label=title,value=intents.toString();NativeUi.button(this,list,"Ярлык · "+label,()->done("shortcut",label,value));title=null;intents=null;}
        }
    }catch(Exception ignored){}}
    private void done(String type,String name,String value){setResult(RESULT_OK,new Intent().putExtra("type",type).putExtra("label",name).putExtra("value",value));finish();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CREATE&&result==RESULT_OK&&data!=null){Intent target=data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);String name=data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);if(target!=null)done("shortcut",name==null?"Ярлык приложения":name,new JSONArray().put(target.toUri(Intent.URI_INTENT_SCHEME)).toString());}}
    @Override public void onBackPressed(){if(currentPackage!=null)apps();else super.onBackPressed();}
}
