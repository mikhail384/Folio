package ru.folio.penbridge;

import android.net.LocalServerSocket;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One shell daemon owns the pen FD for both apps. The app providers check priority again. */
public final class SharedPenBridge {
    static final ShellClient folio=new ShellClient("ru.folio.notes.pen"),focus=new ShellClient("ru.focusflow.pen.bridge");
    static final PenOwner policy=new PenOwner();static final AtomicReference<FileInputStream> stream=new AtomicReference<>();
    static final BlockingQueue<Event> events=new ArrayBlockingQueue<>(256);
    static final ArrayList<LocalServerSocket> locks=new ArrayList<>();
    static volatile boolean grabbed;static volatile String device="",mode="observe";
    static long now(){return System.nanoTime()/1000000;}
    static String owner(){int value=policy.owner(now());return value==PenOwner.FOLIO?"folio":value==PenOwner.FOCUSFLOW?"focusflow":"none";}
    static Bundle extra(boolean blocked){Bundle value=new Bundle();value.putInt("sharedProtocol",2);value.putBoolean("folioPriority",blocked);value.putString("owner",owner());return value;}
    static final class Event {final String text;final PenOwner.Ticket ticket;final long at;Event(long time,int code,int value,PenOwner.Ticket ticket){text=time+","+code+","+value;this.ticket=ticket;at=now();}}
    static synchronized void grab(boolean value){FileInputStream input=stream.get();if(input==null){grabbed=false;return;}if(grabbed==value)return;try{
        Class<?> os=Class.forName("android.system.Os");Method acquire=os.getDeclaredMethod("ioctlInt",FileDescriptor.class,int.class),release=os.getDeclaredMethod("ioctlRet",FileDescriptor.class,int.class);Method method=value?acquire:release;method.setAccessible(true);method.invoke(null,input.getFD(),0x40044590);grabbed=value;mode=value?"grabbed":"observe";
    }catch(Throwable error){mode="error:EVIOCGRAB "+error.getClass().getSimpleName();if(!value&&grabbed)Runtime.getRuntime().halt(0);}}
    static String discover(String name)throws Exception{java.lang.Process p=new ProcessBuilder("/system/bin/getevent","-pl").redirectErrorStream(true).start();String current=null,found=null;try(BufferedReader reader=new BufferedReader(new InputStreamReader(p.getInputStream()))){String line;while((line=reader.readLine())!=null){if(line.startsWith("add device ")&&line.contains("/dev/input/"))current=line.substring(line.indexOf("/dev/input/")).trim();if(line.contains("name:")&&line.contains("\""+name+"\""))found=current;}}p.waitFor();return found!=null&&found.matches("/dev/input/event[0-9]+")?found:null;}
    static long time(ByteBuffer b,int size){long seconds=size==24?b.getLong():Integer.toUnsignedLong(b.getInt()),micros=size==24?b.getLong():Integer.toUnsignedLong(b.getInt());return seconds*1000+micros/1000;}
    static boolean read(InputStream input,byte[] bytes)throws IOException{int count=0,n;while(count<bytes.length&&(n=input.read(bytes,count,bytes.length-count))!=-1)count+=n;return count==bytes.length;}
    static void input(){while(true)try{String path=discover("Xiaomi Focus Pen Pro Keyboard");if(path==null){pause(1500);continue;}device=path;FileInputStream input=new FileInputStream(path);stream.set(input);grabbed=false;try{
        byte[] bytes=new byte[android.os.Process.is64Bit()?24:16];while(read(input,bytes)){ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);long time=time(b,bytes.length);int type=b.getShort()&65535,code=b.getShort()&65535,value=b.getInt();if(type==1&&(value==0||value==1)){PenOwner.Ticket ticket=policy.key(code,value,now());if(ticket!=null)events.offer(new Event(time,code,value,ticket));}}
    }finally{grab(false);stream.compareAndSet(input,null);input.close();device="";}}catch(Exception error){mode="error:"+error.getClass().getSimpleName();pause(1200);}}
    static void rotation(){while(true)try{String path=discover("NVTCapacitivePenP81c");if(path==null){pause(1800);continue;}try(FileInputStream input=new FileInputStream(path)){byte[] bytes=new byte[android.os.Process.is64Bit()?24:16];int angle=0;boolean dirty=false;long sent=0;while(read(input,bytes)){ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);long time=time(b,bytes.length);int type=b.getShort()&65535,code=b.getShort()&65535,value=b.getInt();if(type==3&&code==10){dirty=value>=-360&&value<=360;angle=value;}if(type==0&&code==0&&dirty&&time-sent>=12){dirty=false;PenOwner.Ticket ticket=policy.rotation(now());if(ticket!=null)events.offer(new Event(time,-10,angle,ticket));sent=time;}}}}catch(Exception error){pause(1800);}}
    static void sender(){while(true)try{Event first=events.take();ArrayList<Event> batch=new ArrayList<>();batch.add(first);events.drainTo(batch,31);StringBuilder text=new StringBuilder();int destination=policy.owner(now());for(Event event:batch)if(event.ticket.owner==destination&&policy.valid(event.ticket,now())&&now()-event.at<=PenOwner.LEASE_MS){if(text.length()>0)text.append(';');text.append(event.text);}if(text.length()>0)(destination==PenOwner.FOLIO?folio:focus).call("events",text.toString(),extra(destination==PenOwner.FOLIO));}catch(Exception ignored){}}
    static void poll(){boolean priority=false,active=false,folioGrab=false;try{Bundle b=folio.call("control",policy.owner(now())==PenOwner.FOLIO?mode:"observe",extra(false));if(b!=null){priority=b.getBoolean("priority",b.getBoolean("active",false));active=b.getBoolean("active",false);folioGrab=b.getBoolean("grab",false);folio.device(device);}}catch(Exception error){priority=true;}
        boolean ready=false,focusGrab=false;try{Bundle b=focus.call("control",!priority&&policy.owner(now())==PenOwner.FOCUSFLOW?mode:"observe",extra(priority));if(b!=null){ready=b.getBoolean("ready",false)&&!b.getBoolean("stop",false);focusGrab=b.getBoolean("grab",false);focus.device(device);}}catch(Exception ignored){}
        policy.update(priority,active,folioGrab,ready,focusGrab,now());grab(policy.grab(now()));
    }
    static void thread(String name,Runnable body){Thread worker=new Thread(body,name);worker.setDaemon(true);worker.start();}
    public static void main(String[] args)throws Exception{if(android.os.Process.myUid()!=2000)throw new SecurityException("ADB shell required");try{for(String name:new String[]{"ru.folio.pen.shared","ru.folio.notes.pen.singleton","ru.focusflow.pen.shell.singleton"})locks.add(new LocalServerSocket(name));}catch(IOException conflict){for(LocalServerSocket lock:locks)try{lock.close();}catch(IOException ignored){}System.out.println("Pen helper already running; no second input reader started.");return;}
        System.out.println("Shared Folio/FocusFlow pen bridge v2 · UID=2000");thread("pen-input",SharedPenBridge::input);thread("pen-events",SharedPenBridge::sender);thread("pen-rotation",SharedPenBridge::rotation);thread("pen-lease",()->{while(true){if(!policy.grab(now()))grab(false);pause(80);}});while(true){poll();pause(100);}
    }
    static void pause(long ms){try{Thread.sleep(ms);}catch(InterruptedException error){Thread.currentThread().interrupt();}}
}
