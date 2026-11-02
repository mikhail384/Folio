package ru.folio.notes;

import java.util.*;

public final class SqueezeGestureTests {
    static int assertions;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);assertions++;}
    static void expect(ArrayList<String> events,String... expected){check(events.equals(Arrays.asList(expected)),"expected "+Arrays.toString(expected)+", got "+events);}
    static void cases(){
        SqueezeGestures s=new SqueezeGestures();ArrayList<String> events=new ArrayList<>();
        s.down(1000,false,true,events::add);s.up(1040,events::add);expect(events);check(s.awaitingSingle(),"single waits for double window");s.singleTimeout(events::add);s.singleTimeout(events::add);expect(events,"squeeze");
        s.cancel();events.clear();s.down(2000,false,true,events::add);s.up(2030,events::add);s.down(2200,false,true,events::add);expect(events,"double_squeeze");s.singleTimeout(events::add);s.up(2800,events::add);s.holdTimeout(events::add);expect(events,"double_squeeze");check(!s.awaitingSingle(),"double consumed both squeezes including long second release");
        s.cancel();events.clear();s.down(1000,false,true,events::add);s.up(1020,events::add);s.down(1500,false,true,events::add);expect(events,"squeeze");s.up(1530,events::add);s.singleTimeout(events::add);expect(events,"squeeze","squeeze");
        s.cancel();events.clear();check(!s.up(50,events::add),"unmatched release ignored");s.down(100,true,true,events::add);check(!s.down(110,true,true,events::add),"duplicate press ignored");s.holdTimeout(events::add);s.holdTimeout(events::add);s.up(700,events::add);s.singleTimeout(events::add);expect(events,"hold");
        s.cancel();events.clear();s.down(100,true,true,events::add);s.up(601,events::add);expect(events,"hold");check(!s.awaitingSingle(),"hold recognized by event duration if timer delayed");
        s.cancel();events.clear();s.down(100,false,false,events::add);expect(events,"squeeze");s.up(140,events::add);s.singleTimeout(events::add);expect(events,"squeeze");
        s.cancel();events.clear();s.down(100,true,false,events::add);s.up(140,events::add);expect(events,"squeeze");
        s.cancel();events.clear();s.down(100,true,true,events::add);s.cancel();s.holdTimeout(events::add);s.up(1000,events::add);s.singleTimeout(events::add);expect(events);
        s.down(2000,false,true,events::add);s.up(2020,events::add);s.cancel();s.singleTimeout(events::add);expect(events);
        s.down(3000,false,true,events::add);s.up(3020,events::add);s.down(3100,false,true,events::add);s.up(3120,events::add);s.down(3200,false,true,events::add);s.up(3220,events::add);s.singleTimeout(events::add);expect(events,"double_squeeze","squeeze");
        s.cancel();events.clear();s.down(100,true,true,events::add);s.up(120,events::add);s.down(200,true,true,events::add);s.holdTimeout(events::add);s.up(900,events::add);expect(events,"double_squeeze");
    }
    static void randomized(){Random random=new Random(189350);for(int i=0;i<5000;i++){SqueezeGestures s=new SqueezeGestures();ArrayList<String> events=new ArrayList<>();long epoch=i%2==0?10000:1792500000000L;long start=epoch+random.nextInt(50),up=start+random.nextInt(120)+1,gap=random.nextInt(701);s.down(start,false,true,events::add);s.up(up,events::add);s.down(up+gap,false,true,events::add);if(gap<=350)expect(events,"double_squeeze");else expect(events,"squeeze");s.up(up+gap+random.nextInt(1000)+1,events::add);s.singleTimeout(events::add);if(gap<=350)expect(events,"double_squeeze");else expect(events,"squeeze","squeeze");check(!s.awaitingSingle()&&!s.awaitingHold(),"sequence fully resolved");}}
    public static void main(String[] args){cases();randomized();System.out.println("PASS "+assertions+" squeeze assertions: single/double exclusion, immediate second press, 350 ms window, delayed/long release, hold, duplicates, cancel, triples and 5000 relative/epoch timestamp pairs.");}
}
