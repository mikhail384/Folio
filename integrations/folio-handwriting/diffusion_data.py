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


def prepare(examples, destination, charset):
    destination = Path(destination)
    if destination.exists() and any(destination.iterdir()):
        raise ValueError('Refusing to overwrite an existing dataset')
    labels = sorted({unicodedata.normalize('NFC', e['text']) for e in examples
                     if e['kind'] == 'text' and 2 <= len(e['text']) <= 16
                     and all(c in charset for c in e['text'])})
    keys=sorted({split_key(label) for label in labels if split_key(label)})
    if len(keys) < 12:
        raise ValueError('At least 12 distinct reviewed words are required')
    keys.sort(key=lambda s: hashlib.sha256(('folio-split-v1:' + s).encode('utf8')).digest())
    held = set(keys[:max(3, len(keys)//5)])
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
        scale=min(42.0/(hi_y-lo_y), 274.0/(hi_x-lo_x))
        width=math.ceil((hi_x-lo_x)*scale)+8;height=math.ceil((hi_y-lo_y)*scale)+8
        if width < 8 or height < 8 or width > 288 or height > 64:
            continue
        supersample=3
        image=Image.new('L',(width*supersample,height*supersample),255)
        draw=ImageDraw.Draw(image)
        for path in paths:
            xy=[((x-lo_x)*scale*supersample+4*supersample,(y-lo_y)*scale*supersample+4*supersample) for x,y in path]
            if len(xy)>1:
                draw.line(xy,fill=0,width=2*supersample,joint='curve')
            elif xy:
                x,y=xy[0];r=supersample
                draw.ellipse((x-r,y-r,x+r,y+r),fill=0)
        identity=hashlib.sha256(json.dumps(item,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode('utf8')).hexdigest()
        image.resize((width,height),Image.Resampling.LANCZOS).save(images/(identity+'.png'))
        splits['val' if split_key(text) in held else 'train'].append({'img':identity+'.png','text':text})
    if len(splits['train']) < 8 or len(splits['val']) < 3:
        raise ValueError('Insufficient valid reviewed trajectories after split')
    for name, rows in splits.items():
        (destination/(name+'.json')).write_text(json.dumps(rows,ensure_ascii=False),encoding='utf8')
    (destination/'charset.json').write_text(json.dumps(charset,ensure_ascii=False),encoding='utf8')
    return {name:len(rows) for name,rows in splits.items()}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('corpus');parser.add_argument('destination');parser.add_argument('charset')
    parser.add_argument('--profile',default='my-handwriting')
    args=parser.parse_args()
    charset=json.loads(Path(args.charset).read_text(encoding='utf8'))
    counts=prepare(Corpus(args.corpus,args.profile).examples(),args.destination,charset)
    print('Private reviewed word crops:', counts)


if __name__ == '__main__':
    main()
