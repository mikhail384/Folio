package ru.folio.notes;

import android.content.res.AssetManager;
import android.graphics.*;
import java.io.*;
import java.util.*;

/** Original grain and tape bitmaps; world anchored shaders shared by every rendering path. */
final class StrokeTextures {
    static final String[] LABELS={"Без узора","Полосы","Клетка","Клубника","Подсолнухи","Лето"};
    static final Map<Integer,Bitmap> sources=new HashMap<>();
    static final LinkedHashMap<Long,BitmapShader> shaders=new LinkedHashMap<>(24,.75f,true);
    static final PorterDuffXfermode REPLACE=new PorterDuffXfermode(PorterDuff.Mode.SRC);
    static boolean initialized;static int decodes,compositions;
    static void init(AssetManager assets){if(initialized)return;initialized=true;
        String[] files={"pencil_texture.png","tape_stripe_1.png","tape_square_1.png","tape_texture_1_256.png","tape_texture_2_256.png","tape_texture_3_256.png"};
        for(int i=0;i<files.length;i++)try(InputStream in=assets.open("freenotes_strokes/"+files[i])){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inScaled=false;Bitmap b=BitmapFactory.decodeStream(in,null,opts);if(b!=null){sources.put(i,b);decodes++;}}catch(IOException|OutOfMemoryError e){android.util.Log.w("Folio","Ink material unavailable",e);}
    }
    static synchronized boolean apply(Paint p,Ink.Stroke s){int material=s.tool.equals("pencil")?0:s.tool.equals("tape")&&s.texture>0&&s.texture<=5?s.texture:-1;if(material<0||!sources.containsKey(material))return false;
        long key=((long)material<<32)|(s.color&0xffffffffL);BitmapShader shader=shaders.get(key);
        if(shader==null)try{Bitmap source=sources.get(material);int side=128;Bitmap tile=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);
            if(material==0){int[] pixels=new int[side*side];for(int y=0;y<side;y++)for(int x=0;x<side;x++){int hash=((x/16+17)*73856093)^((y/16+31)*19349663);int cell=(hash>>>3)&15,u=(x+(hash>>>7))&15,v=(y+(hash>>>12))&15;if((hash&1)!=0){int swap=u;u=v;v=swap;}if((hash&2)!=0)u=15-u;int grain=Color.red(source.getPixel(cell*16+u,v));int noise=((x*374761393+y*668265263)^hash);noise=(noise^(noise>>>13))*1274126177;int alpha=Math.max(35,Math.min(255,220-grain*2/3+((noise>>>24)&47)-24));pixels[y*side+x]=(alpha<<24)|(s.color&0xffffff);}tile.setPixels(pixels,0,side,0,0,side,side);}
            else {Canvas c=new Canvas(tile);Paint paint=new Paint(3);int span=Math.max(1,Math.min(source.getWidth(),source.getHeight())*4/5);Rect crop=material<3?new Rect((source.getWidth()-span)/2,(source.getHeight()-span)/2,(source.getWidth()+span)/2,(source.getHeight()+span)/2):new Rect(0,0,source.getWidth(),source.getHeight());c.drawBitmap(source,crop,new Rect(0,0,side,side),paint);
                if(material<3){int[] pixels=new int[side*side];tile.getPixels(pixels,0,side,0,0,side,side);for(int i=0;i<pixels.length;i++){float light=(Color.red(pixels[i])+Color.green(pixels[i])+Color.blue(pixels[i]))/765f;pixels[i]=Studio.blend(s.color|0xff000000,Color.WHITE,light>.95f?.8f:.15f);}tile.setPixels(pixels,0,side,0,0,side,side);}}
            shader=new BitmapShader(tile,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);Matrix m=new Matrix();m.setScale(material==0?.35f:.6f,material==0?.35f:.6f);shader.setLocalMatrix(m);shaders.put(key,shader);compositions++;
            while(shaders.size()>24){Iterator<Long> it=shaders.keySet().iterator();it.next();it.remove();}
        }catch(OutOfMemoryError e){return false;}
        p.setShader(shader);if(material==0)p.setXfermode(REPLACE);return true;
    }
}
