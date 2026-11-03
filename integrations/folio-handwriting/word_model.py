"""Whole-sequence vector VAE: transcript-conditioned ink, including pen lifts.

Research prototype only. This predicts new points for an entire word/line; it
does not construct a word by replaying the user's individual glyph paths.
"""
import math
import torch
from torch import nn
from torch.nn import functional as F


class WordInkVAE(nn.Module):
    def __init__(self,vocab,points=128,dim=96,latent=32):
        super().__init__();self.points=points;self.latent=latent
        self.chars=nn.Embedding(vocab+1,dim,padding_idx=0);self.text=nn.GRU(dim,dim//2,batch_first=True,bidirectional=True)
        self.mode=nn.Embedding(2,dim);self.position=nn.Embedding(points,dim)
        self.encoder=nn.Sequential(nn.Linear(points*4+dim*2,384),nn.SiLU(),nn.Linear(384,192),nn.SiLU())
        self.mean=nn.Linear(192,latent);self.logvar=nn.Linear(192,latent)
        self.style=nn.Linear(latent,dim);self.attention=nn.MultiheadAttention(dim,4,batch_first=True)
        self.output=nn.Sequential(nn.Linear(dim*3,192),nn.SiLU(),nn.Linear(192,96),nn.SiLU(),nn.Linear(96,4))
        self.ratio=nn.Sequential(nn.Linear(dim*2+latent,128),nn.SiLU(),nn.Linear(128,1))

    def context(self,chars,modes):
        mask=chars.eq(0);length=(~mask).sum(1).clamp_min(1).cpu()
        packed=nn.utils.rnn.pack_padded_sequence(self.chars(chars),length,batch_first=True,enforce_sorted=False)
        encoded,_=self.text(packed);encoded,_=nn.utils.rnn.pad_packed_sequence(encoded,batch_first=True,total_length=chars.shape[1])
        pooled=(encoded*(~mask).unsqueeze(-1)).sum(1)/(~mask).sum(1,keepdim=True).clamp_min(1)
        return encoded,pooled,self.mode(modes),mask

    def encode(self,ink,chars,modes):
        _,pooled,mode,_=self.context(chars,modes);h=self.encoder(torch.cat([ink.flatten(1),pooled,mode],1))
        return self.mean(h),self.logvar(h).clamp(-8,5)

    def decode(self,z,chars,modes):
        encoded,pooled,mode,mask=self.context(chars,modes);batch=chars.shape[0]
        position=self.position(torch.arange(self.points,device=chars.device))[None].expand(batch,-1,-1)
        query=position+self.style(z)[:,None,:]+mode[:,None,:]
        attended,_=self.attention(query,encoded,encoded,key_padding_mask=mask,need_weights=False)
        raw=self.output(torch.cat([query,attended,pooled[:,None,:].expand(-1,self.points,-1)],-1))
        xy=torch.sigmoid(raw[:,:,:2]);pressure=torch.sigmoid(raw[:,:,2:3]);lift=raw[:,:,3:4]
        return torch.cat([xy,pressure,lift],-1),self.ratio(torch.cat([pooled,mode,z],1)).squeeze(1).clamp(-4,4)

    def forward(self,ink,chars,modes,stochastic=True):
        mean,logvar=self.encode(ink,chars,modes)
        z=mean+torch.randn_like(mean)*torch.exp(.5*logvar) if stochastic else mean
        if self.training and stochastic:z=torch.where(torch.rand(len(z),1,device=z.device)<.3,torch.zeros_like(z),z)
        result,ratio=self.decode(z,chars,modes);return result,ratio,mean,logvar


def loss(pred,ratio,target,target_ratio,mean,logvar,pressure_mask):
    xy=F.mse_loss(pred[:,:,:2],target[:,:,:2])
    velocity=F.mse_loss(pred[:,1:,:2]-pred[:,:-1,:2],target[:,1:,:2]-target[:,:-1,:2])
    lift=F.binary_cross_entropy_with_logits(pred[:,:,3],target[:,:,3],pos_weight=target.new_tensor(12.))
    ratio_loss=F.smooth_l1_loss(ratio,target_ratio)
    pm=pressure_mask.float()[:,None];pressure=((pred[:,:,2]-target[:,:,2]).square()*pm).sum()/pm.sum().clamp_min(1)/target.shape[1]
    kl=(-.5*(1+logvar-mean.square()-logvar.exp())).mean()
    return xy+.4*velocity+.025*lift+.06*ratio_loss+.05*pressure+.01*kl,xy.detach()
