package ru.folio.notes;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/** Desktop regression checks for the exact platform-free geometry used by Android. */
public final class GeometryTests {
    static int assertions,groups;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);assertions++;}
    static void pass(String label){groups++;System.out.println("PASS "+label);}
    static Ink.Stroke stroke(String tool,float width,Ink.Pt... points){Ink.Stroke s=new Ink.Stroke();s.tool=tool;s.width=width;java.util.Collections.addAll(s.points,points);return s;}
    static Ink.Pt pt(float x,float y,float p,float tilt,float angle){return new Ink.Pt(x,y,p,tilt,angle,.8f);}
    static final class Outline implements InkGeometry.Sink {
        final Path2D.Float path=new Path2D.Float(Path2D.WIND_NON_ZERO);int pieces;
        public void ellipse(float x,float y,float rx,float ry,float angle){path.append(AffineTransform.getRotateInstance(angle,x,y).createTransformedShape(new Ellipse2D.Float(x-rx,y-ry,rx*2,ry*2)),false);pieces++;}
        public void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){path.moveTo(ax,ay);path.lineTo(bx,by);path.lineTo(cx,cy);path.lineTo(dx,dy);path.closePath();pieces++;}
    }
    static Outline outline(Ink.Stroke s){Outline out=new Outline();InkGeometry.sweep(s,out);return out;}
    static void pens(){
        Ink.Stroke pen=stroke("pen",12),fountain=stroke("fountain",12);
        float minimum=Float.MAX_VALUE,maximum=0;
        for(int i=0;i<100;i++){
            Ink.Pt p=pt(0,0,.02f+i*.015f,i*.015f,i*.1f);InkGeometry.Tip a=InkGeometry.tip(pen,p),b=InkGeometry.tip(fountain,p);
            check(a.rx==6&&a.ry==6&&a.angle==0,"ballpoint ignores pressure and orientation");check(b.rx==b.ry&&b.angle==0,"fountain is circular");minimum=Math.min(minimum,b.rx);maximum=Math.max(maximum,b.rx);
            check(InkGeometry.tip(fountain,pt(0,0,p.p,0,0)).rx==b.rx,"fountain only responds to pressure");
        }
        check(maximum>minimum*2,"fountain pressure changes diameter");check(InkGeometry.tip(pen,pt(0,0,1,0,0)).rx==InkGeometry.tip(fountain,pt(0,0,1,1,2)).rx,"same diameter at unit pressure");check(Ink.defaultWidth("pen")==Ink.defaultWidth("fountain"),"matching defaults");pass("round pens differ only in pressure response");
    }
    static void brush(){
        Ink.Stroke s=stroke("brush",22);InkGeometry.Tip upright=InkGeometry.tip(s,pt(0,0,1,0,0)),lean=InkGeometry.tip(s,pt(0,0,1,1.2f,0)),roll=InkGeometry.tip(s,pt(0,0,1,1.2f,1.4f));
        check(lean.rx>upright.rx&&lean.ry<upright.ry,"tilt spreads and flattens bristles");check(Math.abs(roll.angle-lean.angle)>1,"barrel roll rotates bristles");check(InkGeometry.tip(s,pt(0,0,.1f,1.2f,1.4f)).rx<roll.rx,"brush pressure reduces footprint");
        Ink.Pt otherBearing=new Ink.Pt(0,0,1,1.2f,0,-.8f);check(Math.abs(InkGeometry.tip(s,otherBearing).angle-lean.angle)>.1,"tilt bearing changes footprint");
        float delta=InkGeometry.delta((float)Math.toRadians(359),(float)Math.toRadians(1));check(Math.abs(delta-Math.toRadians(2))<1e-5,"roll crosses zero on shortest arc");pass("brush pressure, tilt, barrel roll and azimuth");
    }
    static void continuity(){
        for(String tool:new String[]{"pen","fountain","brush","pencil"})for(float width:new float[]{1,3,18,60}){
            Ink.Stroke s=stroke(tool,width,pt(40,90,.12f,.15f,0),pt(1240,90,1.1f,1.2f,2.8f));Outline out=outline(s);
            for(int x=41;x<1240;x++)check(out.path.contains(x,90),"sparse stroke contains centre "+tool);
            check(out.pieces<60,"geometry cost independent of spatial gap");
        }
        Ink.Stroke dots=stroke("brush",20,pt(50,50,1,.4f,0),pt(50,50,.4f,1.2f,2.6f));check(outline(dots).path.contains(50,50),"stationary pressure and roll keep ink");
        Ink.Stroke corner=stroke("brush",12,pt(20,20,.5f,.5f,0),pt(400,20,.5f,.5f,1.6f),pt(400,350,1,1.2f,3),pt(20,350,.5f,.2f,6));Outline out=outline(corner);for(int y=21;y<350;y++)check(out.path.contains(400,y),"no holes at rotated corner");pass("continuous sparse input and fast rotating corners without oval gaps");
    }
    static void raster(){
        BufferedImage image=new BufferedImage(1300,160,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1300,160);g.setColor(Color.BLACK);g.fill(outline(stroke("brush",4,pt(40,80,1,.7f,.2f),pt(1240,80,1,.7f,.2f))).path);g.dispose();
        for(int x=41;x<1240;x++)check((image.getRGB(x,80)&0xffffff)==0,"no raster gaps with 1200px sparse interval");pass("rasterized ribbon stays connected");
    }
    static void placement(){
        Random random=new Random(101);for(int i=0;i<3000;i++){int w=320+random.nextInt(1600),h=220+random.nextInt(1100),ax=random.nextInt(w-44),ay=random.nextInt(h-64);int[] b=PopoverLayout.place(0,0,w,h,ax,ay,ax+43,ay+64,302,330,10);check(b[0]>=10&&b[1]>=10&&b[0]+b[2]<=w-10&&b[1]+b[3]<=h-10,"card stays inside window");if(ay+64+10+330<=h-10)check(b[1]==ay+74,"card sits directly below tool");else if(ay-10-330>=10)check(b[1]==ay-340,"card sits above tool when needed");}
        check(PopoverLayout.spring(0)==0&&PopoverLayout.spring(1)==1,"spring reaches endpoints");for(int i=0;i<=100;i++)check(Float.isFinite(PopoverLayout.spring(i/100f)),"finite spring");pass("popover edges, adjacent placement and spring endpoints");
    }
    static void transforms(){
        Ink.Stroke s=stroke("brush",20,new Ink.Pt(10,20,.5f,.7f,1.3f,-.4f));Ink.Pt shifted=s.translated(5,7).points.get(0),rotated=s.transformed(0,0,2,.8f).points.get(0);check(shifted.azimuth==s.points.get(0).azimuth,"translation preserves tilt bearing");check(Math.abs(rotated.azimuth-.4f)<1e-5&&Math.abs(rotated.angle-2.1f)<1e-5,"selection rotates both roll and bearing");pass("selection transforms retain nib orientation");
    }
    static void preview(File output)throws Exception{
        BufferedImage image=new BufferedImage(1100,760,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(0xf7f5ef));g.fillRect(0,0,1100,760);g.setColor(new Color(0x292d35));g.setFont(new Font("SansSerif",Font.BOLD,25));g.drawString("Folio 1.3 / shared ink geometry",45,48);g.setFont(new Font("SansSerif",Font.PLAIN,16));
        String[] tools={"pen","fountain","brush","brush"},labels={"Ballpoint / constant round width","Fountain / round, pressure only","Brush / pressure, tilt and roll","Sparse input / 2 points, 950px gap"};
        for(int row=0;row<4;row++){float y=145+row*170;g.setColor(new Color(0x626875));g.drawString(labels[row],45,y-40);Ink.Stroke s=stroke(tools[row],row==3?8:24);if(row==3){s.points.add(pt(65,y,.8f,.8f,.7f));s.points.add(pt(1015,y,.8f,.8f,.7f));}else for(int i=0;i<=160;i++){float t=i/160f;s.points.add(new Ink.Pt(65+950*t,y+(float)Math.sin(t*Math.PI*4)*20,.04f+.96f*(float)Math.sin(t*Math.PI),t*1.3f,t*4,.8f));}g.setColor(new Color(row<2?0x343b51:0x8b84cb));g.fill(outline(s).path);}
        g.dispose();ImageIO.write(image,"png",output);
    }
    public static void main(String[] args)throws Exception{pens();brush();continuity();raster();placement();transforms();preview(new File(args[0]));System.out.println("PASS: "+assertions+" assertions in "+groups+" groups");}
}
