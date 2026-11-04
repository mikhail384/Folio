"""Experimental conversion of *generated* handwriting images to Folio ink.

This traces a generated raster's centreline, never replays a training sample.
Pixel widths are estimates, not measured stylus pressure or stroke order. Do
not enable as a production handwriting engine without visual and device tests.
"""
import math
import re
import numpy as np
from PIL import Image
from scipy import ndimage
from skimage.morphology import skeletonize


def _graph(mask):
    pixels={tuple(int(v) for v in p) for p in np.argwhere(mask)}
    graph={p:[] for p in pixels}
    for y,x in pixels:
        for dy in (-1,0,1):
            for dx in (-1,0,1):
                if not (dx or dy):continue
                neighbour=(y+dy,x+dx)
                if neighbour not in pixels:continue
                # Avoid a redundant diagonal when an orthogonal route exists.
                if dx and dy and ((y+dy,x) in pixels or (y,x+dx) in pixels):continue
                graph[(y,x)].append(neighbour)
        graph[(y,x)].sort()
    return graph


def _paths(graph):
    used=set();result=[]
    def edge(a,b):return (a,b) if a<b else (b,a)
    def follow(start):
        path=[start];prior=None;current=start
        while True:
            candidates=[p for p in graph[current] if edge(current,p) not in used]
            if not candidates:break
            if prior is None:nxt=candidates[0]
            else:
                vy,vx=current[0]-prior[0],current[1]-prior[1]
                # Continue along the straightest available segment through a
                # junction; every actual graph edge is still visited once.
                nxt=max(candidates,key=lambda p:((p[0]-current[0])*vy+(p[1]-current[1])*vx)/math.hypot(p[0]-current[0],p[1]-current[1]))
            used.add(edge(current,nxt));prior,current=current,nxt;path.append(current)
        return path
    # Odd-degree vertices are natural start/end points; processing them first
    # avoids splitting a connected cursive word into a stroke at every crossing.
    starts=sorted(graph,key=lambda p:(len(graph[p])%2==0,p))
    for point in starts:
        if not graph[point]:result.append([point]);continue
        while any(edge(point,n) not in used for n in graph[point]):result.append(follow(point))
    return result


def _smooth(path,graph,sigma=.8):
    """Smooth raster stair steps while keeping endpoints and crossings fixed."""
    coords=np.asarray(path,dtype=np.float64)
    if len(path)<4:return coords
    anchors=[0]+[i for i,p in enumerate(path[1:-1],1) if len(graph[p])!=2]+[len(path)-1]
    result=coords.copy()
    for start,end in zip(anchors,anchors[1:]):
        if end-start<3:continue
        part=coords[start:end+1]
        filtered=ndimage.gaussian_filter1d(part,sigma=sigma,axis=0,mode='nearest')
        filtered[0]=part[0];filtered[-1]=part[-1]
        result[start:end+1]=filtered
    return result


def vectorize(image,x=0,y=0,height=40,color='#28344b',threshold=170,upscale=3):
    if not isinstance(image,Image.Image) or not isinstance(upscale,int) or not 1<=upscale<=4 or image.width*image.height*upscale*upscale>2_000_000:
        raise ValueError('Invalid generated image')
    if any(not math.isfinite(float(v)) or abs(float(v))>1e7 for v in (x,y,height)) or not 2<=height<=2000:
        raise ValueError('Invalid output position or height')
    if not isinstance(color,str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',color) or not 1<=threshold<=254:
        raise ValueError('Invalid ink colour or threshold')
    gray=image.convert('L')
    if upscale>1:gray=gray.resize((gray.width*upscale,gray.height*upscale),Image.Resampling.LANCZOS)
    pixels=np.asarray(gray);foreground=pixels<threshold
    components,count=ndimage.label(foreground,structure=np.ones((3,3),dtype=np.uint8))
    if not count:raise ValueError('Generated image has no ink')
    sizes=np.bincount(components.ravel());keep=sizes>=2*upscale*upscale;keep[0]=False
    foreground=keep[components]
    if not foreground.any():raise ValueError('Generated image contains only isolated noise')
    skeleton=skeletonize(foreground);graph=_graph(skeleton)
    paths=_paths(graph)
    if not paths or len(paths)>2000:raise ValueError('Generated image is too fragmented')
    distance=ndimage.distance_transform_edt(foreground)
    radii=distance[skeleton]
    # The model produces visual ink, not measured pressure. Estimate width from
    # the raster and encode it using Folio's circular pressure-sensitive nib.
    median=float(np.median(radii));ys,xs=np.nonzero(foreground)
    left,top,right,bottom=int(xs.min()),int(ys.min()),int(xs.max()),int(ys.max())
    scale=float(height)/max(1,bottom-top+1)
    # The distance transform measures centre-to-centre pixels; subtract the
    # outer pixel to avoid making a one-pixel line twice as wide as the raster.
    estimated=max(.3,min(80.0,max(1.0,2*median-1)*scale))
    # A thin antialiased raster should not shrink the visible pen compared to
    # the user's actual handwritten sample at the same letter height.
    width=max(.3,min(80.0,max(estimated,float(height)*.065)))
    strokes=[];total=0
    for path in paths:
        points=[]
        smoothed=_smooth(path,graph,sigma=.8*upscale)
        for (yy,xx),(smooth_y,smooth_x) in zip(path,smoothed):
            diameter=max(.3,min(80.0,max(1.0,2*float(distance[yy,xx])-1)*scale))
            ratio=diameter/estimated
            pressure=max(.021,min(1.5,((max(.2,ratio)-.2)/.8)**2))
            points.append([round(float(x)+(smooth_x-left+.5)*scale,4),round(float(y)+(smooth_y-top+.5)*scale,4),round(pressure,5),0.0])
        total+=len(points)
        if total>100000:raise ValueError('Generated image contains too many points')
        strokes.append({'tool':'fountain','color':color,'width':round(width,4),'points':points})
    return {'strokes':strokes,'advance':(right-left+1)*scale,'height':float(height),'estimated_pressure':True}
