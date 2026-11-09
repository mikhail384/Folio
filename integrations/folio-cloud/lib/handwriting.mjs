import font from './font.json' with {type:'json'};

// Folio 1.18 compares float pressure with a double minimum of 0.02.
const replayPressure=p=>Math.max(0.02000001,Math.min(2,Number.isFinite(p)?p:1));
const seeded=(seed,token,offset,part)=>{
 let hash=2166136261;
 for(const character of `${seed}\0${token}\0${offset}\0${part}`){hash^=character.charCodeAt(0);hash=Math.imul(hash,16777619);}
 return ()=>{hash^=hash<<13;hash^=hash>>>17;hash^=hash<<5;return (hash>>>0)/4294967296;};
};
const letterWeight=character=>/\s/.test(character)? .58 : (!/[\p{L}\p{N}]/u.test(character)? .55 : ('iljtїі'.includes(character.toLowerCase())? .72 : ('mwшщжф'.includes(character.toLowerCase())?1.28:1)));
function roundPath(path){
 if(path.length<3)return path;
 const result=[path[0]];
 for(let index=1;index<path.length-1;index++){
  const before=path[index-1],current=path[index],after=path[index+1];
  const dx1=current[0]-before[0],dy1=current[1]-before[1],dx2=after[0]-current[0],dy2=after[1]-current[1];
  const length1=Math.hypot(dx1,dy1),length2=Math.hypot(dx2,dy2);
  if(length1<1e-6||length2<1e-6){result.push(current);continue;}
  const ux1=dx1/length1,uy1=dy1/length1,ux2=dx2/length2,uy2=dy2/length2;
  const direction=Math.max(-1,Math.min(1,ux1*ux2+uy1*uy2));
  if(direction>.95){result.push(current);continue;}
  const cut=Math.min(length1,length2)*.34;
  const entry=[current[0]-ux1*cut,current[1]-uy1*cut,current[2]+(before[2]-current[2])*(cut/length1)];
  const exit=[current[0]+ux2*cut,current[1]+uy2*cut,current[2]+(after[2]-current[2])*(cut/length2)];
  result.push(entry);
  for(const t of [.25,.5,.75,1]){
   const inv=1-t;
   result.push([0,1,2].map(axis=>inv*inv*entry[axis]+2*inv*t*current[axis]+t*t*exit[axis]));
  }
 }
 result.push(path.at(-1));
 return result;
}
function softenDensePath(path,radius=.07){
 if(path.length<4)return path;
 const distances=[0];
 for(let i=1;i<path.length;i++)distances.push(distances.at(-1)+Math.hypot(path[i][0]-path[i-1][0],path[i][1]-path[i-1][1]));
 if(distances.at(-1)<radius*1.5)return path;
 const result=[];let left=0,right=0;
 for(let index=0;index<path.length;index++){
  const center=distances[index];
  if(index===0||index===path.length-1){result.push(path[index]);continue;}
  const lower=center-radius,upper=center+radius;
  while(left<path.length-1&&distances[left]<lower)left++;
  right=Math.max(right,index);
  while(right<path.length-1&&distances[right+1]<=upper)right++;
  const sums=[0,0,0];let total=0;
  for(let pointIndex=left;pointIndex<=right;pointIndex++){
   const weight=Math.max(0,1-Math.abs(distances[pointIndex]-center)/radius),point=path[pointIndex];
   for(let axis=0;axis<3;axis++)sums[axis]+=point[axis]*weight;
   total+=weight;
  }
  result.push(total?sums.map(value=>value/total):path[index]);
 }
 return result;
}
function trimShortHooks(path,maxArc=.34,minHeight=.07,maxWidth=.04,aspect=2.5,chordRatio=.25,maxSteps=48){
 if(path.length<5)return path;
 const distances=[0];
 for(let i=1;i<path.length;i++)distances.push(distances.at(-1)+Math.hypot(path[i][0]-path[i-1][0],path[i][1]-path[i-1][1]));
 const result=[path[0]];let index=1;
 while(index<path.length-1){
  const start=index-1;let found=-1,end=index+1;
  let minX=path[start][0],maxX=path[start][0],minY=path[start][1],maxY=path[start][1];
  while(end<path.length-1&&end<=start+maxSteps&&distances[end]-distances[start]<=maxArc){
   const point=path[end];minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);minY=Math.min(minY,point[1]);maxY=Math.max(maxY,point[1]);
   const arc=distances[end]-distances[start],chord=Math.hypot(path[end][0]-path[start][0],path[end][1]-path[start][1]);
   const spanX=maxX-minX,spanY=maxY-minY,rises=minY<Math.min(path[start][1],path[end][1])-minHeight;
   if(arc>minHeight*2&&chord<arc*chordRatio&&spanX<maxWidth&&spanY>minHeight&&spanY>aspect*Math.max(spanX,1e-6)&&rises){found=end;break;}
   end++;
  }
  if(found<0){result.push(path[index]);index++;}
  else{result.push(path[found]);index=found+1;}
 }
 result.push(path.at(-1));
 return result;
}
const median=values=>{
 if(!values.length)return null;
 values.sort((a,b)=>a-b);
 const middle=Math.floor(values.length/2);
 return values.length%2?values[middle]:(values[middle-1]+values[middle])/2;
};
function profileReferenceHeight(samples){
 const wordHeights=[],allHeights=[];
 for(const [label,value] of Object.entries(samples||{})){
  const variants=Array.isArray(value?.variants)?value.variants:[value];
  const heights=variants.map(item=>item?.natural_height).filter(height=>Number.isFinite(height)&&height>0);
  const labelHeight=median(heights);
  if(!labelHeight)continue;
  allHeights.push(labelHeight);
  const letters=[...label].filter(character=>/\p{L}/u.test(character));
  const uppercase=letters.filter(character=>character===character.toUpperCase()).length;
  if(letters.length>=2&&letters.some(character=>character!==character.toUpperCase())&&uppercase/letters.length<=.34)wordHeights.push(labelHeight);
 }
 return median(wordHeights.length?wordHeights:allHeights);
}
function personalVariation(glyph,token,seed,offset,baseline){
 const points=glyph.paths.flat();
 if(!points.length)return null;
 const left=Math.min(...points.map(point=>point[0])),right=Math.max(...points.map(point=>point[0])),total=right-left;
 if(total<1e-6)return null;
 const characters=[...token];
 if(!characters.length)characters.push(' ');
 const weights=characters.map(letterWeight),weightTotal=weights.reduce((a,b)=>a+b,0),edges=[left];
 let cursor=left;
 for(const weight of weights){cursor+=total*weight/weightTotal;edges.push(cursor);}
 let random=seeded(seed,token,offset,'word');
 const longWordExpansion=Math.min(.08,Math.max(0,characters.length-9)*.015);
 const globalX=1.08+random()*.04+longWordExpansion,globalY=.995+random()*.01;
 const baselineShift=(random()-.5)*.012,globalSlant=(random()-.5)*.016;
 const scalesX=[],scalesY=[],vertical=[],slants=[],variationScale=Math.min(1,8/characters.length);
 for(let index=0;index<characters.length;index++){
  random=seeded(seed,token,offset,`letter:${index}`);
  scalesX.push(globalX*(1-.008*variationScale+random()*.016*variationScale));
  // A multi-letter sample already contains the writer's letter proportions.
  // Guessing boundaries inside connected handwriting can deform a captured е.
  scalesY.push(globalY*(.995+random()*.01*variationScale));
  vertical.push((random()-.5)*.012*variationScale);
  slants.push(globalSlant+(random()-.5)*.008*variationScale);
 }
 const edgeOffsets=[0];
 for(let index=0;index<characters.length;index++)edgeOffsets.push(edgeOffsets[index]+(edges[index+1]-edges[index])*(scalesX[index]-1));
 const advanceScale=1+edgeOffsets.at(-1)/total,centers=characters.map((_,index)=>(edges[index]+edges[index+1])*.5);
 const blend=(values,positions,x)=>{
  if(values.length===1||x<=positions[0])return values[0];
  if(x>=positions.at(-1))return values.at(-1);
  for(let index=0;index<positions.length-1;index++)if(x<=positions[index+1]){
   let t=(x-positions[index])/(positions[index+1]-positions[index]);
   t=t*t*(3-2*t);
   return values[index]*(1-t)+values[index+1]*t;
  }
  return values.at(-1);
 };
 const transform=point=>{
  const px=point[0],py=point[1];
  const warpedX=px+blend(edgeOffsets,edges,px);
  const warpedY=baseline+(py-baseline)*blend(scalesY,centers,px)+blend(vertical,centers,px);
  return [warpedX+(baseline-py)*blend(slants,centers,px),warpedY+baselineShift,advanceScale];
 };
 return {transform,advanceScale};
}

export function layout(text,x,y,height=28,width=2.2,color='#28344b',maxWidth=900,samples=null,variationSeed='',returnMetrics=false){
 if(typeof text!=='string'||!text.length||text.length>1500||![x,y,height,width,maxWidth].every(Number.isFinite)||height<6||height>240||maxWidth<height)throw new Error('Некорректный текст или размер надписи');
 const out=[],tokenMetrics=[],x0=x,referenceHeight=samples?profileReferenceHeight(samples):null;
 const sampleLabels=samples?Object.keys(samples).sort((a,b)=>b.length-a.length):[];
 let i=0,points=0,spaceScale=height,lineIndex=0,wordIndex=0,inWord=false;
 while(i<text.length){
  let token=text[i],glyph;
  if(token==='\n'){
   const random=samples&&variationSeed?seeded(variationSeed,text,i,'line'):null;
   x=x0;y+=height*(random?1.55+random()*.2:1.65);spaceScale=height;lineIndex++;inWord=false;i++;continue;
  }
  if(/\s/.test(token)){
   const random=samples&&variationSeed?seeded(variationSeed,text[i],i,'space'):null;
   x+=spaceScale*(samples?(.38+(random?random()*.08:.04)):.65);inWord=false;i++;continue;
  }
  if(samples){
   token=sampleLabels.find(label=>text.startsWith(label,i));
   if(!token)throw new Error('Для вашего почерка нет образца: '+text[i]);
   glyph=samples[token];
   if(Array.isArray(glyph.variants)&&glyph.variants.length){
    const choose=seeded(variationSeed,token,i,'variant');
    glyph=glyph.variants[Math.floor(choose()*glyph.variants.length)];
   }
  }else{
   const shape=font[token]||font[token.toUpperCase()];
   if(!shape)throw new Error('Нет образца символа: '+token);
   const lower=token!==token.toUpperCase();
   glyph={advance:lower?1.2:1.3,paths:shape.split('|').map(path=>path.split(' ').map(word=>{
    let [px,py]=word.split(',').map(Number);
    py=py/10*(lower?.75:1)+(lower?.25:0);
    return [px/10+.12*(1-py),py,1];
   }))};
  }
  let glyphHeight=height;
  if(samples&&referenceHeight&&Number.isFinite(glyph.natural_height)&&glyph.natural_height>0){
   let ratio=glyph.natural_height/referenceHeight;
   if([...token].length>1)ratio=Math.max(.96,Math.min(1.04,ratio));
   glyphHeight=height*ratio;
  }
  const variation=samples&&variationSeed?personalVariation(glyph,token,variationSeed,i,1):null;
  const advance=glyph.advance*glyphHeight*(variation?.advanceScale??1);
  if(advance>maxWidth)throw new Error('Образец шире строки');
  if(x>x0&&x+advance>x0+maxWidth){x=x0;y+=height*1.65;spaceScale=height;lineIndex++;}
  if(!inWord)wordIndex++;inWord=true;const currentWord=wordIndex;
  const tokenStart=out.length;
  const renderPaths=samples?glyph.paths.map(path=>roundPath(softenDensePath(trimShortHooks(path)))):glyph.paths;
  let wordVerticalScale=1;
  if(samples&&token.length>1){
   const wordY=renderPaths.flatMap(path=>path.map(point=>(variation?variation.transform(point):point)[1]));
   const currentHeight=(Math.max(...wordY)-Math.min(...wordY))*glyphHeight,targetHeight=height*.95;
   if(currentHeight>targetHeight&&currentHeight>1e-6)wordVerticalScale=targetHeight/currentHeight;
  }
  for(const renderPath of renderPaths){
   points+=renderPath.length;
   if(out.length>=2000||points>100000)throw new Error('Разделите надпись на части');
   out.push({tool:'fountain',color,width,points:renderPath.map(point=>{
    const transformed=variation?variation.transform(point):point;
    const rawY=y+transformed[1]*glyphHeight,baseline=y+height,adjustedY=baseline+(rawY-baseline)*wordVerticalScale;
    return [Math.round((x+transformed[0]*glyphHeight)*1000)/1000,Math.round(adjustedY*1000)/1000,replayPressure(point[2])];
   })});
  }
  if(returnMetrics){let l=Infinity,t=Infinity,r=-Infinity,b=-Infinity;for(let j=tokenStart;j<out.length;j++)for(const point of out[j].points){l=Math.min(l,point[0]);t=Math.min(t,point[1]);r=Math.max(r,point[0]);b=Math.max(b,point[1]);}if(l<Infinity)tokenMetrics.push({text:token,line:lineIndex,word:currentWord,bounds:[l,t,r,b]});}
  x+=advance;spaceScale=glyphHeight;i+=token.length;
 }
 if(!out.length)throw new Error('Пустая надпись');
 if(returnMetrics){let l=Infinity,t=Infinity,r=-Infinity,b=-Infinity;for(const stroke of out)for(const point of stroke.points){l=Math.min(l,point[0]);t=Math.min(t,point[1]);r=Math.max(r,point[0]);b=Math.max(b,point[1]);}return {strokes:out,metrics:{text,bounds:[l,t,r,b],line_count:lineIndex+1,tokens:tokenMetrics}};}
 return out;
}

export function sample(strokes){
 const paths=strokes.map(stroke=>{
  const points=[];
  for(let i=0;i+2<stroke.points.length;i+=5)points.push([stroke.points[i],stroke.points[i+1],stroke.points[i+2]]);
  return points;
 });
 const points=paths.flat();
 if(!points.length)throw new Error('Выделите образец лассо');
 const left=Math.min(...points.map(point=>point[0])),top=Math.min(...points.map(point=>point[1]));
 const height=Math.max(...points.map(point=>point[1]))-top,width=Math.max(...points.map(point=>point[0]))-left;
 if(height<1||width<.1)throw new Error('Образец слишком мал');
 return {advance:width/height+.25,natural_height:Math.round(height*10000)/10000,paths:paths.map(path=>path.map(point=>[(point[0]-left)/height,(point[1]-top)/height,point[2]]))};
}
