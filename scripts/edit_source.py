"""Small exact method replacement helper for the original compact Java sources."""
from pathlib import Path
def method(path, signature, replacement):
    p=Path(path);source=p.read_text(encoding='utf-8-sig');start=source.index(signature);brace=source.index('{',start);depth=0;quote=None;escape=False;end=None
    for i in range(brace,len(source)):
        c=source[i]
        if quote:
            if escape:escape=False
            elif c=='\\':escape=True
            elif c==quote:quote=None
        elif c in ('"',"'"):quote=c
        elif c=='{':depth+=1
        elif c=='}':
            depth-=1
            if depth==0:end=i+1;break
    if end is None:raise ValueError('Unclosed method '+signature)
    p.write_text(source[:start]+replacement+source[end:],encoding='utf-8')
