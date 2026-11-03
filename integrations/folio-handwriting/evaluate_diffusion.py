"""Render a private visual comparison on transcripts excluded from training."""
import argparse
import json
import os
from pathlib import Path
import sys
import time


def main():
    p=argparse.ArgumentParser();p.add_argument('dataset');p.add_argument('output');p.add_argument('checkpoints',nargs='+')
    p.add_argument('--reference',default='research/handwriting-diffusion-reference');p.add_argument('--count',type=int,default=4)
    a=p.parse_args();dataset=Path(a.dataset).resolve();reference=Path(a.reference).resolve()
    os.environ['PROJ']=str(dataset.parent.parent);os.environ['HW_DS']=dataset.name
    os.environ['HW_FONT']=os.environ.get('HW_FONT',r'C:\Windows\Fonts\arial.ttf')
    sys.path.insert(0,str(reference/'src'))
    import numpy as np
    from PIL import Image,ImageDraw,ImageFont
    import torch
    import train as ref
    torch.set_num_threads(4);torch.backends.cuda.matmul.allow_tf32=True
    rows=json.loads((dataset/'val.json').read_text(encoding='utf8'))
    distinct=[];seen=set()
    for row in rows:
        if row['text'].casefold().strip('.,:;!?') not in seen:
            distinct.append(row);seen.add(row['text'].casefold().strip('.,:;!?'))
    rows=distinct[:a.count]
    if len(rows)<3:raise ValueError('Not enough distinct held-out words')
    generated=[]
    for checkpoint in a.checkpoints:
        saved=torch.load(checkpoint,map_location='cpu',weights_only=True)
        if saved.get('chars')!=ref.chars:raise ValueError('Mismatched vocabulary')
        model=ref.UNet().to(ref.dev);model.load_state_dict({k:v.float() for k,v in saved['e'].items()});model.eval()
        torch.manual_seed(20261103)
        start=time.time();images=ref.sample(model,[row['text'] for row in rows],2.0)
        generated.append(images[:,0]);print('Evaluated',Path(checkpoint).name,'seconds',round(time.time()-start),flush=True)
        del model
    width=220+(1+len(generated))*600;height=70+len(rows)*155
    canvas=Image.new('RGB',(width,height),'white');draw=ImageDraw.Draw(canvas)
    font=ImageFont.truetype(r'C:\Windows\Fonts\arial.ttf',18)
    headers=['Исходный образец']+[Path(c).stem for c in a.checkpoints]
    for col,header in enumerate(headers):draw.text((220+col*600,12),header,font=font,fill='#1a2535')
    for row_index,row in enumerate(rows):
        top=70+row_index*155;draw.text((10,top+20),row['text'],font=font,fill='#1a2535')
        original=Image.open(dataset/'img'/row['img']).convert('L')
        refimage,_=ref.canvas(original)
        pictures=[np.asarray(refimage)]+[g[row_index] for g in generated]
        for col,picture in enumerate(pictures):
            image=Image.fromarray(picture).resize((576,128),Image.Resampling.NEAREST).convert('RGB')
            canvas.paste(image,(220+col*600,top))
    Path(a.output).parent.mkdir(parents=True,exist_ok=True);canvas.save(a.output)
    print('Private comparison saved; generated images require human quality review.',flush=True)


if __name__=='__main__':main()
