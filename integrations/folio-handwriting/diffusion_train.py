"""Research-only personalization of the MIT-licensed word diffusion reference.

Train on explicitly reviewed, private word crops. The output is an experimental
image generator, not a Folio ink engine; validate it on held-out words and do not
ship the weights until an editable-stroke conversion also passes review.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import sys
import time


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('dataset');parser.add_argument('checkpoint');parser.add_argument('output')
    parser.add_argument('--reference',default='research/handwriting-diffusion-reference')
    parser.add_argument('--steps',type=int,default=1000);parser.add_argument('--batch',type=int,default=8)
    parser.add_argument('--every',type=int,default=250);parser.add_argument('--learning-rate',type=float,default=0.00001)
    parser.add_argument('--image-scale',type=float,default=1.0,choices=(1.0,1.5,2.0))
    args=parser.parse_args()
    dataset=Path(args.dataset).resolve();reference=Path(args.reference).resolve();output=Path(args.output).resolve()
    if dataset.parent.name!='data' or not 100<=args.steps<=100000 or not 1<=args.batch<=32 or args.every<1:
        raise ValueError('Invalid dataset or training settings')
    if output.exists() and any(output.iterdir()):raise ValueError('Refusing to overwrite an existing model directory')
    os.environ['PROJ']=str(dataset.parent.parent);os.environ['HW_DS']=dataset.name
    os.environ['HW_FONT']=os.environ.get('HW_FONT',r'C:\Windows\Fonts\arial.ttf')
    font_path=Path(os.environ['HW_FONT']).resolve()
    if not font_path.is_file():raise ValueError('The content conditioning font does not exist')
    font_sha256=hashlib.sha256(font_path.read_bytes()).hexdigest()
    sys.path.insert(0,str(reference/'src'))
    import torch
    from torch.nn import functional as F
    from PIL import ImageFont
    import train as ref

    ref.H=round(64*args.image_scale);ref.W=round(288*args.image_scale);ref.PPC=19*args.image_scale
    ref._f=ImageFont.truetype(os.environ['HW_FONT'],round(40*args.image_scale))
    geometry=dataset/'geometry.json'
    if geometry.exists() and json.loads(geometry.read_text(encoding='utf8'))!={'height':ref.H,'width':ref.W}:
        raise ValueError('Dataset dimensions do not match the model image scale')
    if not geometry.exists() and args.image_scale!=1:
        raise ValueError('Scaled datasets require explicit image geometry')
    if ref.dev!='cuda':raise RuntimeError('A CUDA GPU is required for this research training')
    torch.set_num_threads(4);torch.manual_seed(20261103)
    torch.backends.cuda.matmul.allow_tf32=True
    torch.set_float32_matmul_precision('high')
    generic=torch.load(args.checkpoint,map_location='cpu',weights_only=True)
    if generic.get('chars')!=ref.chars or not isinstance(generic.get('e'),dict):
        raise ValueError('Pretrained model vocabulary does not match the reviewed data')
    training=ref.build_cache('train');validation=ref.build_cache('val')
    tg,co,ids=(tensor.to(ref.dev) for tensor in training[:3]);vt,vc,vi=(tensor.to(ref.dev) for tensor in validation[:3])
    if len(tg)<8 or len(vt)<3:raise ValueError('Insufficient distinct reviewed training/validation data')
    model=ref.UNet().to(ref.dev)
    model.load_state_dict({k:v.float() for k,v in generic['e'].items()})
    ema=copy.deepcopy(model).eval()
    for parameter in ema.parameters():parameter.requires_grad_(False)
    optimizer=torch.optim.AdamW(model.parameters(),lr=args.learning_rate,weight_decay=.0001)
    generator=torch.Generator(device=ref.dev).manual_seed(20261104)
    valid_noise=torch.randn((len(vt),1,ref.H,ref.W),device=ref.dev,generator=generator)
    valid_time=torch.randint(100,900,(len(vt),),device=ref.dev,generator=generator)
    output.mkdir(parents=True,exist_ok=True)

    def to_float(value):return value.float().unsqueeze(1)/127.5-1
    def augment(target,content):
        count=len(target);device=target.device
        sx=torch.empty(count,device=device).uniform_(.94,1.06)
        sy=torch.empty(count,device=device).uniform_(.96,1.04)
        shear=torch.empty(count,device=device).uniform_(-.04,.04)
        shift=torch.empty(count,device=device).uniform_(-.01,.01)
        zero=torch.zeros(count,device=device)
        matrix=torch.stack([torch.stack([1/sx,shear,shift],1),torch.stack([zero,1/sy,zero],1)],1)
        grid=F.affine_grid(matrix,target.shape,align_corners=False)
        return tuple(F.grid_sample((x+1)/2,grid,align_corners=False,padding_mode='border')*2-1 for x in (target,content))
    @torch.no_grad()
    def score():
        ema.eval();values=[]
        for idx in range(0,len(vt),args.batch):
            sl=slice(idx,idx+args.batch);target=to_float(vt[sl]);content=to_float(vc[sl]);t=valid_time[sl];noise=valid_noise[sl]
            with torch.autocast('cuda',dtype=torch.bfloat16):
                value=F.mse_loss(ema(ref.qsample(target,t,noise),content,vi[sl],t).float(),noise)
            values.append((float(value),len(target)))
        return sum(value*n for value,n in values)/sum(n for _,n in values)
    def save(step,metric):
        state={'e':{k:v.detach().cpu().half() for k,v in ema.state_dict().items()},'step':step,'chars':ref.chars,
               'research_only':True,'validation_noise_mse':metric,'training_examples':len(tg),'held_out_examples':len(vt),
               'image_scale':args.image_scale,'condition_font_sha256':font_sha256}
        torch.save(state,output/('step-%05d.pt'%step))
        print('step',step,'held_out_noise_mse',round(metric,5),'elapsed_seconds',round(time.time()-started),flush=True)

    started=time.time()
    print('CUDA research training:',len(tg),'reviewed train,',len(vt),'held-out word examples',flush=True)
    save(0,score())
    model.train()
    for step in range(1,args.steps+1):
        index=torch.randint(0,len(tg),(args.batch,),device=ref.dev)
        target,content=augment(to_float(tg[index]),to_float(co[index]));tokens=ids[index]
        drop=torch.rand(args.batch,device=ref.dev)<.1
        content=content*(~drop)[:,None,None,None];tokens=tokens*(~drop)[:,None]
        noise=torch.randn_like(target);t=torch.randint(0,ref.T,(args.batch,),device=ref.dev)
        optimizer.zero_grad(set_to_none=True)
        with torch.autocast('cuda',dtype=torch.bfloat16):
            prediction=model(ref.qsample(target,t,noise),content,tokens,t)
            loss=F.mse_loss(prediction.float(),noise)
        if not torch.isfinite(loss):raise ValueError('Non-finite training loss')
        loss.backward();torch.nn.utils.clip_grad_norm_(model.parameters(),1.0);optimizer.step()
        with torch.no_grad():
            for a,b in zip(ema.parameters(),model.parameters()):a.mul_(.995).add_(b,alpha=.005)
            for a,b in zip(ema.buffers(),model.buffers()):a.copy_(b)
        if step%args.every==0 or step==args.steps:save(step,score())
    (output/'training-summary.json').write_text(json.dumps({'research_only':True,'train_count':len(tg),'held_out_count':len(vt),'steps':args.steps},indent=2),encoding='utf8')
    print('Research checkpoints only; readability and editable-ink quality are not yet validated.',flush=True)


if __name__=='__main__':
    main()
