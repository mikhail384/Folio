package ru.folio.notes;

import android.graphics.*;
import android.view.*;
import android.widget.LinearLayout;
import java.util.Locale;

/** Fixed on the main pen case, outside the scrolling tool list. */
final class PresetStrip extends LinearLayout {
    final MainActivity a;final Dot[] dots=new Dot[3];
    PresetStrip(MainActivity a){super(a);this.a=a;setGravity(Gravity.CENTER);setPadding(a.dp(6),0,a.dp(6),0);
        View divider=new View(a);divider.setBackgroundColor(Studio.LINE);LayoutParams line=new LayoutParams(a.dp(1),a.dp(24));line.rightMargin=a.dp(5);addView(divider,line);
        for(int slot=0;slot<3;slot++){Dot dot=new Dot(slot);dots[slot]=dot;addView(dot,new LayoutParams(a.dp(42),a.dp(48)));ToolbarMotion.press(a,dot);}refresh();
    }
    void refresh(){if(a.canvas==null)return;for(Dot dot:dots){boolean enabled=a.canvas.canColor();dot.setEnabled(enabled);dot.setAlpha(enabled?1:.26f);boolean selected=enabled&&a.canvas.presets.selected(a.canvas.tool,a.canvas.activeColor())==dot.slot;dot.setSelected(selected);dot.setContentDescription("Цвет "+(dot.slot+1)+": "+ColorModel.hex(a.canvas.presets.color(a.canvas.tool,dot.slot))+(enabled?(selected?". Нажмите, чтобы изменить":". Нажмите, чтобы выбрать; удерживайте, чтобы изменить"):". Недоступен для этого инструмента"));dot.invalidate();}}
    void choose(int slot){NotesView v=a.canvas;if(v==null||!v.canColor())return;if(v.presets.selected(v.tool,v.activeColor())==slot){edit(slot);return;}v.presets.select(v.tool,slot);v.activeColor(v.presets.color(v.tool,slot));a.toolChanged();refresh();}
    Studio.Sheet edit(int slot){NotesView target=a.canvas;if(target==null||!target.canColor())return null;String tool=target.tool;
        return ColorStudio.show(a,target.presets.color(tool,slot),color->{if(a.canvas!=target)return;target.presets.put(tool,slot,color);target.presets.select(tool,slot);target.colorFor(tool,color);a.toolChanged();refresh();},dots[slot],String.format(Locale.ROOT,"Цвет %d · %s",slot+1,Ink.label(tool)));
    }
    final class Dot extends View {final int slot;final Paint p=new Paint(3);Dot(int slot){super(a);this.slot=slot;setFocusable(true);setClickable(true);setOnClickListener(v->choose(slot));setOnLongClickListener(v->{edit(slot);return true;});}
        protected void onDraw(Canvas c){if(a.canvas==null)return;float x=getWidth()/2f,y=getHeight()/2f,r=a.dp(10);int color=a.canvas.presets.color(a.canvas.tool,slot);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(.8f));p.setColor(0x608f95a3);c.drawCircle(x,y,r,p);
            if(isSelected()){p.setColor(Studio.ACCENT);p.setStrokeWidth(a.dp(1.6f));c.drawCircle(x,y,r+a.dp(4),p);p.setStyle(Paint.Style.FILL);p.setColor(Color.luminance(color)>.45?0xff22252d:0xfffaf8f1);c.drawCircle(x,y,a.dp(2),p);}p.setStyle(Paint.Style.FILL);
        }
    }
}
