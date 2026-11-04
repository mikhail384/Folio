"""Generate private research word images, with no access to source trajectories."""
import argparse
import json
import math
import os
from pathlib import Path
import sys


def main():
    parser=argparse.ArgumentParser();parser.add_argument('checkpoint');parser.add_argument('output');parser.add_argument('words',nargs='+')
    parser.add_argument('--reference',default='research/handwriting-diffusion-reference');parser.add_argument('--seed',type=int,default=7401)
    parser.add_argument('--sampler',choices=('reference','ddim'),default='reference')
    parser.add_argument('--steps',type=int,default=50);parser.add_argument('--eta',type=float,default=1.0)
    parser.add_argument('--guidance',type=float,default=2.0);parser.add_argument('--spacing',type=float,default=22.0)
    args=parser.parse_args();reference=Path(args.reference).resolve();output=Path(args.output).resolve()
    if output.exists() and any(output.iterdir()):raise ValueError('Refusing to overwrite an existing output directory')
    os.environ['PROJ']=str(output.parent/'diffusion-render-work');os.environ['HW_FONT']=os.environ.get('HW_FONT',r'C:\Windows\Fonts\arial.ttf')
    sys.path.insert(0,str(reference/'src'))
    import torch
    from PIL import Image, ImageFont
    import train as ref
    if not 1<=len(args.words)<=12 or any(not word or len(word)>ref.L or any(c not in ref.chars for c in word) for word in args.words):
        raise ValueError('Each research word must use known characters and contain at most 16 symbols')
    if not math.isfinite(args.spacing) or not 12<=args.spacing<=30 or not math.isfinite(args.guidance) or not 0<=args.guidance<=10:
        raise ValueError('Invalid research spacing or guidance')
    torch.set_num_threads(4);torch.manual_seed(args.seed);torch.backends.cuda.matmul.allow_tf32=True
    saved=torch.load(args.checkpoint,map_location='cpu',weights_only=True)
    if saved.get('chars')!=ref.chars:raise ValueError('Mismatched model vocabulary')
    scale=saved.get('image_scale',1.0)
    if scale not in (1.0,1.5,2.0):raise ValueError('Unsupported checkpoint image scale')
    ref.H=round(64*scale);ref.PPC=args.spacing*scale
    ref.W=max(round(288*scale),math.ceil((max(map(len,args.words))*ref.PPC+24*scale)/8)*8)
    ref._f=ImageFont.truetype(os.environ['HW_FONT'],round(40*scale))
    model=ref.UNet().to(ref.dev);model.load_state_dict({k:v.float() for k,v in saved['e'].items()});model.eval()
    if args.sampler=='reference':
        images=ref.sample(model,args.words,args.guidance)
    else:
        from diffusion_samplers import ddim
        images=ddim(model,ref,args.words,steps=args.steps,tmax=990,guidance=args.guidance,seed=args.seed,eta=args.eta)
    output.mkdir(parents=True,exist_ok=True)
    rows=[]
    for index,(word,image) in enumerate(zip(args.words,images)):
        name='word-%02d.png'%index;Image.fromarray(image[0]).save(output/name);rows.append({'text':word,'image':name})
    (output/'manifest.json').write_text(json.dumps(rows,ensure_ascii=False),encoding='utf8')
    (output/'research-settings.json').write_text(json.dumps({'sampler':args.sampler,'steps':args.steps if args.sampler=='ddim' else ref.T,
        'eta':args.eta if args.sampler=='ddim' else None,'guidance':args.guidance,'spacing':args.spacing,'seed':args.seed,
        'image_scale':scale,'width':ref.W,'height':ref.H,'validated_for_production':False},indent=2),encoding='utf8')
    print('Generated',len(rows),'new research word images; no sample strokes were replayed.',flush=True)


if __name__=='__main__':main()
