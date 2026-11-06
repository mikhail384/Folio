"""Editable print handwriting plus user-sampled vector handwriting.

Built-in glyphs are authored for Folio; no rasterization, outline tracing or font dependency.
Personal paths stay stored as captured. Each playback can apply a small deterministic
per-letter warp, keyed by operation ID so retrying a write reproduces the same strokes.
This is sample-based variation, not a trained generative cursive model.
"""
import hashlib
import math
import random

def _replay_pressure(value):
    # Folio 1.18 compares float pressure against a double 0.02; keep even
    # rounded or imported minimum-pressure samples above that boundary.
    pressure = value if type(value) in (int, float) and math.isfinite(value) else 1
    return max(.02000001, min(2, round(pressure, 4)))

GLYPHS = {
 'A':'0,10 5,0 10,10|2,6 8,6', 'B':'0,10 0,0 6,0 9,2 8,4 0,5 7,5 10,7 8,10 0,10',
 'C':'10,1 7,0 2,1 0,5 2,9 7,10 10,9','D':'0,10 0,0 5,0 9,2 10,5 9,8 5,10 0,10',
 'E':'10,0 0,0 0,10 10,10|0,5 8,5','F':'0,10 0,0 10,0|0,5 8,5',
 'G':'10,2 8,0 3,0 0,3 0,7 3,10 9,10 10,6 6,6','H':'0,0 0,10|10,0 10,10|0,5 10,5',
 'I':'2,0 8,0|5,0 5,10|2,10 8,10','J':'2,0 10,0 10,8 8,10 3,10 0,7',
 'K':'0,0 0,10|10,0 0,5 10,10','L':'0,0 0,10 10,10','M':'0,10 0,0 5,6 10,0 10,10',
 'N':'0,10 0,0 10,10 10,0','O':'5,0 1,1 0,5 1,9 5,10 9,9 10,5 9,1 5,0',
 'P':'0,10 0,0 7,0 10,2 9,4 6,5 0,5','Q':'5,0 1,1 0,5 1,9 5,10 9,9 10,5 9,1 5,0|6,7 11,11',
 'R':'0,10 0,0 7,0 10,2 9,4 0,5|5,5 10,10','S':'10,1 7,0 2,1 0,3 3,5 8,6 10,8 8,10 2,10 0,9',
 'T':'0,0 10,0|5,0 5,10','U':'0,0 0,8 2,10 8,10 10,8 10,0','V':'0,0 5,10 10,0',
 'W':'0,0 2,10 5,5 8,10 10,0','X':'0,0 10,10|10,0 0,10','Y':'0,0 5,5 10,0|5,5 5,10',
 'Z':'0,0 10,0 0,10 10,10',
 'Б':'10,0 0,0 0,10 7,10 10,8 9,6 6,5 0,5', 'Г':'0,10 0,0 10,0',
 'Д':'0,10 2,8 3,0 8,0 9,10|0,12 0,10 11,10 11,12',
 'Ж':'0,0 5,5 0,10|5,0 5,10|10,0 5,5 10,10','З':'0,1 4,0 8,1 10,3 7,5 4,5|7,5 10,7 9,9 5,10 0,9',
 'И':'0,0 0,10 10,0 10,10','Й':'0,0 0,10 10,0 10,10|3,-3 5,-2 7,-3',
 'Л':'0,10 2,9 4,0 10,0 10,10','П':'0,10 0,0 10,0 10,10','У':'0,0 5,6 10,0|8,3 3,10 0,10',
 'Ф':'5,-1 5,11|5,1 1,2 0,5 1,8 5,9 9,8 10,5 9,2 5,1',
 'Ц':'0,0 0,10 10,10 10,0|10,10 10,12','Ч':'0,0 0,4 2,5 10,5|10,0 10,10',
 'Ш':'0,0 0,10 10,10 10,0|5,0 5,10','Щ':'0,0 0,10 10,10 10,0|5,0 5,10|10,10 10,12',
 'Ъ':'0,0 3,0 3,10 8,10 10,8 9,6 6,5 3,5','Ы':'0,0 0,10 4,10 6,8 5,6 0,5|10,0 10,10',
 'Ь':'0,0 0,10 7,10 10,8 9,6 6,5 0,5','Э':'0,1 3,0 8,1 10,5 8,9 3,10 0,9|4,5 10,5',
 'Ю':'0,0 0,10|0,5 4,5|7,0 4,2 4,8 7,10 10,8 10,2 7,0',
 'Я':'10,10 10,0 3,0 0,2 1,4 4,5 10,5|5,5 0,10',
 '0':'5,0 1,1 0,5 1,9 5,10 9,9 10,5 9,1 5,0|2,9 8,1','1':'2,2 5,0 5,10|1,10 9,10',
 '2':'0,2 2,0 7,0 10,2 9,4 0,10 10,10','3':'0,0 10,0 5,5 9,6 10,8 8,10 1,10',
 '4':'8,10 8,0 0,7 10,7','5':'10,0 0,0 0,5 6,5 10,7 9,9 6,10 0,9','6':'9,0 4,0 0,4 0,8 3,10 8,10 10,7 8,5 1,5',
 '7':'0,0 10,0 3,10','8':'5,0 1,1 0,3 3,5 9,6 10,8 8,10 2,10 0,8 1,6 8,4 10,2 8,0 5,0',
 '9':'9,5 2,5 0,3 1,1 4,0 8,0 10,2 10,7 7,10 1,10',
 '.':'5,10 5.2,10', ',':'5,10 4,12', ':':'5,3 5.2,3|5,9 5.2,9',';':'5,3 5.2,3|5,9 4,12',
 '!':'5,0 5,7|5,10 5.2,10','?':'0,2 2,0 7,0 10,2 9,4 5,6 5,7|5,10 5.2,10',
 '-':'1,5 9,5','+':'0,5 10,5|5,0 5,10','=':'0,3 10,3|0,7 10,7',
 '(':'7,0 3,3 2,5 3,7 7,10',')':'3,0 7,3 8,5 7,7 3,10','[':'8,0 2,0 2,10 8,10',']':'2,0 8,0 8,10 2,10',
 '/':'0,10 10,0','\\':'0,0 10,10','×':'1,1 9,9|9,1 1,9','÷':'0,5 10,5|5,1 5.2,1|5,9 5.2,9',
 '√':'0,6 3,10 5,0 12,0','π':'0,2 10,2|3,2 2,10|8,2 8,10 10,10','∞':'5,5 2,2 0,3 0,7 2,8 8,2 10,3 10,7 8,8 5,5',
 '→':'0,5 12,5|8,1 12,5 8,9','≤':'10,1 0,5 10,8|0,11 10,11','≥':'0,1 10,5 0,8|0,11 10,11',
 '"':'3,0 3,3|7,0 7,3',"'":'5,0 5,3','_':'0,11 10,11','%':'0,10 10,0|2,0 0,0 0,3 2,3 2,0|10,7 8,7 8,10 10,10 10,7'
}
for ru,latin in {'А':'A','В':'B','С':'C','Е':'E','Н':'H','К':'K','М':'M','О':'O','Р':'P','Т':'T','Х':'X'}.items(): GLYPHS[ru]=GLYPHS[latin]
GLYPHS['Ё']=GLYPHS['Е']+'|3,-2 3.2,-2|7,-2 7.2,-2'
GLYPHS.update({'—':GLYPHS['-'],'–':GLYPHS['-'],'«':'5,2 1,5 5,8|10,2 6,5 10,8','»':'0,2 4,5 0,8|5,2 9,5 5,8','²':'2,-2 3,-3 6,-3 7,-2 2,2 7,2'})

def builtin(character):
    key=character if character in GLYPHS else character.upper(); encoded=GLYPHS.get(key)
    if encoded is None: raise ValueError('Нет образца символа: '+repr(character))
    lower=character.islower(); yscale=.75 if lower else 1; offset=.25 if lower else 0
    paths=[]
    for path in encoded.split('|'):
        pts=[]
        for word in path.split():
            x,y=map(float,word.split(','));y=y/10*yscale+offset
            pts.append([x/10+.12*(1-y),y,1])
        paths.append(pts)
    return {'paths':paths,'advance':1.3 if not lower else 1.2}

def sample_from_selection(strokes,baseline_fraction=None):
    paths=[]
    for s in strokes:
        p=s['points']
        path=[[p[i],p[i+1],p[i+2]] for i in range(0,len(p)-2,5)]
        paths.append(path)
    all_points=[p for path in paths for p in path]
    if not all_points: raise ValueError('Пустой образец')
    x=min(p[0] for p in all_points); y=min(p[1] for p in all_points)
    h=max(p[1] for p in all_points)-y; w=max(p[0] for p in all_points)-x
    if h<1 or w<.1: raise ValueError('Слишком маленький образец')
    sample={'paths':[[[(p[0]-x)/h,(p[1]-y)/h,max(.02,min(2,p[2]))] for p in path] for path in paths], 'advance':w/h+.25,'natural_height':round(h,4)}
    if type(baseline_fraction) in (int,float) and math.isfinite(baseline_fraction):sample['baseline_fraction']=round(max(0,min(1.2,baseline_fraction)),4)
    return sample

def _median(values):
    n=len(values)
    if not n:return None
    middle=n//2
    return values[middle] if n%2 else (values[middle-1]+values[middle])/2

def _profile_reference_height(samples):
    # Prose words define the normal text height; formulas and one-letter samples
    # can be taller and must not shrink ordinary writing. One vote per label.
    word_heights=[];all_heights=[]
    for label,value in samples.items():
        variants=value.get('variants',[value]) if isinstance(value,dict) else []
        heights=sorted(v.get('natural_height') for v in variants if isinstance(v,dict) and type(v.get('natural_height')) in (int,float) and math.isfinite(v['natural_height']) and v['natural_height']>0)
        if not heights: continue
        label_height=_median(heights);all_heights.append(label_height)
        letters=[c for c in label if c.isalpha()]
        uppercase=sum(c.isupper() for c in letters)
        if len(letters)>=2 and any(c.islower() for c in letters) and uppercase/len(letters)<=.34:
            word_heights.append(label_height)
    reference=word_heights or all_heights
    reference.sort()
    return _median(reference)

def _variant(glyph,seed,token,offset):
    if not isinstance(glyph,dict): raise ValueError('Повреждён образец почерка')
    variants=glyph.get('variants')
    if variants is None: return glyph
    if not isinstance(variants,list) or not variants or any(not isinstance(v,dict) or 'paths' not in v or 'advance' not in v for v in variants):
        raise ValueError('Повреждены варианты образца почерка')
    digest=hashlib.sha256((seed+'\0'+token+'\0'+str(offset)).encode('utf-8')).digest()
    return variants[int.from_bytes(digest[:8],'big')%len(variants)]

def _seeded_random(seed,token,offset,part):
    digest=hashlib.sha256((seed+'\0'+token+'\0'+str(offset)+'\0'+part).encode('utf-8')).digest()
    return random.Random(int.from_bytes(digest[:8],'big'))

def _letter_weight(character):
    if character.isspace():return .58
    if not character.isalnum():return .55
    if character.lower() in 'iljtїі':return .72
    if character.lower() in 'mwшщжф':return 1.28
    return 1.0

def _personal_variation(glyph,token,seed,offset,baseline_fraction):
    """Apply subtle word-level deformation with continuous joins and stable retries."""
    points=[p for path in glyph['paths'] for p in path]
    if not points:return None
    left=min(p[0] for p in points);right=max(p[0] for p in points);total=right-left
    if total<1e-6:return None
    characters=list(token) or [' ']
    weights=[_letter_weight(c) for c in characters];weight_total=sum(weights)
    edges=[left];acc=left
    for weight in weights:
        acc+=total*weight/weight_total;edges.append(acc)
    rng=_seeded_random(seed,token,offset,'word')
    global_x=.995+rng.random()*.01
    global_y=.995+rng.random()*.01
    baseline_shift=(rng.random()-.5)*.012
    global_slant=(rng.random()-.5)*.016
    widths=[];heights=[];vertical=[];slants=[]
    for index in range(len(characters)):
        rng=_seeded_random(seed,token,offset,'letter:'+str(index))
        widths.append(global_x*(.975+rng.random()*.05))
        heights.append(global_y*(.985+rng.random()*.03))
        vertical.append((rng.random()-.5)*.024)
        slants.append(global_slant+(rng.random()-.5)*.016)
    edge_offsets=[0.0]
    for index in range(len(characters)):
        edge_offsets.append(edge_offsets[-1]+(edges[index+1]-edges[index])*(widths[index]-1))
    advance_scale=1+edge_offsets[-1]/total
    centers=[(edges[i]+edges[i+1])*.5 for i in range(len(characters))]

    def blend(values,positions,x):
        if len(values)==1 or x<=positions[0]:return values[0]
        if x>=positions[-1]:return values[-1]
        for index in range(len(positions)-1):
            if x<=positions[index+1]:
                t=(x-positions[index])/(positions[index+1]-positions[index])
                t=t*t*(3-2*t)
                return values[index]*(1-t)+values[index+1]*t
        return values[-1]

    def transform(point):
        px,py=point[0],point[1]
        warped_x=px+blend(edge_offsets,edges,px)
        warped_y=baseline_fraction+(py-baseline_fraction)*blend(heights,centers,px)+blend(vertical,centers,px)
        warped_x+=(baseline_fraction-py)*blend(slants,centers,px)
        return warped_x,warped_y+baseline_shift,advance_scale

    return transform,advance_scale

def layout(text,x,y,height=28,width=2.2,color='#28344b',max_width=900,samples=None,variation_seed=''):
    if not text or len(text)>1500: raise ValueError('Текст должен содержать 1–1500 символов')
    if not all(math.isfinite(v) for v in [x,y,height,width,max_width]) or not 6<=height<=240 or max_width<height: raise ValueError('Некорректный размер надписи')
    if samples is not None and not samples: raise ValueError('Личный почерк ещё не содержит образцов')
    x0=x;result=[];i=0;space_scale=height;line_baseline=y+height
    reference_height=_profile_reference_height(samples) if samples is not None else None
    while i<len(text):
        if text[i]=='\n':
            line_rng=_seeded_random(variation_seed,text,i,'line') if samples is not None and variation_seed else None
            line_gap=1.55+line_rng.random()*.2 if line_rng else 1.65
            x=x0;y+=height*line_gap;line_baseline=y+height;i+=1;continue
        if text[i].isspace():
            gap_rng=_seeded_random(variation_seed,text[i],i,'space') if samples is not None and variation_seed else None
            gap=.20+(gap_rng.random()*.08 if gap_rng else .04) if samples is not None else .65
            x+=space_scale*gap;i+=1;continue
        if samples is None: token=text[i];glyph=builtin(token)
        else:
            token=next((k for k in sorted(samples,key=len,reverse=True) if text.startswith(k,i)),None)
            if token is None: raise ValueError('Для личного почерка нет образца: '+repr(text[i]))
            glyph=_variant(samples[token],variation_seed,token,i)
        glyph_height=height
        natural_height=glyph.get('natural_height') if samples is not None else None
        if reference_height and type(natural_height) in (int,float) and math.isfinite(natural_height) and natural_height>0:
            ratio=natural_height/reference_height
            if len(token)>1:
                # Word examples may come from notebooks written at different canvas
                # zoom levels. Preserve single-glyph geometry; bound only whole words.
                ratio=max(.84,min(1.18,ratio))
            glyph_height=height*ratio
        advance=glyph['advance']*glyph_height
        if advance>max_width:raise ValueError('Образец шире строки; уменьшите высоту или увеличьте max_width')
        if x>x0 and x+advance>x0+max_width:x=x0;y+=height*1.65;line_baseline=y+height
        glyph_y=y
        if samples is not None:
            baseline_fraction=glyph.get('baseline_fraction',1)
            if type(baseline_fraction) not in (int,float) or not math.isfinite(baseline_fraction):baseline_fraction=1
            baseline_fraction=max(0,min(1.2,baseline_fraction))
            glyph_y=line_baseline-baseline_fraction*glyph_height
            varied=_personal_variation(glyph,token,variation_seed,i,baseline_fraction) if variation_seed else None
        else:variation=None
        advance_scale=1
        if samples is not None:
            variation,advance_scale=varied if varied is not None else (None,1)
        for path in glyph['paths']:
            if len(result)>=2000 or sum(len(s['points']) for s in result)+len(path)>100000:raise ValueError('Надпись слишком большая; разделите её на части')
            points=[]
            for p in path:
                if variation is not None:px,py,_=variation(p)
                else:px,py=p[0],p[1]
                points.append([round(x+px*glyph_height,3),round(glyph_y+py*glyph_height,3),_replay_pressure(p[2] if len(p)>2 else None)])
            result.append({'tool':'fountain','color':color,'width':width,'points':points})
        x+=advance*advance_scale;space_scale=glyph_height;i+=len(token)
    if not result or len(result)>2000 or sum(len(s['points']) for s in result)>100000:raise ValueError('Надпись слишком большая; разделите её на части')
    return result
