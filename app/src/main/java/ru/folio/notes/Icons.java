package ru.folio.notes;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class Icons extends View {
    public final String name;
    public int tint=0xffc3cbd6;
    public Icons(Context c,String name){super(c);this.name=name;setContentDescription(name);}
    @Override protected void onDraw(Canvas c){draw(c,name,getWidth()/2f,getHeight()/2f,Math.min(getWidth(),getHeight())*.48f,tint);}
    public static void draw(Canvas canvas,String name,float x,float y,float size,int color){
        Canvas c=canvas;Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.save();c.translate(x-size/2,y-size/2);c.scale(size/24,size/24);
        switch(name){
            case "layers":for(int i=0;i<3;i++){Path shape=new Path();shape.moveTo(3,7+i*5);shape.lineTo(12,3+i*5);shape.lineTo(21,7+i*5);shape.lineTo(12,11+i*5);shape.close();c.drawPath(shape,p);}break;
            case "bookmark":{Path b=new Path();b.moveTo(6,3);b.lineTo(18,3);b.lineTo(18,21);b.lineTo(12,17);b.lineTo(6,21);b.close();c.drawPath(b,p);break;}
            case "audio":for(int i=0;i<5;i++){float h=3+(2-Math.abs(i-2))*3;c.drawLine(4+i*4,12-h,4+i*4,12+h,p);}break;
            case "cloud":{Path b=new Path();b.moveTo(6,18);b.cubicTo(-2,18,0,9,6,10);b.cubicTo(7,1,19,2,19,10);b.cubicTo(25,10,25,18,18,18);b.close();c.drawPath(b,p);break;}
            case "dropper":c.save();c.rotate(40,12,12);c.drawRoundRect(9,6,15,18,2,2,p);c.drawLine(8,6,16,6,p);c.drawLine(12,2,12,6,p);c.drawLine(12,18,12,22,p);c.restore();break;
            case "eye":case "eye-off":{Path b=new Path();b.moveTo(2,12);b.quadTo(12,0,22,12);b.quadTo(12,24,2,12);c.drawPath(b,p);c.drawCircle(12,12,3,p);if(name.equals("eye-off"))c.drawLine(3,3,21,21,p);break;}
            case "lock":case "unlock":c.drawRoundRect(5,10,19,21,3,3,p);c.drawArc(8,2,16,16,name.equals("lock")?180:210,name.equals("lock")?180:130,false,p);c.drawCircle(12,15,1,p);break;
            case "up":case "down":c.save();if(name.equals("down"))c.rotate(180,12,12);c.drawLine(12,3,12,21,p);c.drawLine(12,3,5,10,p);c.drawLine(12,3,19,10,p);c.restore();break;

            case "compass":path(c,p,5,22,12,3,20,22);c.drawCircle(12,5,3,p);line(c,p,5,22,4,24);line(c,p,20,22,21,24);line(c,p,7,17,18,17);break;
            case "protractor":c.drawArc(new RectF(1,2,23,24),180,180,false,p);line(c,p,1,13,23,13);line(c,p,12,2,12,5);line(c,p,4,5,6,7);line(c,p,20,5,18,7);break;
            case "triangle":path(c,p,12,2,23,22,1,22,12,2);break;
            case "arrow":path(c,p,3,21,21,3,12,4);path(c,p,21,3,20,12);break;
            case "fountain":path(c,p,12,1,20,13,12,22,4,13,12,1);line(c,p,12,1,12,13);c.drawCircle(12,13,2,p);break;
            case "laser":c.drawCircle(12,12,4,p);line(c,p,12,1,12,5);line(c,p,12,19,12,23);line(c,p,1,12,5,12);line(c,p,19,12,23,12);break;
            case "rainbow":p.setColor(0xffdf6a96);c.drawArc(new RectF(1,1,23,23),180,180,false,p);p.setColor(0xffdcc36b);c.drawArc(new RectF(5,5,19,23),180,180,false,p);p.setColor(0xff71b6d4);c.drawArc(new RectF(9,9,15,23),180,180,false,p);break;
            case "tape":c.drawRoundRect(new RectF(2,7,22,17),2,2,p);line(c,p,6,7,6,17);line(c,p,18,7,18,17);break;
            case "pen":case "pencil":case "brush":path(c,p,5,19,6,13,17,2,22,7,11,18,5,19);line(c,p,14,5,19,10);if(name.equals("brush")){path(c,p,5,19,3,22,8,21,10,18);}else line(c,p,6,13,11,18);break;
            case "marker":path(c,p,3,17,12,8,18,14,9,23,3,17);path(c,p,12,8,16,2,23,9,18,14);line(c,p,2,23,10,23);break;
            case "eraser":path(c,p,3,14,13,3,22,11,12,22,10,22,3,16,3,14);line(c,p,7,10,16,18);line(c,p,11,22,23,22);break;
            case "lasso":p.setPathEffect(new DashPathEffect(new float[]{3,3},0));c.drawOval(new RectF(2,2,22,17),p);p.setPathEffect(null);path(c,p,8,16,6,22,12,20,8,16);break;
            case "line":case "ruler":path(c,p,2,16,16,2,22,8,8,22,2,16);line(c,p,6,12,9,15);line(c,p,10,8,13,11);line(c,p,14,4,17,7);break;
            case "rect":c.drawRoundRect(new RectF(3,4,21,20),2,2,p);break;
            case "ellipse":c.drawOval(new RectF(3,4,21,20),p);break;
            case "text":line(c,p,3,4,21,4);line(c,p,12,4,12,21);line(c,p,8,21,16,21);break;
            case "hand":path(c,p,5,12,5,4,8,4,8,11,8,2,11,2,11,11,11,3,14,3,14,12,14,6,17,6,17,14,19,12,22,13,17,22,8,22,2,15,3,12,8,16);break;
            case "back":path(c,p,15,4,7,12,15,20);break;
            case "next":path(c,p,9,4,17,12,9,20);break;
            case "undo":path(c,p,8,5,3,10,8,15);path(c,p,3,10,14,10,18,12,20,16,18,21);break;
            case "redo":path(c,p,16,5,21,10,16,15);path(c,p,21,10,10,10,6,12,4,16,6,21);break;
            case "plus":line(c,p,12,4,12,20);line(c,p,4,12,20,12);break;
            case "close":line(c,p,5,5,19,19);line(c,p,19,5,5,19);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(5+i*7,12,1.6f,p);break;
            case "folder":path(c,p,2,7,2,4,9,4,12,7,22,7,22,21,2,21,2,7);break;
            case "book":path(c,p,5,2,21,2,21,22,5,22,3,20,3,5,5,2);line(c,p,7,2,7,22);line(c,p,11,7,17,7);break;
            case "grid":for(int i=0;i<2;i++)for(int j=0;j<2;j++)c.drawRoundRect(new RectF(3+i*11,3+j*11,10+i*11,10+j*11),1,1,p);break;
            case "pages":path(c,p,8,2,21,2,21,18,8,18,8,2);path(c,p,4,6,3,6,3,23,16,23,16,21);break;
            case "import":line(c,p,12,2,12,15);path(c,p,7,10,12,15,17,10);path(c,p,3,15,3,22,21,22,21,15);break;
            case "export":line(c,p,12,15,12,2);path(c,p,7,7,12,2,17,7);path(c,p,3,13,3,22,21,22,21,13);break;
            case "settings":c.drawCircle(12,12,4,p);c.drawCircle(12,12,9,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;line(c,p,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9,12+(float)Math.cos(a)*12,12+(float)Math.sin(a)*12);}break;
            case "search":c.drawCircle(10,10,7,p);line(c,p,15,15,22,22);break;
            case "trash":path(c,p,5,6,6,22,18,22,19,6);line(c,p,3,6,21,6);path(c,p,8,6,8,2,16,2,16,6);line(c,p,10,10,10,18);line(c,p,14,10,14,18);break;
            case "copy":c.drawRoundRect(new RectF(7,7,22,22),2,2,p);path(c,p,17,4,17,2,2,2,2,17,4,17);break;
            case "paper":path(c,p,4,2,16,2,21,7,21,22,4,22,4,2);path(c,p,16,2,16,7,21,7);line(c,p,8,12,17,12);line(c,p,8,16,17,16);break;
            case "check":path(c,p,4,12,9,18,21,5);break;
            case "focus":path(c,p,3,9,3,3,9,3);path(c,p,15,3,21,3,21,9);path(c,p,21,15,21,21,15,21);path(c,p,9,21,3,21,3,15);break;
            case "star":path(c,p,12,2,15,9,23,10,17,15,19,23,12,19,5,23,7,15,1,10,9,9,12,2);break;
            case "chatgpt":{c.drawRoundRect(new RectF(2,3,22,17),4,4,p);path(c,p,7,17,6,21,12,17);Path spark=new Path();spark.moveTo(15,5);spark.lineTo(16.3f,8.1f);spark.lineTo(20,9);spark.lineTo(16.3f,10.1f);spark.lineTo(15,13);spark.lineTo(13.7f,10.1f);spark.lineTo(10,9);spark.lineTo(13.7f,8.1f);spark.close();c.drawPath(spark,p);break;}
            default:c.drawCircle(12,12,9,p);line(c,p,12,7,12,13);c.drawPoint(12,17,p);
        }c.restore();
    }
    static void line(Canvas c,Paint p,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
    static void path(Canvas c,Paint p,float... v){Path path=new Path();path.moveTo(v[0],v[1]);for(int i=2;i<v.length;i+=2)path.lineTo(v[i],v[i+1]);c.drawPath(path,p);}
}
