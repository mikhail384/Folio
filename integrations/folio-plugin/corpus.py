"""Private labeled pen trajectories for training, never generated ink.

Labels remain case-sensitive. Each unique example is immutable and written
atomically so different ways of writing the same word can coexist.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import re
import unicodedata
import uuid

MAX_STROKES=128
MAX_POINTS=16000


def prepare(text,strokes,kind='unknown',source='manual',document_id='',page_id=''):
    if not isinstance(text,str):raise ValueError('Подпись образца должна быть текстом')
    text=unicodedata.normalize('NFC',text)
    if not text.strip() or len(text)>64 or any(ord(c)<32 or ord(c)==127 for c in text):raise ValueError('Недопустимая подпись образца')
    if kind not in ('text','formula','unknown') or source not in ('manual','ocr-reviewed'):raise ValueError('Недопустимый тип образца')
    if any(not isinstance(v,str) or v and not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',v) for v in (document_id,page_id)):raise ValueError('Некорректный источник образца')
    if not isinstance(strokes,list) or not 1<=len(strokes)<=MAX_STROKES:raise ValueError('Выберите написанные пером штрихи')
    clean=[];count=0
    for stroke in strokes:
        if not isinstance(stroke,dict) or str(stroke.get('action','')).startswith('ai:'):raise ValueError('ИИ-штрихи нельзя использовать для обучения')
        if stroke.get('tool','pen') not in ('pen','fountain','brush','pencil') or stroke.get('cuts'):raise ValueError('Нужны нестёртые рукописные штрихи')
        values=stroke.get('points')
        if not isinstance(values,list) or len(values)<5 or len(values)%5:raise ValueError('Повреждены точки образца')
        count+=len(values)//5
        if count>MAX_POINTS:raise ValueError('Образец слишком большой')
        pts=[]
        for i in range(0,len(values),5):
            x,y,p,tilt,roll=values[i:i+5]
            if any(type(v) not in (int,float) or not math.isfinite(v) for v in (x,y,p,tilt,roll)) or abs(x)>1e7 or abs(y)>1e7 or p<0 or p>2:raise ValueError('Повреждены точки образца')
            pts.extend([x,y,p,tilt,roll])
        clean.append({'points':pts})
    return {'format':'folio-training-example-v1','text':text,'kind':kind,'source':source,'document_id':document_id,'page_id':page_id,'strokes':clean}


class Corpus:
    def __init__(self,root,profile):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,48}',profile):raise ValueError('Некорректный профиль')
        self.root=Path(root)/profile

    def add(self,text,strokes,kind='unknown',source='manual',document_id='',page_id=''):
        item=prepare(text,strokes,kind,source,document_id,page_id)
        data=json.dumps(item,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode('utf-8')
        identity=hashlib.sha256(data).hexdigest();self.root.mkdir(parents=True,exist_ok=True)
        dest=self.root/(identity+'.json')
        if dest.exists():
            if dest.read_bytes()!=data:raise ValueError('Повреждён сохранённый образец')
            return False
        temporary=self.root/('.'+uuid.uuid4().hex+'.tmp')
        try:
            fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
            with os.fdopen(fd,'wb') as out:
                out.write(data);out.flush();os.fsync(out.fileno())
            try:os.link(temporary,dest)
            except FileExistsError:
                if dest.read_bytes()!=data:raise ValueError('Повреждён сохранённый образец')
                return False
            return True
        finally:
            try:temporary.unlink()
            except FileNotFoundError:pass

    def examples(self):
        if not self.root.exists():return []
        result=[]
        for file in sorted(self.root.glob('*.json')):
            if not re.fullmatch(r'[0-9a-f]{64}\.json',file.name):raise ValueError('Повреждено хранилище образцов')
            data=file.read_bytes()
            if hashlib.sha256(data).hexdigest()!=file.stem:raise ValueError('Повреждён сохранённый образец')
            obj=json.loads(data.decode('utf-8'));clean=prepare(obj.get('text'),obj.get('strokes'),obj.get('kind'),obj.get('source'),obj.get('document_id'),obj.get('page_id'))
            if obj!=clean:raise ValueError('Неожиданные данные в образце')
            result.append(clean)
        return result
