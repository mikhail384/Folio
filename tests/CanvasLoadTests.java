package ru.folio.notes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Deterministic 100k-stroke geometry, lasso and streaming-save stress test. */
public final class CanvasLoadTests {
    static int assertions;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);assertions++;}
    static final class CountingOutput extends OutputStream {long bytes;public void write(int value){bytes++;}public void write(byte[] data,int offset,int length){bytes+=length;}}
    static Ink.Pt pt(float x,float y){return new Ink.Pt(x,y,.6f,.2f,.4f,.7f);}
    static void lassoCases(){Ink.Page page=new Ink.Page();page.infinite=true;Ink.Layer locked=new Ink.Layer();locked.id="locked";locked.locked=true;page.layers.add(locked);Ink.Stroke crossing=new Ink.Stroke();crossing.points.add(pt(-20,50));crossing.points.add(pt(120,50));Ink.Stroke protectedLine=new Ink.Stroke();protectedLine.layer="locked";protectedLine.points.add(pt(0,50));protectedLine.points.add(pt(100,50));page.strokes.add(crossing);page.strokes.add(protectedLine);SpatialIndex index=new SpatialIndex();index.add(crossing,0);index.add(protectedLine,1);List<Ink.Pt> box=Arrays.asList(pt(40,40),pt(60,40),pt(60,60),pt(40,60));HashSet<Ink.Stroke> result=LassoSelection.select(index,page,box);check(result.contains(crossing),"lasso captures a stroke crossing between sampled endpoints");check(!result.contains(protectedLine),"lasso excludes locked-layer strokes");}
    public static void main(String[] args)throws Exception{
        lassoCases();final int count=100000;Ink.Doc doc=new Ink.Doc();Ink.Page page=doc.page();page.infinite=true;page.pattern="blank";page.strokes.ensureCapacity(count);SpatialIndex index=new SpatialIndex();long buildStart=System.nanoTime();
        for(int i=0;i<count;i++){float x=(i%500)*10,y=(i/500)*10;Ink.Stroke stroke=new Ink.Stroke();stroke.action="group-"+(i/4);stroke.width=2;stroke.points.add(pt(x,y));stroke.points.add(pt(x+2,y+1));stroke.points.add(pt(x+4,y+2));stroke.points.add(pt(x+6,y+3));page.strokes.add(stroke);index.add(stroke,i);}
        double buildMs=(System.nanoTime()-buildStart)/1e6;check(index.entries.size()==count,"spatial index stores all 100,000 strokes");
        long queryStart=System.nanoTime();ArrayList<Ink.Stroke> full=index.query(0,0,5000,2000);double fullQueryMs=(System.nanoTime()-queryStart)/1e6;check(full.size()==count,"full-page query returns every stroke in document");
        queryStart=System.nanoTime();ArrayList<Ink.Stroke> local=index.query(800,400,1300,900);double localQueryMs=(System.nanoTime()-queryStart)/1e6;check(local.size()>0&&local.size()<5000,"viewport query stays local on a 100k page");int localCandidates=index.lastCandidates;
        List<Ink.Pt> outline=Arrays.asList(pt(800,400),pt(1300,400),pt(1300,900),pt(800,900));queryStart=System.nanoTime();HashSet<Ink.Stroke> selected=LassoSelection.select(index,page,outline);double lassoMs=(System.nanoTime()-queryStart)/1e6;check(selected.size()>0&&selected.size()<5000,"lasso selects local strokes without scanning full page");
        CountingOutput sink=new CountingOutput();long exportStart=System.nanoTime();OutputStreamWriter writer=new OutputStreamWriter(sink,StandardCharsets.UTF_8);InkJsonWriter.write(doc,writer);writer.flush();double exportMs=(System.nanoTime()-exportStart)/1e6;check(sink.bytes>1000000,"streaming save emits complete large document");
        CountingOutput archive=new CountingOutput();long archiveStart=System.nanoTime();try(ZipOutputStream zip=new ZipOutputStream(archive)){zip.setLevel(Deflater.BEST_SPEED);zip.putNextEntry(new ZipEntry("document.json"));OutputStreamWriter archiveWriter=new OutputStreamWriter(zip,StandardCharsets.UTF_8);InkJsonWriter.write(doc,archiveWriter);archiveWriter.flush();zip.closeEntry();}double archiveMs=(System.nanoTime()-archiveStart)/1e6;check(archive.bytes>0&&archive.bytes<sink.bytes,"streamed .folio archive compresses the complete JSON entry");
        System.out.printf(Locale.ROOT,"PASS %d assertions; %d strokes / %d points; index %.1f ms, full query %.1f ms, viewport %d strokes / %d candidates in %.1f ms, lasso %d strokes in %.1f ms, streaming JSON %d bytes in %.1f ms, streamed .folio %d bytes in %.1f ms (desktop JVM, not tablet rendering).%n",assertions,count,count*4,buildMs,fullQueryMs,local.size(),localCandidates,localQueryMs,selected.size(),lassoMs,sink.bytes,exportMs,archive.bytes,archiveMs);
    }
}
