package ru.folio.notes;

import android.content.res.AssetManager;
import android.graphics.*;
import java.io.*;
import java.util.*;

/** Original Freenotes tool layers. Bodies and color masks are composed once, never decoded per frame. */
final class ToolTextures {
    static final int WIDTH=104;static final float HEIGHT=83;
    static final Map<String,Sprite> sprites=new HashMap<>();static final Map<String,Bitmap> flats=new HashMap<>();
    static final Paint image=new Paint(3);static final RectF target=new RectF();static AssetManager assets;static boolean initialized;static int compositions,decodes;
    static final class Sprite {final Bitmap body,mask;final float width;final Paint ink=new Paint(3);int color;boolean colored;
        Sprite(Bitmap body,Bitmap mask){this.body=body;this.mask=mask;width=WIDTH*HEIGHT/body.getHeight();}
        void draw(Canvas canvas,int color){target.set(-width/2,0,width/2,HEIGHT);canvas.drawBitmap(body,null,target,image);if(mask!=null){if(!colored||this.color!=color){ink.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));this.color=color;colored=true;}canvas.drawBitmap(mask,null,target,ink);}}
    }
    static String family(String tool){switch(tool){case "pen":return "ball";case "fountain":return "fountain";case "pencil":return "pencil";case "marker":return "marker";case "brush":case "rainbow":return "felt";case "eraser":return "eraser";case "laser":return "laser";case "lasso":return "lasso";default:return "";}}
    static boolean nib(String tool){return !family(tool).isEmpty();}
    static void init(AssetManager manager){if(initialized)return;assets=manager;
        add("ball",new String[]{"ic_ball_pen_part_1_night.png","ic_ball_pen_part_2_night.png","ic_ball_pen_part_3_night.png","ic_ball_pen_part_4_night.png"},new boolean[]{true,false,true,false},"ic_ball_pen_highlighter_night.png");
        add("fountain",new String[]{"ic_pen_part_1.png","ic_pen_part_2_night.png","ic_pen_part_3_night.png","ic_pen_part_4_night.png"},new boolean[]{false,false,true,false},"ic_pen_highlighter_night.png");
        add("pencil",new String[]{"ic_pencil_part_1.png","ic_pencil_part_2_night.png","ic_pencil_part_3_night.png","ic_pencil_part_4_night.png"},new boolean[]{true,false,true,false},null);
        add("marker",new String[]{"ic_highlighter_part_1_night.png","ic_highlighter_part_2_night.png","ic_highlighter_part_3_night.png","ic_highlighter_part_4_night.png"},new boolean[]{true,false,true,false},"ic_highlighter_pen_highlighter_night.png");
        add("felt",new String[]{"ic_underline_pen_part_1.png","ic_underline_pen_part_2_night.png","ic_underline_pen_part_3.png","ic_underline_pen_part_4_night.png"},new boolean[]{true,false,true,false},null);
        add("eraser",new String[]{"ic_eraser_part_1_night.png","ic_eraser_part_2_night.png"},new boolean[]{false,false},"ic_eraser_highlighter_night.png");
        add("eraser_stroke",new String[]{"ic_eraser_part_1_night.png","ic_eraser_part_2_night_cha.png","ic_eraser_part_3_night.png"},new boolean[]{false,false,false},"ic_eraser_highlighter_night.png");
        add("laser",new String[]{"ic_laser_pen_head1.png","ic_laser_pen_head_night.png","ic_laser_pen_foot_night.png"},new boolean[]{false,false,false},"ic_laser_highlighter_night.png");
        add("lasso",new String[]{"ic_lasso_part_1_night.png","ic_lasso_part_2_night.png"},new boolean[]{false,false},"ic_lasso_highlighter_night.png");
        flats.put("compass",GuideArt.icon());loadFlat("tape","ic_tape_25.png");loadFlat("line","ic_brush_ruler_night.png");initialized=true;
    }
    static Bitmap decode(String file)throws IOException{try(InputStream stream=assets.open("freenotes_tools/"+file)){BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);if(bitmap==null)throw new IOException("Invalid texture: "+file);decodes++;return bitmap;}}
    static void loadFlat(String tool,String file){try{flats.put(tool,decode(file));}catch(IOException|OutOfMemoryError error){android.util.Log.w("Folio","Tool texture unavailable: "+tool,error);}}
    static void add(String family,String[] files,boolean[] colors,String highlight){
        ArrayList<Bitmap> parts=new ArrayList<>();Bitmap body=null,mask=null,glow=null,laser=null;
        try{float height=0;boolean hasColor=family.equals("laser");for(int i=0;i<files.length;i++){Bitmap part=decode(files[i]);parts.add(part);height+=WIDTH*part.getHeight()/(float)part.getWidth();hasColor|=colors[i];}
            body=Bitmap.createBitmap(WIDTH,(int)Math.ceil(height),Bitmap.Config.ARGB_8888);if(hasColor)mask=Bitmap.createBitmap(WIDTH,body.getHeight(),Bitmap.Config.ARGB_8888);Canvas base=new Canvas(body),tinted=mask==null?null:new Canvas(mask);Paint paint=new Paint(3);RectF rect=new RectF();float top=0;
            for(int i=0;i<parts.size();i++){Bitmap part=parts.get(i);float h=WIDTH*part.getHeight()/(float)part.getWidth();rect.set(0,top,WIDTH,top+h);(colors[i]?tinted:base).drawBitmap(part,null,rect,paint);top+=h;}
            if(family.equals("laser")){laser=decode("ic_laser_pen_head2.png");rect.set(0,0,WIDTH,WIDTH*laser.getHeight()/(float)laser.getWidth());tinted.drawBitmap(laser,null,rect,paint);}
            if(highlight!=null){glow=decode(highlight);paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));float start=family.equals("fountain")?height*.34f:height*.12f;rect.set(0,start,WIDTH,height);base.drawBitmap(glow,null,rect,paint);paint.setXfermode(null);}
            sprites.put(family,new Sprite(body,mask));body=mask=null;compositions++;
        }catch(IOException|OutOfMemoryError error){android.util.Log.w("Folio","Tool texture unavailable: "+family,error);}
        finally{for(Bitmap part:parts)part.recycle();if(glow!=null)glow.recycle();if(laser!=null)laser.recycle();if(body!=null)body.recycle();if(mask!=null)mask.recycle();}
    }
    static boolean draw(Canvas canvas,String tool,float x,float y,float scale,int color){return draw(canvas,tool,x,y,scale,color,"area");}
    static boolean draw(Canvas canvas,String tool,float x,float y,float scale,int color,String mode){Sprite sprite=sprites.get(tool.equals("eraser")&&mode.equals("stroke")?"eraser_stroke":family(tool));if(sprite==null)return false;canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);sprite.draw(canvas,color);canvas.restore();return true;}
    static boolean flat(Canvas canvas,String tool,float x,float y,float size,int color){Bitmap bitmap=flats.get(tool);if(bitmap==null)return false;float scale=size/Math.max(bitmap.getWidth(),bitmap.getHeight()),w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;target.set(x-w/2,y-h/2,x+w/2,y+h/2);if(tool.equals("tape"))image.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));canvas.drawBitmap(bitmap,null,target,image);image.setColorFilter(null);return true;}
}
