"""Fit a private writer style to checked samples; retain a held-out test set."""
import argparse
from copy import deepcopy
from pathlib import Path
import random
import numpy as np
import torch
from torch.nn import functional as F
from model import InkVAE


def fidelity(pred,target):
    xy=F.mse_loss(pred[:,:,:2],target[:,:,:2])
    delta=F.mse_loss(pred[:,1:,:2]-pred[:,:-1,:2],target[:,1:,:2]-target[:,:-1,:2])
    lifts=F.binary_cross_entropy_with_logits(pred[:,:,2],target[:,:,2],pos_weight=target.new_tensor(15.))
    return xy+.35*delta+.025*lifts,xy


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('base');parser.add_argument('public');parser.add_argument('personal');parser.add_argument('output')
    parser.add_argument('--steps',type=int,default=1400);parser.add_argument('--seed',type=int,default=1301);parser.add_argument('--fit-all',action='store_true')
    args=parser.parse_args();random.seed(args.seed);np.random.seed(args.seed);torch.manual_seed(args.seed)
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device=='cuda':torch.cuda.set_per_process_memory_fraction(.35)
    ck=torch.load(args.base,map_location='cpu',weights_only=True)
    net=InkVAE(len(ck['labels']),points=ck['points'],writers=ck['writers']).to(device);net.load_state_dict(ck['model'])
    teacher=deepcopy(net).eval()
    for p in teacher.parameters():p.requires_grad_(False)
    for p in net.parameters():p.requires_grad_(False)
    for p in net.decoder.parameters():p.requires_grad_(True)
    public=np.load(args.public,allow_pickle=False);personal=np.load(args.personal,allow_pickle=False)
    seq=torch.from_numpy(personal['strokes']).to(device);label=torch.from_numpy(personal['labels']).to(device)
    if len(seq)<12:raise ValueError('At least 12 reviewed samples are required for an experimental fit')
    pseq=torch.from_numpy(public['strokes']).to(device);plabel=torch.from_numpy(public['labels']).to(device)
    unique=np.unique(personal['labels']);rng=np.random.default_rng(args.seed);rng.shuffle(unique)
    held=set() if args.fit_all else set(map(int,unique[:max(3,len(unique)//5)]));train=torch.tensor([i for i,v in enumerate(personal['labels']) if int(v) not in held],device=device);val=train if args.fit_all else torch.tensor([i for i,v in enumerate(personal['labels']) if int(v) in held],device=device)
    with torch.no_grad():initial=net.encode(seq[train],label[train])[0].mean(0)
    style=torch.nn.Parameter(initial.clone());optimizer=torch.optim.AdamW([style,*net.decoder.parameters()],lr=.0002,weight_decay=.0001)
    best=float('inf');out=Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
    print('device',device,'reviewed train examples',len(train),'held-out examples',0 if args.fit_all else len(val),flush=True)
    for step in range(1,args.steps+1):
        chosen=train[torch.randint(len(train),(min(24,len(train)),),device=device)]
        generic=torch.randint(len(pseq),(48,),device=device)
        personal_z=style[None].expand(len(chosen),-1)+torch.randn(len(chosen),len(style),device=device)*.035
        prediction=net.decode(personal_z,label[chosen]);fit,_=fidelity(prediction,seq[chosen])
        with torch.no_grad():
            z=teacher.encode(pseq[generic],plabel[generic])[0];expected=teacher.decode(z,plabel[generic])
        retain=F.mse_loss(net.decode(z,plabel[generic]),expected)
        loss=fit+.2*retain+.0005*F.mse_loss(style,initial)
        optimizer.zero_grad(set_to_none=True);loss.backward();torch.nn.utils.clip_grad_norm_([style,*net.decoder.parameters()],2);optimizer.step()
        if step==1 or step%50==0 or step==args.steps:
            with torch.no_grad():
                guess=net.decode(style[None].expand(len(val),-1),label[val]);_,error=fidelity(guess,seq[val]);score=float(error)
            if score<best:
                best=score
                torch.save({'format':'folio-personal-vector-experimental-v1','labels':ck['labels'],'points':ck['points'],'writers':ck['writers'],'model':net.state_dict(),'style':style.detach().cpu(),'pressure':float(np.median(personal['pressure'])),'source':ck['source'],'validation_xy':score,'training_examples':len(train),'held_out_examples':0 if args.fit_all else len(val)},out)
            if step==1 or step%200==0 or step==args.steps:print('step',step,'train_xy' if args.fit_all else 'held_out_xy',round(score,5),'best',round(best,5),flush=True)
    print('Private prototype saved;',('training' if args.fit_all else 'held-out'),'XY error',round(best,6),flush=True)


if __name__=='__main__':main()
