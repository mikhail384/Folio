"""Editable single-line print handwriting, plus exact vector samples from Folio.

Built-in glyphs are authored for Folio; no rasterization, outline tracing or font dependency.
Personal samples keep their original paths. This is glyph composition, not a trained cursive model.
"""
import math

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

def sample_from_selection(strokes):
    paths=[]
    for s in strokes:
        p=s['points']
        paths.append([[p[i],p[i+1],p[i+2]] for i in range(0,len(p),5)])
    all_points=[p for path in paths for p in path]
    if not all_points: raise ValueError('Пустой образец')
    x=min(p[0] for p in all_points); y=min(p[1] for p in all_points)
    h=max(p[1] for p in all_points)-y; w=max(p[0] for p in all_points)-x
    if h<1 or w<.1: raise ValueError('Слишком маленький образец')
    return {'paths':[[[(p[0]-x)/h,(p[1]-y)/h,max(.02,min(2,p[2]))] for p in path] for path in paths], 'advance':w/h+.25}

def layout(text,x,y,height=28,width=2.2,color='#28344b',max_width=900,samples=None):
    if not text or len(text)>1500: raise ValueError('Текст должен содержать 1–1500 символов')
    if not all(math.isfinite(v) for v in [x,y,height,width,max_width]) or not 6<=height<=240 or max_width<height: raise ValueError('Некорректный размер надписи')
    if samples is not None and not samples: raise ValueError('Личный почерк ещё не содержит образцов')
    x0=x;result=[];i=0
    while i<len(text):
        if text[i]=='\n':x=x0;y+=height*1.65;i+=1;continue
        if text[i].isspace():x+=height*.65;i+=1;continue
        if samples is None: token=text[i];glyph=builtin(token)
        else:
            token=next((k for k in sorted(samples,key=len,reverse=True) if text.startswith(k,i)),None)
            if token is None: raise ValueError('Для личного почерка нет образца: '+repr(text[i]))
            glyph=samples[token]
        advance=glyph['advance']*height
        if advance>max_width:raise ValueError('Образец шире строки; уменьшите высоту или увеличьте max_width')
        if x>x0 and x+advance>x0+max_width:x=x0;y+=height*1.65
        for path in glyph['paths']:
            if len(result)>=2000 or sum(len(s['points']) for s in result)+len(path)>100000:raise ValueError('Надпись слишком большая; разделите её на части')
            result.append({'tool':'fountain','color':color,'width':width,'points':[[round(x+p[0]*height,3),round(y+p[1]*height,3),_replay_pressure(p[2] if len(p)>2 else None)] for p in path]})
        x+=advance;i+=len(token)
    if not result or len(result)>2000 or sum(len(s['points']) for s in result)>100000:raise ValueError('Надпись слишком большая; разделите её на части')
    return result
