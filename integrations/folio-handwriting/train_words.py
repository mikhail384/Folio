"""Train a whole-word prototype with held-out writers/words; never deploy blindly."""
import argparse
import json
from pathlib import Path
import random
import numpy as np
import torch
from word_model import WordInkVAE,loss


def tensors(data,alphabet,device):
    ids={c:i+1 for i,c in enumerate(alphabet)};texts=data['texts'];longest=max(map(len,texts))
    encoded=np.zeros((len(texts),longest),dtype=np.int64)
    for row,text in enumerate(texts):encoded[row,:len(text)]=[ids[c] for c in text]
    return tuple(torch.from_numpy(x).to(device) for x in (data['strokes'],encoded,data['modes'],data['log_ratio'],data['pressure_mask']))


def main():
    p=argparse.ArgumentParser();p.add_argument('dataset');p.add_argument('output');p.add_argument('--epochs',type=int,default=80);p.add_argument('--seed',type=int,default=7401);p.add_argument('--base');p.add_argument('--personal',action='store_true');a=p.parse_args()
    random.seed(a.seed);np.random.seed(a.seed);torch.manual_seed(a.seed)
    device='cuda' if torch.cuda.is_available() else 'cpu'
    if device=='cuda':torch.cuda.set_per_process_memory_fraction(.35)
    path=Path(a.dataset);meta=json.loads(path.with_suffix('.json').read_text(encoding='utf-8'));data=np.load(path,allow_pickle=False)
    alphabet=meta['alphabet'];own=data['personal'];texts=data['texts'];writers=data['writers'];modes=data['modes']
    if a.personal:
        if not a.base:raise ValueError('Personal fitting requires the generic base')
        words=[s for s in sorted(set(texts[own & (modes==0)])) if len(s)>1]
        formulas=sorted(set(texts[own & (modes==1)]))
        if len(words)<12 or len(formulas)<3:raise ValueError('Need at least 12 distinct reviewed text words/lines and 3 distinct formula examples; keep collecting calibration.')
        rng=np.random.default_rng(a.seed);rng.shuffle(words);rng.shuffle(formulas)
        held=set(words[:max(3,len(words)//5)])|set(formulas[:max(1,len(formulas)//5)])
        train_own=np.flatnonzero(own & np.asarray([x not in held for x in texts]));val=np.flatnonzero(own & np.asarray([x in held for x in texts]));generic=np.flatnonzero(~own & (writers<10))
        train=np.concatenate([generic,np.tile(train_own,8)])
    else:
        train=np.flatnonzero(~own & (writers<10));val=np.flatnonzero(~own & (writers>=10))
    if len(train)<10 or len(val)<3:raise ValueError('Not enough independent training and held-out samples')
    ink,chars,mode,ratios,pressure=tensors(data,alphabet,device);net=WordInkVAE(len(alphabet),points=meta['points']).to(device)
    if a.base:
        ck=torch.load(a.base,map_location='cpu',weights_only=True)
        if ck.get('format')!='folio-word-vae-research-v1':raise ValueError('Invalid base model')
        state=ck['model'];old=state.pop('chars.weight');new=net.state_dict()['chars.weight'];old_ids={c:i+1 for i,c in enumerate(ck['alphabet'])}
        with torch.no_grad():
            new[0]=old[0]
            for i,c in enumerate(alphabet,1):
                if c in old_ids:new[i]=old[old_ids[c]]
        state['chars.weight']=new;net.load_state_dict(state)
    optimizer=torch.optim.AdamW(net.parameters(),lr=.0002 if a.personal else .0006,weight_decay=.0001)
    train=torch.tensor(train,device=device);val=torch.tensor(val,device=device);out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);best=float('inf')
    print('device',device,'train sequences',len(train),'held-out sequences',len(val),'personal',a.personal,flush=True)
    for epoch in range(1,a.epochs+1):
        net.train();order=train[torch.randperm(len(train),device=device)]
        for idx in order.split(48):
            pred,r,mean,logvar=net(ink[idx],chars[idx],mode[idx]);value,_=loss(pred,r,ink[idx],ratios[idx],mean,logvar,pressure[idx])
            optimizer.zero_grad(set_to_none=True);value.backward();torch.nn.utils.clip_grad_norm_(net.parameters(),2);optimizer.step()
        net.eval();scores=[]
        with torch.inference_mode():
            # Never encode held-out ink: evaluate generation from text and a style
            # estimated only from the training set, not posterior reconstruction.
            if a.personal:
                means=[]
                for idx in torch.tensor(train_own,device=device).split(64):means.append(net.encode(ink[idx],chars[idx],mode[idx])[0])
                eval_style=torch.cat(means).mean(0)
            else:eval_style=torch.zeros(net.latent,device=device)
            for idx in val.split(64):
                z=eval_style[None].expand(len(idx),-1);pred,r=net.decode(z,chars[idx],mode[idx]);zero=torch.zeros_like(z)
                _,xy=loss(pred,r,ink[idx],ratios[idx],zero,zero,pressure[idx]);scores.append((float(xy),len(idx)))
        score=sum(x*n for x,n in scores)/sum(n for _,n in scores)
        if score<best:
            style=eval_style.detach().cpu()
            best=score;torch.save({'format':'folio-word-vae-research-v1','alphabet':alphabet,'points':meta['points'],'model':net.state_dict(),'style':style,
                'personal':a.personal,'train_count':len(train),'held_out_count':len(val),'held_out_xy':score,'source':meta['source']},out)
        if epoch==1 or epoch%10==0 or epoch==a.epochs:print('epoch',epoch,'held_out_generation_xy',round(score,5),'best',round(best,5),flush=True)
    print('Research checkpoint saved; validation is numerical, not a handwriting-quality certification.',flush=True)


if __name__=='__main__':main()
