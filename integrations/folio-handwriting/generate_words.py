"""Experimental whole-word inference; never enabled as Folio's default."""
import hashlib
import math
from pathlib import Path
import re
import unicodedata
import numpy as np
import torch
from word_model import WordInkVAE


def load_model(path,allow_generic=False):
    ck=torch.load(Path(path),map_location='cpu',weights_only=True)
    alphabet=ck.get('alphabet')
    if ck.get('format')!='folio-word-vae-research-v1' or not isinstance(alphabet,list) or len(alphabet)>1024 or len(set(alphabet))!=len(alphabet):raise ValueError('Некорректная модель')
    if not ck.get('personal') and not allow_generic:raise ValueError('Личная модель ещё не обучена')
    net=WordInkVAE(len(alphabet),points=int(ck['points']));net.load_state_dict(ck['model']);net.eval()
    style=ck['style'].detach().float()
    if style.shape!=(net.latent,) or not torch.isfinite(style).all():raise ValueError('Повреждён стиль')
    return net,{c:i+1 for i,c in enumerate(alphabet)},style


def units(text,mode):
    if mode=='text':return re.findall(r'\s+|\S+',text)
    if mode=='formula':return list(text)
    raise ValueError('Неизвестный режим письма')


def split_paths(values):
    if not np.isfinite(values).all():raise ValueError('Модель создала некорректный штрих')
    xy=values[:,:2];prob=1/(1+np.exp(-np.clip(values[:,3],-30,30)))
    paths=[];start=0
    for i in range(2,len(xy)-2):
        peak=prob[i]>.6 and prob[i]>=prob[i-1] and prob[i]>=prob[i+1]
        jump=float(np.linalg.norm(xy[i+1]-xy[i]))>.27
        if (peak or jump) and i-start>=2:
            paths.append(values[start:i+1]);start=i+1
    if len(xy)-start>=2:paths.append(values[start:])
    if not paths:raise ValueError('Не удалось построить штрихи')
    return paths


def generate(model,text,x,y,height=28,width=2.2,color='#28344b',max_width=900,mode='text',operation_id=''):
    net,alphabet,style=model;text=unicodedata.normalize('NFC',text)
    if not text or len(text)>500 or not isinstance(operation_id,str) or not operation_id:raise ValueError('Нужен текст и идентификатор операции')
    if not all(math.isfinite(v) for v in (x,y,height,width,max_width)) or not 6<=height<=240 or width<=0 or max_width<height:raise ValueError('Некорректные размеры')
    missing=sorted({c for c in text if not c.isspace() and c not in alphabet})
    if missing:raise ValueError('Модель ещё не знает символы: '+' '.join(missing))
    rng=np.random.default_rng(int.from_bytes(hashlib.sha256(operation_id.encode()).digest()[:8],'little'))
    origin=x;result=[];total=0
    with torch.inference_mode():
        for unit in units(text,mode):
            if unit.isspace():
                for c in unit:
                    if c=='\n':x=origin;y+=height*1.65
                    else:x+=height*.35
                continue
            if len(unit)>64:raise ValueError('Разделите слишком длинное слово')
            chars=torch.tensor([[alphabet[c] for c in unit]],dtype=torch.long);modes=torch.tensor([0 if mode=='text' else 1])
            z=style+torch.from_numpy(rng.normal(0,.035,style.numel()).astype('float32'))
            values,ratio=net.decode(z[None],chars,modes);values=values[0].numpy();factor=float(torch.exp(ratio[0]).clamp(.1,30))
            size=height*.68 if mode=='formula' and unit in '⁰¹²³⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉' else height
            advance=max(size*.18,factor*size)+size*.12
            if advance>max_width:raise ValueError('Слово шире строки')
            if x>origin and x+advance>origin+max_width:x=origin;y+=height*1.65
            top=y+(height-size if mode=='formula' and unit in '₀₁₂₃₄₅₆₇₈₉' else 0)
            for path in split_paths(values):
                pts=[[round(x+float(a)*factor*size,3),round(top+float(b)*size,3),round(max(.021,min(2,float(p)*2)),4)] for a,b,p,_ in path]
                total+=len(pts);result.append({'tool':'fountain','color':color,'width':width,'points':pts})
            x+=advance
            if len(result)>2000 or total>100000:raise ValueError('Разделите надпись на части')
    if not result:raise ValueError('Пустая надпись')
    if any(abs(p[0])>1e7 or abs(p[1])>1e7 or not all(math.isfinite(v) for v in p) for stroke in result for p in stroke['points']):raise ValueError('Надпись выходит за пределы холста')
    return result
