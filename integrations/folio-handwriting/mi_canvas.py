"""Read-only research parser for locally copied Mi Canvas 3.8.3 ink.

Only the observed ENTPAGEINFO/HWPENKITDATA v20 structure is supported. Unknown
objects and deleted strokes are excluded; no bytes are ever written to a tablet.
Automatic OCR labels from Mi Canvas must be reviewed before use in training.
"""
import math
from pathlib import Path
import struct

MAX_BYTES=100*1024*1024


class Reader:
    def __init__(self,data):self.data=memoryview(data);self.size=len(data)
    def take(self,at,n):
        if not isinstance(at,int) or not isinstance(n,int) or at<0 or n<0 or at+n>self.size:raise ValueError('truncated Mi Canvas file')
        return self.data[at:at+n]
    def unpack(self,fmt,at):
        n=struct.calcsize(fmt);return struct.unpack(fmt,self.take(at,n))
    def u32(self,at):return self.unpack('>I',at)[0]
    def block(self,at):
        size=self.u32(at)
        if size<4 or size>self.size-at:raise ValueError('invalid Mi Canvas block')
        return at+4,at+size
    def table(self,at):
        start,end=self.block(at);count=self.u32(start)
        if count>100000 or start+4+count*4>end:raise ValueError('invalid offset table')
        return [self.u32(start+4+i*4) for i in range(count)],end


def ink_stream(raw):
    r=Reader(raw)
    if bytes(r.take(0,12))!=b'HWPENKITDATA':raise ValueError('unknown ink format')
    _,at=r.block(12);version=r.u32(16)
    if version!=20:raise ValueError('unsupported ink version')
    base,end=r.block(at);point_size,stroke_size,_,shape_size=r.unpack('>IIII',base);at=end
    if point_size!=36 or stroke_size not in (42,43) or shape_size>4096:raise ValueError('unsupported ink layout')
    pages,at=r.table(at)
    if len(pages)!=1:raise ValueError('unsupported page table')
    at=max(at,pages[0]);offsets,_=r.table(at)
    strokes=[];other=deleted=shapes=0
    for off in offsets:
        start,end=r.block(off);kind,tool=r.unpack('>HH',start)
        if kind!=1:other+=1;continue
        info=start+4;r.take(info,stroke_size);shape=r.unpack('>H',info+40)[0];is_deleted=stroke_size>42 and bool(r.unpack('>B',info+42)[0])
        if shape:shapes+=1;continue
        if is_deleted:deleted+=1;continue
        pos=info+stroke_size
        if (end-pos)%point_size:raise ValueError('invalid point data size')
        points=[]
        while pos<end:
            x,y,f13,f14,t,f17,f18,roll=r.unpack('>ffffqfff',pos);pos+=point_size
            if not all(math.isfinite(v) and abs(v)<1e8 for v in (x,y,f13,f14,f17,f18,roll)):raise ValueError('non-finite ink')
            points.append((x,y,f13,f14,t,f17,f18,roll))
        if points:strokes.append({'tool':tool,'points':points})
    return {'version':version,'strokes':strokes,'skipped_other':other,'skipped_deleted':deleted,'skipped_shapes':shapes}


def read_document(path):
    path=Path(path)
    if path.stat().st_size>MAX_BYTES:raise ValueError('Mi Canvas file too large')
    raw=path.read_bytes();r=Reader(raw)
    if bytes(r.take(0,12))!=b'ENTPAGEINFO\x00' or r.u32(12)+12!=len(raw):raise ValueError('unknown Mi Canvas container')
    _,at=r.block(16);entries,at=r.table(at)
    for offset in entries:
        _,at=r.block(max(at,offset))
    pages,at=r.table(at)
    if len(pages)>1024:raise ValueError('too many ink layers')
    streams=[]
    for offset in pages:
        start,end=r.block(max(at,offset));kind=bytes(r.take(start,8)).rstrip(b'\0');payload_size=r.u32(start+12)
        if start+16+payload_size>end:raise ValueError('invalid ink payload')
        if kind==b'ent_ink' and payload_size:streams.append(ink_stream(bytes(r.take(start+16,payload_size))))
        at=end
    return streams
