package ru.focusflow.pen;
import android.app.*;import android.graphics.Color;import android.widget.*;import android.view.*;
final class NativeUi {
    static LinearLayout page(Activity a,String title){LinearLayout p=new LinearLayout(a);p.setOrientation(1);p.setPadding(24,60,24,24);p.setBackgroundColor(0xff101217);a.setContentView(p);TextView h=new TextView(a);h.setText(title);h.setTextColor(Color.WHITE);h.setTextSize(23);h.setPadding(8,10,8,20);p.addView(h);return p;}
    static void text(Activity a,LinearLayout p,String text){TextView t=new TextView(a);t.setText(text);t.setTextSize(14);t.setTextColor(0xffaeb6ca);t.setPadding(8,10,8,15);p.addView(t);}
    static void button(Activity a,LinearLayout p,String text,Runnable run){Button b=new Button(a);b.setAllCaps(false);b.setText(text);b.setOnClickListener(v->run.run());p.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    static LinearLayout list(Activity a,LinearLayout p){ScrollView s=new ScrollView(a);LinearLayout list=new LinearLayout(a);list.setOrientation(1);s.addView(list);p.addView(s,new LinearLayout.LayoutParams(-1,0,1));return list;}
}
