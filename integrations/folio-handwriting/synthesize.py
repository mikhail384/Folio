"""Experimental CPU vector synthesis. It does not read or replay source paths."""
import hashlib
import math
from pathlib import Path
import unicodedata

import numpy as np
import torch
from model import InkVAE

MIN_PRESSURE=.021


def load_model(path):
    checkpoint=torch.load(Path(path),map_location='cpu',weights_only=True)
    if checkpoint.get('format')!='folio-personal-vector-experimental-v1':raise ValueError('Неизвестный формат модели')
    labels=checkpoint.get('labels')
    if not isinstance(labels,list) or len(labels)>512 or len(labels)!=len(set(labels)):raise ValueError('Повреждён словарь модели')
    net=InkVAE(len(labels),points=checkpoint['points'],writers=checkpoint['writers'])
    net.load_state_dict(checkpoint['model']);net.eval()
    style=checkpoint['style'].detach().float()
    if style.shape!=(net.latent,) or not torch.isfinite(style).all():raise ValueError('Повреждён стиль')
    return net,labels,style,float(checkpoint.get('pressure',.5))


def smooth(points):
    if len(points)<3:return points
    out=points.copy()
    for i in range(1,len(points)-1):
        a=points[i]-points[i-1];b=points[i+1]-points[i]
        # Preserve strong corners instead of rounding their topology away.
        denom=max(1e-8,float(np.linalg.norm(a)*np.linalg.norm(b)))
        if float(np.dot(a,b))/denom>-.15:out[i]=.2*points[i-1]+.6*points[i]+.2*points[i+1]
    return out


def segments(value):
    xy=value[:,:2];logits=value[:,2]
    if not np.isfinite(value).all():raise ValueError('Модель создала некорректный штрих')
    probability=1/(1+np.exp(-np.clip(logits,-30,30)))
    paths=[];start=0;last_lift=-10
    for i in range(2,len(xy)-2):
        peak=probability[i]>.6 and probability[i]>=probability[i-1] and probability[i]>=probability[i+1]
        jump=float(np.linalg.norm(xy[i+1]-xy[i]))>.32
        if (peak or jump) and i-start>=2 and i-last_lift>=3:
            paths.append(smooth(xy[start:i+1]));start=i+1;last_lift=i
    if len(xy)-start>=2:paths.append(smooth(xy[start:]))
    if not paths:raise ValueError('Модель не построила букву')
    return paths


def connector(a,b,height):
    dx=b[0]-a[0];dy=b[1]-a[1]
    # A short cubic path, generated afresh between adjacent characters.
    c1=(a[0]+dx*.32,a[1]+min(height*.1,abs(dx)*.15))
    c2=(b[0]-dx*.32,b[1]+min(height*.08,abs(dx)*.12))
    result=[]
    for i in range(9):
        t=i/8;u=1-t
        result.append([u**3*a[0]+3*u*u*t*c1[0]+3*u*t*t*c2[0]+t**3*b[0],u**3*a[1]+3*u*u*t*c1[1]+3*u*t*t*c2[1]+t**3*b[1]])
    return result


def synthesize(model,text,x,y,height=28,width=2.2,color='#28344b',max_width=900,mode='text',operation_id=''):
    net,labels,style,pressure=model;text=unicodedata.normalize('NFC',text)
    if not text or len(text)>500 or mode not in ('text','formula'):raise ValueError('Некорректный текст или режим')
    if not all(math.isfinite(v) for v in (x,y,height,width,max_width)) or not 6<=height<=240 or width<=0 or max_width<height:raise ValueError('Некорректный размер надписи')
    if not isinstance(operation_id,str) or not operation_id:raise ValueError('Нужен идентификатор операции для безопасного повтора')
    alphabet={c:i for i,c in enumerate(labels)}
    unsupported=sorted({c for c in text if not c.isspace() and c not in alphabet})
    if unsupported:raise ValueError('Модель ещё не знает символы: '+' '.join(unsupported))
    seed=int.from_bytes(hashlib.sha256(operation_id.encode('utf-8')).digest()[:8],'little')
    rng=np.random.default_rng(seed);out=[];origin=x;previous=None;count=0
    with torch.inference_mode():
        for char in text:
            if char=='\n':x=origin;y+=height*1.65;previous=None;continue
            if char.isspace():x+=height*.38;previous=None;continue
            latent=style+torch.from_numpy(rng.normal(0,.035,style.numel()).astype('float32'))
            value=net.decode(latent[None],torch.tensor([alphabet[char]],dtype=torch.long))[0].numpy()
            paths=segments(value);points=np.concatenate(paths);minx,miny=points.min(axis=0);maxx,maxy=points.max(axis=0)
            glyph_h=float(maxy-miny)
            if glyph_h<.08 or glyph_h>5 or float(maxx-minx)>5:raise ValueError('Не удалось построить разборчивую букву')
            cap=char.isupper() or char.isdigit();size=height if cap else height*.73
            glyph_w=max(.18*height,float(maxx-minx)/glyph_h*size);advance=glyph_w+height*.08
            if advance>max_width:raise ValueError('Символ шире строки')
            if x>origin and x+advance>origin+max_width:x=origin;y+=height*1.65;previous=None
            top=y+height-size
            if char in '\u0440\u0443\u0444\u0434\u0446\u0449':top+=height*.14
            world=[]
            for path in paths:
                world.append([[x+(float(a)-float(minx))/glyph_h*size,top+(float(b)-float(miny))/glyph_h*size] for a,b in path])
            if mode=='text' and previous is not None:
                candidates=[p[0] for p in world if p]+[p[-1] for p in world if p]
                first=min(candidates,key=lambda p:p[0]+abs(p[1]-(y+height))*.8)
                if 0<=first[0]-previous[0]<=height*.65:
                    stroke=connector(previous,first,height)
                    out.append({'tool':'fountain','color':color,'width':width,'points':[[round(a,3),round(b,3),max(MIN_PRESSURE,min(2,pressure))] for a,b in stroke]});count+=len(stroke)
            for path in world:
                if len(path)<2:continue
                p=max(MIN_PRESSURE,min(2,pressure+float(rng.normal(0,.008))))
                out.append({'tool':'fountain','color':color,'width':width,'points':[[round(a,3),round(b,3),round(p,4)] for a,b in path]});count+=len(path)
            endpoints=[p[0] for p in world if p]+[p[-1] for p in world if p]
            previous=max(endpoints,key=lambda p:p[0]-abs(p[1]-(y+height))*.8) if mode=='text' else None
            x+=advance
            if len(out)>2000 or count>100000:raise ValueError('Разделите надпись на части')
    if not out:raise ValueError('Пустая надпись')
    if any(abs(p[0])>1e7 or abs(p[1])>1e7 or not all(math.isfinite(v) for v in p) for stroke in out for p in stroke['points']):raise ValueError('Надпись выходит за пределы холста')
    return out
