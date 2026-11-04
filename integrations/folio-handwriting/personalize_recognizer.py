"""Research-only, local OCR adaptation for rejecting bad generated words.

Only confirmed personal transcripts are used. The held-out set contains whole
words excluded from training; unreviewed OCR and generated ink never enter it.
The recognizer is a quality signal, not proof that a word is correct.
"""
import argparse
import json
from pathlib import Path
import random
import unicodedata


def distance(a,b):
    a=unicodedata.normalize('NFC',a).casefold().strip(' .,:;!?')
    b=unicodedata.normalize('NFC',b).casefold().strip(' .,:;!?')
    row=list(range(len(b)+1))
    for i,c in enumerate(a,1):
        nxt=[i]
        for j,d in enumerate(b,1):nxt.append(min(nxt[-1]+1,row[j]+1,row[j-1]+(c!=d)))
        row=nxt
    return row[-1],max(1,len(a))


def main():
    parser=argparse.ArgumentParser();parser.add_argument('dataset');parser.add_argument('base');parser.add_argument('output')
    parser.add_argument('--steps',type=int,default=400);parser.add_argument('--every',type=int,default=100);parser.add_argument('--batch',type=int,default=2)
    parser.add_argument('--learning-rate',type=float,default=.00001);parser.add_argument('--encoder-only',action='store_true')
    args=parser.parse_args();dataset=Path(args.dataset);output=Path(args.output)
    if output.exists() and any(output.iterdir()):raise ValueError('Refusing to overwrite an existing experiment')
    if not 1<=args.batch<=8 or not 1<=args.steps<=10000 or args.every<1 or not 1e-7<=args.learning_rate<=.001:raise ValueError('Invalid training settings')
    import numpy as np
    import torch
    from PIL import Image
    from torch.nn import functional as F
    from peft import LoraConfig,inject_adapter_in_model,get_peft_model_state_dict
    from transformers import TrOCRProcessor,VisionEncoderDecoderModel
    if not torch.cuda.is_available():raise RuntimeError('CUDA is required for the research experiment')
    torch.manual_seed(20261104);random.seed(20261104);torch.set_num_threads(4)
    torch.backends.cuda.matmul.allow_tf32=True
    processor=TrOCRProcessor.from_pretrained(args.base,local_files_only=True)
    def load(split):
        rows=json.loads((dataset/(split+'.json')).read_text(encoding='utf8'))
        if len(rows)<3:raise ValueError('Not enough reviewed examples')
        images=[Image.open(dataset/'img'/row['img']).convert('RGB') for row in rows]
        values=processor(images=images,return_tensors='pt').pixel_values.to('cuda')
        labels=processor.tokenizer([row['text'] for row in rows],padding='max_length',max_length=32,truncation=True,return_tensors='pt').input_ids
        labels[labels==processor.tokenizer.pad_token_id]=-100
        return rows,values,labels.to('cuda')
    train,tg,labels=load('train');valid,vg,_=load('val')
    model=VisionEncoderDecoderModel.from_pretrained(args.base,local_files_only=True,use_safetensors=True)
    for param in model.parameters():param.requires_grad_(False)
    targets=['query','value'] if args.encoder_only else ['query','value','q_proj','v_proj']
    inject_adapter_in_model(LoraConfig(r=8,lora_alpha=16,lora_dropout=.05,target_modules=targets),model)
    model=model.to('cuda');optimizer=torch.optim.AdamW([p for p in model.parameters() if p.requires_grad],lr=args.learning_rate,weight_decay=.001)
    output.mkdir(parents=True,exist_ok=True)
    def evaluate():
        model.eval();edits=chars=exact=0
        with torch.inference_mode(),torch.autocast('cuda',dtype=torch.bfloat16):
            for start in range(0,len(valid),args.batch):
                token=model.generate(vg[start:start+args.batch],max_new_tokens=48,num_beams=4)
                guesses=processor.batch_decode(token,skip_special_tokens=True)
                for row,guess in zip(valid[start:start+args.batch],guesses):
                    error,total=distance(row['text'],guess);edits+=error;chars+=total;exact+=error==0
        return edits/chars,exact
    def augment(image):
        count=len(image);device=image.device
        sx=torch.empty(count,device=device).uniform_(.96,1.04);sy=torch.empty(count,device=device).uniform_(.96,1.04)
        shear=torch.empty(count,device=device).uniform_(-.025,.025);shift=torch.empty(count,device=device).uniform_(-.015,.015);zero=torch.zeros(count,device=device)
        matrix=torch.stack([torch.stack([1/sx,shear,shift],1),torch.stack([zero,1/sy,zero],1)],1)
        return F.grid_sample(image,F.affine_grid(matrix,image.shape,align_corners=False),align_corners=False,padding_mode='border')
    baseline,exact=evaluate();best=baseline
    print('Held-out baseline CER',round(baseline,4),'exact',exact,'/',len(valid),flush=True)
    for step in range(1,args.steps+1):
        model.train();indices=torch.randint(0,len(train),(args.batch,),device='cuda')
        optimizer.zero_grad(set_to_none=True)
        with torch.autocast('cuda',dtype=torch.bfloat16):loss=model(pixel_values=augment(tg[indices]),labels=labels[indices]).loss
        if not torch.isfinite(loss):raise ValueError('Non-finite recognizer loss')
        loss.backward();torch.nn.utils.clip_grad_norm_([p for p in model.parameters() if p.requires_grad],1.0);optimizer.step()
        if step%args.every==0 or step==args.steps:
            score,correct=evaluate();print('step',step,'held-out CER',round(score,4),'exact',correct,'/',len(valid),flush=True)
            if score<best:
                best=score
                state={k:v.detach().cpu() for k,v in get_peft_model_state_dict(model).items()}
                torch.save({'format':'folio-private-ocr-adapter-v1','adapter':state,'step':step,'heldout_cer':score,'baseline_cer':baseline},output/'best.pt')
    (output/'summary.json').write_text(json.dumps({'baseline_cer':baseline,'best_cer':best,'train':len(train),'heldout':len(valid)},indent=2),encoding='utf8')
    print('Research evaluation complete. OCR can still reject correct handwriting or accept wrong handwriting.',flush=True)


if __name__=='__main__':main()
