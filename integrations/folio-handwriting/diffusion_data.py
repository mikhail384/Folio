"""Prepare private, reviewed word images for an experimental diffusion model.

The source of truth is editable vector ink. The resulting images and labels are
private derived data; never put them in a public repository. A word is assigned
entirely to the training or held-out split, including repeated examples.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys
import unicodedata

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'folio-plugin'))
from corpus import Corpus


def split_key(text):
    """Keep case and punctuation variants of the same word in the same split."""
    value=unicodedata.normalize('NFC',text).casefold().strip()
    while value and unicodedata.category(value[0]).startswith('P'):
        value=value[1:]
    while value and unicodedata.category(value[-1]).startswith('P'):
        value=value[:-1]
    return value


def default_split(key):
    """Assign a word independently of corpus size, so later additions cannot leak it."""
    digest=hashlib.sha256(('folio-split-v2:'+key).encode('utf8')).digest()
    return 'val' if digest[0]<51 else 'train'


def previous_splits(dataset):
    """Preserve an existing dataset's word assignments when its corpus grows."""
    assigned={}
    for split in ('train','val'):
        rows=json.loads((Path(dataset)/(split+'.json')).read_text(encoding='utf8'))
        if not isinstance(rows,list):raise ValueError('Invalid previous split')
        for row in rows:
            if not isinstance(row,dict) or not isinstance(row.get('text'),str):
                raise ValueError('Invalid previous split')
            key=split_key(row['text'])
            if not key or key in assigned and assigned[key]!=split:
                raise ValueError('Previous training and validation words overlap')
            assigned[key]=split
    return assigned


def prepare(examples, destination, charset, height=64, width=288, assignments=None):
    destination = Path(destination)
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Refusing to overwrite an existing dataset')
    if height<32 or height>128 or width<128 or width>576 or height%8 or width%8:
        raise ValueError('Invalid word image dimensions')
    size_factor=height/64.0
    labels = sorted({unicodedata.normalize('NFC', e['text']) for e in examples
                     if e['kind'] == 'text' and 2 <= len(e['text']) <= 16
                     and split_key(e['text'])
                     and all(c in charset for c in e['text'])})
    keys=sorted({split_key(label) for label in labels if split_key(label)})
    if len(keys) < 12:
        raise ValueError('At least 12 distinct reviewed words are required')
    assignments={} if assignments is None else assignments
    if not isinstance(assignments,dict) or any(not isinstance(k,str) or v not in ('train','val') for k,v in assignments.items()):
        raise ValueError('Invalid previous assignments')
    assigned={key:assignments.get(key,default_split(key)) for key in keys}
    images = destination / 'img'
    images.mkdir(parents=True, exist_ok=True)
    splits = {'train': [], 'val': []}
    for item in examples:
        text = unicodedata.normalize('NFC', item['text'])
        if item['kind'] != 'text' or text not in labels:
            continue
        paths = [[(p[i], p[i+1]) for i in range(0, len(p), 5)]
                 for p in (stroke['points'] for stroke in item['strokes'])]
        points = [point for path in paths for point in path]
        if not points:
            continue
        lo_x=min(p[0] for p in points);hi_x=max(p[0] for p in points)
        lo_y=min(p[1] for p in points);hi_y=max(p[1] for p in points)
        if hi_y-lo_y < .001 or hi_x-lo_x < .001:
            continue
        scale=min(42.0*size_factor/(hi_y-lo_y), (width-14*size_factor)/(hi_x-lo_x))
        padding=round(8*size_factor)
        image_width=math.ceil((hi_x-lo_x)*scale)+padding;image_height=math.ceil((hi_y-lo_y)*scale)+padding
        if image_width < padding or image_height < padding or image_width > width or image_height > height:
            continue
        supersample=3
        image=Image.new('L',(image_width*supersample,image_height*supersample),255)
        draw=ImageDraw.Draw(image)
        for path in paths:
            xy=[((x-lo_x)*scale*supersample+padding*supersample/2,(y-lo_y)*scale*supersample+padding*supersample/2) for x,y in path]
            if len(xy)>1:
                draw.line(xy,fill=0,width=max(1,round(2*size_factor*supersample)),joint='curve')
            elif xy:
                x,y=xy[0];r=size_factor*supersample
                draw.ellipse((x-r,y-r,x+r,y+r),fill=0)
        identity=hashlib.sha256(json.dumps(item,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode('utf8')).hexdigest()
        image.resize((image_width,image_height),Image.Resampling.LANCZOS).save(images/(identity+'.png'))
        splits[assigned[split_key(text)]].append({'img':identity+'.png','text':text})
    if len(splits['train']) < 8 or len(splits['val']) < 3:
        raise ValueError('Insufficient valid reviewed trajectories after split')
    for name, rows in splits.items():
        (destination/(name+'.json')).write_text(json.dumps(rows,ensure_ascii=False),encoding='utf8')
    (destination/'charset.json').write_text(json.dumps(charset,ensure_ascii=False),encoding='utf8')
    (destination/'geometry.json').write_text(json.dumps({'height':height,'width':width}),encoding='utf8')
    return {name:len(rows) for name,rows in splits.items()}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('corpus');parser.add_argument('destination');parser.add_argument('charset')
    parser.add_argument('--profile',default='my-handwriting')
    parser.add_argument('--height',type=int,default=64);parser.add_argument('--width',type=int,default=288)
    parser.add_argument('--split-from',help='Preserve the train and validation assignments of this earlier dataset')
    args=parser.parse_args()
    charset=json.loads(Path(args.charset).read_text(encoding='utf8'))
    prior=previous_splits(args.split_from) if args.split_from else None
    counts=prepare(Corpus(args.corpus,args.profile).examples(),args.destination,charset,height=args.height,width=args.width,assignments=prior)
    print('Private reviewed word crops:', counts)


if __name__ == '__main__':
    main()
