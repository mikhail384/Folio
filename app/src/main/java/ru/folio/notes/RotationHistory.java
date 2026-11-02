package ru.folio.notes;

/** Small timestamped sensor history; historical pen samples keep their own barrel angle. */
final class RotationHistory {
    static final int CAPACITY=64;
    final long[] times=new long[CAPACITY];final float[] angles=new float[CAPACITY];int start,count;
    void clear(){start=count=0;}
    boolean add(float degrees,long time){
        if(!Float.isFinite(degrees)||degrees<-360||degrees>360||time<0)return false;
        int last=(start+count-1)%CAPACITY;
        if(count>0&&time<times[last])return false;
        float angle=(float)Math.toRadians((degrees%360+360)%360);
        if(count>0&&time==times[last]){angles[last]=angle;return true;}
        int at=(start+count)%CAPACITY;if(count==CAPACITY){at=start;start=(start+1)%CAPACITY;}else count++;
        times[at]=time;angles[at]=angle;return true;
    }
    float at(long time,float fallback){
        if(count==0)return fallback;
        int latest=(start+count-1)%CAPACITY;
        if(time>=times[latest])return angles[latest];
        if(time<=times[start])return angles[start];
        for(int n=count-2;n>=0;n--){int left=(start+n)%CAPACITY,right=(left+1)%CAPACITY;if(times[left]<=time){float t=(time-times[left])/(float)(times[right]-times[left]);return angles[left]+InkGeometry.delta(angles[left],angles[right])*t;}}
        return fallback;
    }
}
