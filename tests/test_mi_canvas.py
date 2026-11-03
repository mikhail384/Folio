"""Tests use synthetic data; no real notebook contents are bundled."""
from pathlib import Path
import struct
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'integrations/folio-handwriting'))
from mi_canvas import ink_stream,read_document

def u32(*v):return struct.pack('>'+'I'*len(v),*v)
def stroke(points,kind=1,tool=2,shape=False,deleted=False,old=False):
    info=bytearray(42 if old else 43);struct.pack_into('>H',info,40,int(shape))
    if not old:info[42]=int(deleted)
    data=b''.join(struct.pack('>ffffqfff',*p) for p in points)
    body=struct.pack('>HH',kind,tool)+info+data
    return u32(len(body)+4)+body
def ink(items,old=False):
    header=b'HWPENKITDATA'+struct.pack('>IIqII',24,20,0,1000,1600)
    base=u32(20,36,42 if old else 43,6,38);pagehead=68
    entries=[];at=pagehead+8+4*len(items)
    for item in items:entries.append(at);at+=len(item)
    return header+base+u32(12,1,pagehead)+u32(8+len(items)*4,len(items),*entries)+b''.join(items)
def container(data):
    item=b'ent_ink\0'+struct.pack('>HHI',0,0,len(data))+data
    item=u32(len(item)+4)+item
    rest=u32(4)+u32(8,0)+u32(12,1,40)+item
    return b'ENTPAGEINFO\0'+u32(4+len(rest))+rest

class MiCanvasTests(unittest.TestCase):
    def test_preserves_order_coordinates_pressure_and_time_and_skips_deleted(self):
        points=[(10.,20.,.2,.4,123456,1.,.7,30.),(12.,24.,.8,.5,123470,1.1,.8,40.)]
        result=ink_stream(ink([stroke(points),stroke(points,deleted=True)]))
        self.assertEqual(len(result['strokes']),1);self.assertEqual(result['skipped_deleted'],1)
        actual=result['strokes'][0]['points'];self.assertAlmostEqual(actual[0][2],.2,places=6)
        self.assertEqual(actual[1][4],123470);self.assertEqual(actual[1][:2],(12.,24.))
    def test_old_header_is_supported_and_container_is_validated(self):
        points=[(1.,2.,.3,.4,50,0.,1.,0.),(4.,5.,.6,.4,60,0.,1.,0.)]
        data=container(ink([stroke(points,old=True)],old=True))
        with tempfile.TemporaryDirectory() as folder:
            file=Path(folder)/'note.ent';file.write_bytes(data)
            self.assertEqual(len(read_document(file)[0]['strokes']),1)
            file.write_bytes(data[:-1])
            with self.assertRaises(ValueError):read_document(file)
    def test_corrupt_or_unsupported_data_is_rejected(self):
        data=bytearray(ink([stroke([(1.,2.,.3,.4,50,0.,1.,0.)])]))
        struct.pack_into('>I',data,16,19)
        with self.assertRaises(ValueError):ink_stream(data)
        with self.assertRaises(ValueError):ink_stream(b'HWPENKITDATA')

if __name__=='__main__':unittest.main()
