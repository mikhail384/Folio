package ru.folio.notes;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Laser controls preview the actual transient renderer rather than an ink sample. */
final class LaserInspector {
    static Studio.Sheet show(MainActivity a){
        LinearLayout box=a.column();Preview preview=new Preview(a);box.addView(preview,new LinearLayout.LayoutParams(-1,a.dp(76)));CompactInspector.gap(a,box,10);
        LinearLayout modes=a.row();Runnable[] fill=new Runnable[1];fill[0]=()->{modes.removeAllViews();for(boolean trail:new boolean[]{false,true})CompactInspector.add(a,modes,CompactInspector.chip(a,trail?"Светящийся след":"Лазерная точка",a.canvas.laser.trail==trail,()->{a.canvas.laser.mode(trail);a.savePenPrefs();preview.invalidate();fill[0].run();}),32);};fill[0].run();box.addView(modes);CompactInspector.gap(a,box,12);
        LinearLayout labels=a.row();labels.addView(a.text("Размер точки",12,Studio.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));TextView value=a.text("",12,Studio.ACCENT,true);labels.addView(value);box.addView(labels);
        Studio.Slider slider=new Studio.Slider(a,2,18,Ink.clamp(a.canvas.width,2,18),n->{a.canvas.width=n;a.canvas.laser.diameter=n;value.setText(String.format(Locale.ROOT,"%.1f",n));a.toolChanged();preview.invalidate();});slider.unit="dp";value.setText(String.format(Locale.ROOT,"%.1f",slider.value));box.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(35)));
        LinearLayout colors=a.row();Runnable[] palette=new Runnable[1];palette[0]=()->{colors.removeAllViews();for(int color:new int[]{LaserEffect.DEFAULT_COLOR,0xffff824a,0xffad68ff,0xff399fff,0xff35c49b,0xffffc44c}){View dot=new ToolStudio.ColorDot(a,color,a.canvas.laser.color==color);dot.setContentDescription(String.format(Locale.ROOT,"Цвет лазера #%06X",color&0xffffff));dot.setOnClickListener(v->{a.canvas.laser.color=color;a.toolChanged();preview.invalidate();palette[0].run();});CompactInspector.add(a,colors,dot,34);}TextView custom=CompactInspector.chip(a,"+",false,()->a.colorDialog(a.canvas.laser.color,color->{a.canvas.laser.color=color;a.toolChanged();preview.invalidate();palette[0].run();}));custom.setContentDescription("Свой цвет лазера");CompactInspector.add(a,colors,custom,32);};palette[0].run();box.addView(colors);
        LinearLayout pulse=a.row();pulse.addView(a.text("Мягкая пульсация",12,Studio.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));Studio.Toggle toggle=new Studio.Toggle(a,a.canvas.laser.pulse,on->{a.canvas.laser.pulse=on;a.savePenPrefs();preview.invalidate();});toggle.setContentDescription("Пульсация лазера");pulse.addView(toggle,new LinearLayout.LayoutParams(a.dp(46),a.dp(32)));box.addView(pulse);
        return new Studio.Builder(a).anchor(CompactInspector.anchor(a,"laser")).setTitle("Лазер").setView(box).show();
    }
    static final class Preview extends View {
        final MainActivity a;final LaserEffect sample=new LaserEffect();final LaserPainter painter=new LaserPainter();final Paint paint=new Paint(3);final long started=SystemClock.uptimeMillis();
        Preview(MainActivity a){super(a);this.a=a;setBackground(Studio.surface(0xff171b23,14,a.getResources().getDisplayMetrics().density));setClipToOutline(true);setContentDescription("Лазер: светящаяся точка и исчезающий след");}
        protected void onDraw(Canvas c){super.onDraw(c);if(a.canvas==null)return;long phase=(SystemClock.uptimeMillis()-started)%2800,contact=Math.min(phase,1750);sample.clear();sample.trail=a.canvas.laser.trail;sample.pulse=a.canvas.laser.pulse;sample.color=a.canvas.laser.color;sample.diameter=Ink.clamp(a.canvas.width,2,18);c.save();c.scale(getWidth()/274f,getHeight()/76f);paint.setColor(0xff272c36);paint.setStrokeWidth(.7f);for(int x=16;x<274;x+=22)c.drawLine(x,0,x,76,paint);for(int y=16;y<76;y+=22)c.drawLine(0,y,274,y,paint);
            if(phase>=180){sample.down(30,38,180);for(long t=200;t<=contact;t+=20){float q=(t-180)/1570f;sample.move(30+214*q,38+(float)Math.sin(q*Math.PI*2)*15,t);}if(phase>1750)sample.release(1750);painter.draw(c,sample,phase,1);}c.restore();if(hasWindowFocus()&&isShown())postInvalidateOnAnimation();
        }
        public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
        protected void onDetachedFromWindow(){sample.clear();super.onDetachedFromWindow();}
    }
}
