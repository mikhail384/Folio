"""Train a generic prototype on public examples. Private calibration is separate."""
import argparse
import json
from pathlib import Path
import random

import numpy as np
import torch
from model import InkVAE, losses


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('dataset');ap.add_argument('output')
    ap.add_argument('--epochs',type=int,default=120);ap.add_argument('--seed',type=int,default=923)
    args=ap.parse_args()
    random.seed(args.seed);np.random.seed(args.seed);torch.manual_seed(args.seed)
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device=='cuda':torch.cuda.set_per_process_memory_fraction(.35)
    source=Path(args.dataset);meta=json.loads(source.with_suffix('.json').read_text(encoding='utf-8'))
    raw=np.load(source,allow_pickle=False);points=torch.from_numpy(raw['strokes']).to(device);labels=torch.from_numpy(raw['labels']).to(device);writers=torch.from_numpy(raw['writers']).to(device)
    train=torch.nonzero(writers<10,as_tuple=False).flatten();val=torch.nonzero(writers>=10,as_tuple=False).flatten()
    net=InkVAE(len(meta['labels']),points=meta['points'],writers=int(writers.max())+1).to(device)
    optimizer=torch.optim.AdamW(net.parameters(),lr=.0007,weight_decay=.0001)
    best=float('inf');out=Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
    print('device',device,'training glyphs',len(train),'held-out glyphs',len(val),flush=True)
    for epoch in range(1,args.epochs+1):
        net.train();order=train[torch.randperm(len(train),device=device)];total=0.
        for ids in order.split(96):
            prediction,mean,logvar=net(points[ids],labels[ids]);loss,_,_=losses(net,prediction,points[ids],mean,logvar,writers[ids])
            optimizer.zero_grad(set_to_none=True);loss.backward();torch.nn.utils.clip_grad_norm_(net.parameters(),2);optimizer.step();total+=float(loss)*len(ids)
        net.eval()
        with torch.inference_mode():
            pred,mean,logvar=net(points[val],labels[val],stochastic=False);loss,xy,end=losses(net,pred,points[val],mean,logvar,writers[val])
            score=float(xy)
        if score<best:
            best=score;torch.save({'format':'folio-vector-vae-experimental-v1','labels':meta['labels'],'points':meta['points'],'writers':int(writers.max())+1,'model':net.state_dict(),'epoch':epoch,'val_xy':score,'source':meta['source']},out)
        if epoch==1 or epoch%10==0 or epoch==args.epochs:
            print('epoch',epoch,'train',round(total/len(train),4),'held_out_xy',round(score,5),'best',round(best,5),flush=True)
    print('checkpoint',out,'best held-out xy error',round(best,6),flush=True)


if __name__=='__main__':main()
