package ru.folio.notes;

import android.graphics.*;

/** Miniature physical tools, cached once per ink/density instead of rebuilt each animation frame. */
final class RadialPainter {
    final Paint paint=new Paint(3),images=new Paint(3);final RectF oval=new RectF();
    final Bitmap[] icons=new Bitmap[RadialTools.PAGE_SIZE];final String[] keys=new String[RadialTools.PAGE_SIZE];
    LinearGradient face;float density,iconScale=1;final int[] cachedInks=new int[RadialTools.PAGE_SIZE];boolean lowMemory;
    void prepare(NotesView view,float scale){
        String[] tools=view.radialTools;
        if(density!=scale||face==null){clear();density=scale;face=new LinearGradient(0,-110*scale,0,110*scale,new int[]{0xff363944,0xff23262f,0xff191c24},null,Shader.TileMode.CLAMP);}
        int count=view.radialOrder.visibleCount();float nextScale=count<=8?1:count<=10?.92f:.84f;if(iconScale!=nextScale){iconScale=nextScale;for(int i=0;i<icons.length;i++){if(icons[i]!=null)icons[i].recycle();icons[i]=null;keys[i]=null;cachedInks[i]=0;}}
        int size=Math.max(1,Math.round(52*scale*iconScale));
        for(int i=0;i<icons.length;i++){if(tools[i]==null){if(icons[i]!=null)icons[i].recycle();icons[i]=null;keys[i]=null;continue;}int ink=view.colorFor(tools[i]);String key=tools[i]+(tools[i].equals("eraser")?view.eraserMode:"");if(cachedInks[i]==ink&&key.equals(keys[i]))continue;if(icons[i]!=null){icons[i].recycle();icons[i]=null;}keys[i]=null;if(lowMemory)continue;try{Bitmap icon=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(icon);canvas.scale(scale*iconScale,scale*iconScale);canvas.save();canvas.clipPath(circle(26,26,22));if(ToolArt.nib(tools[i])){canvas.rotate(i%2==0?-11:11,26,26);ToolArt.draw(canvas,tools[i],26,7,.53f,ink,view.eraserMode);}else if(!ToolTextures.flat(canvas,tools[i],26,26,24,0xffe5e4ed)){Icons.draw(canvas,tools[i],26,26,24,0xffe5e4ed);}canvas.restore();icons[i]=icon;keys[i]=key;cachedInks[i]=ink;}catch(OutOfMemoryError error){lowMemory=true;}}
    }
    static Path circle(float x,float y,float r){Path p=new Path();p.addCircle(x,y,r,Path.Direction.CW);return p;}
    void draw(Canvas c,NotesView view){
        float d=view.dp(1),progress=Ink.clamp(view.radialProgress,0,1);prepare(view,d);
        c.drawColor(Color.argb((int)(20*progress),10,13,22));int layer=progress>=.999f?c.save():c.saveLayerAlpha(view.radialX-135*d,view.radialY-135*d,view.radialX+135*d,view.radialY+135*d,(int)(255*progress));
        c.translate(view.radialX,view.radialY);float reveal=.84f+.16f*progress;c.scale(reveal,reveal);
        paint.setShader(null);paint.setColor(0x33000000);c.drawCircle(0,5*d,112*d,paint);paint.setShader(face);c.drawCircle(0,0,110*d,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0x25ffffff);c.drawCircle(0,0,109.5f*d,paint);paint.setColor(0xff414550);c.drawCircle(0,0,51*d,paint);paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff191c24);c.drawCircle(0,0,48*d,paint);
        int count=view.radialOrder.visibleCount();float radius=count<=8?RadialPalette.TOOL_RADIUS:count<=10?82:84,button=23*iconScale;
        for(int i=0;i<view.radialTools.length;i++){
            if(view.radialTools[i]==null)continue;float lift=Ink.clamp(view.paletteHighlights[i].value,0,1),orb=radius*(.96f+.04f*progress),x=RadialPalette.x(i,orb,count)*d,y=RadialPalette.y(i,orb,count)*d-3*d*lift;boolean selected=view.tool.equals(view.radialTools[i]);
            paint.setColor(Studio.blend(selected?0xff55516e:0xff30343e,0xff4d4967,lift));c.drawCircle(x,y,button*d,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((selected?1.3f:.7f)*d);paint.setColor(Studio.blend(selected?Studio.ACCENT:0xff505460,Studio.ACCENT,lift));c.drawCircle(x,y,button*d,paint);paint.setStyle(Paint.Style.FILL);
            c.save();c.translate(x,y);float grow=1+.035f*lift;c.scale(grow,grow);oval.set(-26*d*iconScale,-26*d*iconScale,26*d*iconScale,26*d*iconScale);if(icons[i]!=null)c.drawBitmap(icons[i],null,oval,images);else Icons.draw(c,view.radialTools[i],0,0,24*d*iconScale,Studio.TEXT);c.restore();
            if(selected){paint.setColor(Studio.ACCENT);c.drawCircle(x,y+18*d,1.6f*d,paint);}
        }
        for(int i=0;i<RadialPalette.COLOR_COUNT;i++){
            float glow=Ink.clamp(view.paletteHighlights[i+view.radialTools.length].value,0,1),x=RadialPalette.x(i,RadialPalette.COLOR_RADIUS)*d,y=RadialPalette.y(i,RadialPalette.COLOR_RADIUS)*d;boolean selected=view.canColor()&&view.activeColor()==view.colors[i];
            paint.setColor(view.canColor()?view.colors[i]:Studio.blend(view.colors[i],0xff191c24,.8f));c.drawCircle(x,y,(8+glow)*d,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.7f*d);paint.setColor(0x45ffffff);c.drawCircle(x,y,(8+glow)*d,paint);paint.setStyle(Paint.Style.FILL);
            if(selected||glow>.001f){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f*d);paint.setColor(Studio.ACCENT);paint.setAlpha((int)(255*Math.max(selected?.7f:0,glow)));c.drawCircle(x,y,(12+glow)*d,paint);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);}
        }
        paint.setColor(0xff292d37);c.drawCircle(0,0,14*d,paint);paint.setColor(0xffd8d3f9);paint.setTextSize(7*d);paint.setTextAlign(Paint.Align.CENTER);c.drawText(view.radialOrder.pages()>1?(view.radialOrder.page+1)+"/"+view.radialOrder.pages()+" ›":"folio",0,2.5f*d,paint);paint.setTextAlign(Paint.Align.LEFT);c.restoreToCount(layer);
    }
    void clear(){for(int i=0;i<icons.length;i++){if(icons[i]!=null)icons[i].recycle();icons[i]=null;keys[i]=null;}face=null;lowMemory=false;}
}
