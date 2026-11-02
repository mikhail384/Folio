package ru.folio.notes;

import java.util.Arrays;

/** Transient light. A contact retains its entire path until lift, then fades as one gesture. */
public final class LaserEffect {
    public static final int DEFAULT_COLOR=0xfffa4261,CAPACITY=4096,BANDS=12,TRAIL_MS=900,FADE_MS=220;
    public boolean trail,pulse=true,touching,hovering;
    public int color=DEFAULT_COLOR;
    public float diameter=5,x,y;float pressX,pressY;
    final float[] xs=new float[CAPACITY],ys=new float[CAPACITY],errors=new float[CAPACITY];
    final long[] ends=new long[CAPACITY];final int[] gestures=new int[CAPACITY];
    int count,gesture;boolean visible;long began,ended=-1,pressed=-1;
    public interface Sink {
        void beginTrail(float width,int color,float alpha);
        void segment(float x1,float y1,float x2,float y2);
        void endTrail();
        void spot(float x,float y,float radius,int color,float alpha);
        void ring(float x,float y,float radius,float width,int color,float alpha);
    }
    static float clamp(float value){return Math.max(0,Math.min(1,value));}
    static float smooth(float value){float t=clamp(value);return t*t*(3-2*t);}
    public void down(float x,float y,long now){if(touching)release(now);trim(now);gesture++;began=pressed=now;ended=-1;visible=touching=true;hovering=false;this.x=pressX=x;this.y=pressY=y;if(trail)add(x,y,now);}
    public void move(float x,float y,long now){if(!touching)return;this.x=x;this.y=y;if(trail)add(x,y,now);}
    public void hover(float x,float y,long now){if(touching)return;if(!visible||ended>=0&&now-ended>=FADE_MS)began=now;visible=hovering=true;ended=-1;this.x=x;this.y=y;}
    public void release(long now){if(!touching&&!hovering)return;if(touching)for(int i=count-1;i>=0&&gestures[i]==gesture;i--)ends[i]=now;touching=hovering=false;ended=now;}
    public void leave(long now){if(hovering){hovering=false;ended=now;}}
    public void mode(boolean trace){if(trail!=trace){clear();trail=trace;}}
    void copy(int from,int to){xs[to]=xs[from];ys[to]=ys[from];ends[to]=ends[from];gestures[to]=gestures[from];}
    void discard(int amount){if(amount<=0)return;for(int i=amount;i<count;i++)copy(i,i-amount);count-=amount;}
    float error(int i){float ax=xs[i-1],ay=ys[i-1],dx=xs[i+1]-ax,dy=ys[i+1]-ay,length=dx*dx+dy*dy;float t=length<.000001f?0:clamp(((xs[i]-ax)*dx+(ys[i]-ay)*dy)/length);float x=xs[i]-ax-t*dx,y=ys[i]-ay-t*dy;return x*x+y*y;}
    /** Remove low-error interior samples, preserving every retained gesture's first and last point. */
    void compact(){int candidates=0;for(int i=1;i<count-1;i++)if(gestures[i-1]==gestures[i]&&gestures[i]==gestures[i+1])errors[candidates++]=error(i);if(candidates==0){int n=1;while(n<count&&gestures[n]==gestures[0])n++;discard(n);return;}Arrays.sort(errors,0,candidates);float threshold=errors[Math.min(candidates-1,candidates/3)];int write=0,removed=0;boolean skipped=false;for(int i=0;i<count;i++){boolean interior=i>0&&i<count-1&&gestures[i-1]==gestures[i]&&gestures[i]==gestures[i+1];boolean drop=interior&&!skipped&&removed<CAPACITY/3&&error(i)<=threshold;if(drop){removed++;skipped=true;}else {copy(i,write++);skipped=false;}}count=write;}
    void add(float x,float y,long now){trim(now);if(count>0&&gestures[count-1]==gesture&&Math.hypot(x-xs[count-1],y-ys[count-1])<.05)return;if(count==CAPACITY)compact();xs[count]=x;ys[count]=y;ends[count]=-1;gestures[count++]=gesture;}
    public void trim(long now){int expired=0;while(expired<count&&ends[expired]>=0&&now-ends[expired]>=TRAIL_MS)expired++;discard(expired);int groups=0;for(int i=0;i<count;i++)if(i==0||gestures[i]!=gestures[i-1])groups++;while(groups>BANDS){int n=1;while(n<count&&gestures[n]==gestures[0])n++;discard(n);groups--;}if(visible&&!touching&&!hovering&&ended>=0&&now-ended>=FADE_MS)visible=false;if(!visible&&count==1)count=0;}
    public boolean active(long now){trim(now);return visible||count>0;}
    public boolean needsFrame(long now){trim(now);for(int i=0;i<count;i++)if(ends[i]>=0)return true;return visible&&(pulse||now-began<75||ended>=0||pressed>=0&&now-pressed<330);}
    public int pointCount(){return count;}
    public void clear(){count=0;touching=hovering=visible=false;began=0;ended=pressed=-1;}
    public void render(long now,float unit,Sink sink){
        trim(now);unit=Math.max(.001f,unit);float size=Math.max(2,Math.min(18,diameter));
        if(trail)for(int start=0;start<count;){int end=start+1;while(end<count&&gestures[end]==gestures[start])end++;if(end-start>1){float fade=ends[start]<0?1:1-smooth((now-ends[start])/(float)TRAIL_MS);sink.beginTrail(size*unit*(.75f+.25f*fade),color,fade*fade);for(int i=start+1;i<end;i++)sink.segment(xs[i-1],ys[i-1],xs[i],ys[i]);sink.endTrail();}start=end;}
        if(!visible)return;float fade=touching||hovering?1:1-smooth((now-ended)/(float)FADE_MS),appear=smooth((now-began)/75f),beat=pulse?1+.065f*(float)Math.sin((now-began)*Math.PI*2/1100):1;
        float radius=size*.5f*unit*beat*(.55f+.45f*fade)*(.45f+.55f*appear);sink.spot(x,y,radius,color,fade*appear);
        if(pressed>=0&&now-pressed<330){float t=clamp((now-pressed)/330f);sink.ring(pressX,pressY,(size*.7f+12*smooth(t))*unit,.8f*unit,color,(1-t)*(1-t)*.36f*fade);}
    }
}
