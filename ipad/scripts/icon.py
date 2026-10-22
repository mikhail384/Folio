"""Original Folio icon; no assets extracted from other apps. Requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw
import math

root = Path(__file__).resolve().parents[1]
scale = 2
image = Image.new('RGB', (1024*scale, 1024*scale), '#f8f4ec')
d = ImageDraw.Draw(image)
def box(values): return tuple(int(v*scale) for v in values)
d.rounded_rectangle(box((205, 153, 824, 905)), radius=70*scale, fill='#e6dfef')
d.rounded_rectangle(box((176, 122, 795, 874)), radius=70*scale, fill='#fffdf8')
for y in [305, 410, 515, 620, 725]:
    d.line(box((251, y, 715, y)), fill='#e9e6ed', width=3*scale)
points=[]
for i in range(600):
    t=i/599
    points.append((int((253+456*t)*scale),int((548-110*math.sin(t*math.pi*2.15)-90*t)*scale)))
for j in range(len(points)-1):
    width=int((14+17*math.sin(j/599*math.pi)**2)*scale)
    d.line([points[j],points[j+1]],fill='#8170b1',width=width)
for p in [points[0], points[-1]]:
    r=8*scale; d.ellipse((p[0]-r,p[1]-r,p[0]+r,p[1]+r),fill='#8170b1')
d.ellipse(box((671, 236, 722, 287)), fill='#dda891')
image.resize((1024,1024),Image.Resampling.LANCZOS).save(root/'Folio/Assets.xcassets/AppIcon.appiconset/AppIcon.png')
print('Original Folio icon generated')
