"""Small conditional vector VAE for research; it predicts coordinates and pen lifts."""
import torch
from torch import nn
from torch.nn import functional as F


class InkVAE(nn.Module):
    def __init__(self, labels, points=64, latent=24, embedding=48, hidden=384, writers=13):
        super().__init__()
        self.points, self.latent = points, latent
        self.label = nn.Embedding(labels, embedding)
        self.encoder = nn.Sequential(nn.Linear(points*3+embedding, hidden),nn.SiLU(),
                                     nn.Linear(hidden, hidden//2),nn.SiLU())
        self.mean = nn.Linear(hidden//2, latent)
        self.logvar = nn.Linear(hidden//2, latent)
        self.decoder = nn.Sequential(nn.Linear(latent+embedding, hidden),nn.SiLU(),
                                     nn.Linear(hidden, hidden),nn.SiLU(),
                                     nn.Linear(hidden, points*3))
        self.writer = nn.Linear(latent, writers)

    def encode(self, points, labels):
        h=self.encoder(torch.cat([points.flatten(1),self.label(labels)],dim=1))
        return self.mean(h),self.logvar(h).clamp(-8,5)

    def decode(self, latent, labels):
        value=self.decoder(torch.cat([latent,self.label(labels)],dim=1)).reshape(-1,self.points,3)
        return value

    def forward(self, points, labels, stochastic=True):
        mean,logvar=self.encode(points,labels)
        latent=mean+torch.randn_like(mean)*torch.exp(.5*logvar) if stochastic else mean
        return self.decode(latent,labels),mean,logvar


def losses(model, prediction, target, mean, logvar, writers, beta=.01):
    xy=F.mse_loss(prediction[:,:,:2],target[:,:,:2])
    velocity=F.mse_loss(prediction[:,1:,:2]-prediction[:,:-1,:2],target[:,1:,:2]-target[:,:-1,:2])
    end=F.binary_cross_entropy_with_logits(prediction[:,:,2],target[:,:,2],pos_weight=target.new_tensor(15.))
    kl=(-.5*(1+logvar-mean.square()-logvar.exp())).mean()
    writer=F.cross_entropy(model.writer(mean),writers)
    return xy+.35*velocity+.025*end+beta*kl+.005*writer,xy.detach(),end.detach()
