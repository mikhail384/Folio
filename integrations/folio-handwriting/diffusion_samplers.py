"""Research samplers; the accelerated path must be validated before deployment."""
import numpy as np
import torch


@torch.inference_mode()
def ddim(model,ref,words,steps=50,tmax=970,guidance=2.0,seed=7401,clip=False,eta=0):
    if (not isinstance(steps,int) or not isinstance(tmax,int)
            or not 2<=steps<=ref.T or not steps<=tmax<ref.T
            or not isinstance(words,(tuple,list)) or not 1<=len(words)<=12
            or not np.isfinite(guidance) or not 0<=guidance<=10
            or not np.isfinite(eta) or not 0<=eta<=1):
        raise ValueError('Invalid denoising schedule')
    # The reference encoder otherwise maps unknown characters to padding and
    # silently truncates long input, producing a different word than requested.
    if any(not isinstance(word,str) or not word or len(word)>ref.L
           or any(character not in ref.chars for character in word) for word in words):
        raise ValueError('A word contains unsupported characters or is too long')
    generator=torch.Generator(device=ref.dev).manual_seed(seed)
    count=len(words)
    cond=torch.stack([torch.from_numpy(np.asarray(ref.render_content(text,min(ref.W,len(text)*ref.PPC)),np.float32)/127.5-1)[None] for text in words]).to(ref.dev)
    ids=torch.tensor([ref.encode(text) for text in words],device=ref.dev)
    empty=torch.zeros_like(cond);blank=torch.zeros_like(ids)
    x=torch.randn((count,1,ref.H,ref.W),generator=generator,device=ref.dev)
    times=np.linspace(0,tmax,steps,dtype=np.int64)[::-1].copy()
    model.eval()
    for index,step in enumerate(times):
        t=torch.full((count,),int(step),device=ref.dev,dtype=torch.long)
        if guidance==1:
            noise=model(x,cond,ids,t)
        else:
            base=model(x,empty,blank,t);noise=base+guidance*(model(x,cond,ids,t)-base)
        a=ref.abar[int(step)]
        predicted=(x-(1-a).sqrt()*noise)/a.sqrt()
        # Clipping x0 at each step badly damaged cursive letters in local
        # held-out experiments for this checkpoint. Keep it opt-in for research.
        if clip:predicted=predicted.clamp(-1,1)
        if index+1==len(times):x=predicted;break
        previous=ref.abar[int(times[index+1])]
        sigma=eta*((1-previous)/(1-a)*(1-a/previous)).clamp(min=0).sqrt()
        direction=(1-previous-sigma*sigma).clamp(min=0).sqrt()*noise
        x=previous.sqrt()*predicted+direction
        if eta:x=x+sigma*torch.randn(x.shape,device=ref.dev,generator=generator)
    return ((x.clamp(-1,1)+1)*127.5).cpu().numpy().astype(np.uint8)
