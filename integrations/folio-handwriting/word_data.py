"""Prepare full-word trajectories for an experimental generative model.

The public dataset has no recorded pen-up or pressure. Pen lifts are inferred
from timing/distance and its pressure is masked during training. Private Folio
examples retain their actual stroke boundaries and pressure. Never mix
unreviewed OCR labels or generated ink into this dataset.
"""
import argparse
import json
import math
from pathlib import Path
import re
import sys
import zipfile

import numpy as np
from dataset import SOURCE, inferred_paths, read_numbers

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'folio-plugin'))
from corpus import Corpus

POINTS=128


def trajectory(paths,flip=False,count=POINTS):
    paths=[np.asarray(p,dtype=np.float32) for p in paths if len(p)]
    if not paths or len(paths)>count//2:raise ValueError('too many strokes')
    values=np.concatenate(paths)
    if values.ndim!=2 or values.shape[1]!=3 or len(values)<2 or not np.isfinite(values).all():raise ValueError('invalid points')
    lo=values[:,:2].min(axis=0);hi=values[:,:2].max(axis=0);width,height=hi-lo
    if width<.1 or height<1e-3 or not .02<=float(width/height)<=60:raise ValueError('invalid bounds')
    normalized=[];lengths=[]
    for path in paths:
        p=path.copy();p[:,0]=(p[:,0]-lo[0])/width;p[:,1]=((hi[1]-p[:,1]) if flip else (p[:,1]-lo[1]))/height
        p[:,2]=np.clip(p[:,2],.021,2)/2;normalized.append(p)
        lengths.append(max(float(np.linalg.norm(np.diff(p[:,:2],axis=0),axis=1).sum()),.025))
    raw=(count-2*len(paths))*np.asarray(lengths)/sum(lengths);extra=np.floor(raw).astype(int)
    for i in np.argsort(-(raw-extra))[:count-2*len(paths)-int(extra.sum())]:extra[i]+=1
    result=[]
    for path,n in zip(normalized,extra+2):
        dist=np.concatenate([[0.],np.cumsum(np.linalg.norm(np.diff(path[:,:2],axis=0),axis=1))])
        if dist[-1]<1e-8:sample=np.repeat(path[:1],int(n),axis=0)
        else:
            grid=np.linspace(0,dist[-1],int(n));sample=np.column_stack([np.interp(grid,dist,path[:,axis]) for axis in range(3)])
        end=np.zeros((int(n),1),dtype=np.float32);end[-1]=1
        result.extend(np.column_stack([sample,end]))
    return np.asarray(result,dtype=np.float32),math.log(float(width/height))


def public(archive):
    result=[]
    with zipfile.ZipFile(archive) as z:
        names=set(z.namelist())
        for name in sorted(names):
            m=re.fullmatch(r'w_(\d+)_\d+/(?:extra/)?([^/]+)',name)
            if not m or name+'_times' not in names:continue
            writer,label=m.groups()
            if not 1<=len(label)<=32:continue
            try:
                paths=inferred_paths(read_numbers(z,name),read_numbers(z,name+'_times'))
                paths=[[[x,y,.5] for x,y in path] for path in paths]
                seq,ratio=trajectory(paths,flip=True)
                result.append((label,'text',int(writer),False,False,seq,ratio))
            except (ValueError,IndexError,UnicodeError):continue
    return result


def private(root,profile):
    result=[]
    for item in Corpus(root,profile).examples():
        if item['kind'] not in ('text','formula'):continue
        paths=[]
        for stroke in item['strokes']:
            p=stroke['points'];paths.append([[p[i],p[i+1],p[i+2]] for i in range(0,len(p),5)])
        try:
            seq,ratio=trajectory(paths)
            # Mi Canvas files may contain marker strokes and do not carry an
            # unambiguous pressure-quality flag in this corpus format. Their
            # trajectory geometry is useful, but not a pressure target.
            pressure_known=not item['document_id'].startswith('mi_')
            result.append((item['text'],item['kind'],0,True,pressure_known,seq,ratio))
        except ValueError:continue
    return result


def main():
    p=argparse.ArgumentParser();p.add_argument('archive');p.add_argument('output');p.add_argument('--corpus');p.add_argument('--profile',default='my-handwriting');a=p.parse_args()
    rows=public(a.archive);own=private(a.corpus,a.profile) if a.corpus else [];rows+=own
    if not rows:raise ValueError('No training examples')
    alphabet=sorted(set(''.join(row[0] for row in rows)))
    dest=Path(a.output);dest.parent.mkdir(parents=True,exist_ok=True)
    np.savez_compressed(dest,texts=np.asarray([r[0] for r in rows]),modes=np.asarray([0 if r[1]=='text' else 1 for r in rows]),
        writers=np.asarray([r[2] for r in rows]),personal=np.asarray([r[3] for r in rows]),pressure_mask=np.asarray([r[4] for r in rows]),
        strokes=np.stack([r[5] for r in rows]),log_ratio=np.asarray([r[6] for r in rows],dtype=np.float32))
    dest.with_suffix('.json').write_text(json.dumps({'format':'folio-word-trajectories-v1','source':SOURCE,'alphabet':alphabet,
        'public_examples':len(rows)-len(own),'personal_examples':len(own),'pen_lifts':'public inferred; Folio recorded',
        'pressure':'public and Mi Canvas masked; Folio recorded','points':POINTS},ensure_ascii=False,indent=2),encoding='utf-8')
    print('Prepared public',len(rows)-len(own),'private reviewed',len(own),'case-sensitive symbols',len(alphabet))


if __name__=='__main__':main()
