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
  if(direction>.88){result.push(current);continue;}
  const cut=Math.min(length1,length2)*.22;
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
 const globalX=1.10+random()*.05,globalY=.995+random()*.01;
 const baselineShift=(random()-.5)*.012,globalSlant=(random()-.5)*.016;
 const scalesX=[],scalesY=[],vertical=[],slants=[];
 for(let index=0;index<characters.length;index++){
  random=seeded(seed,token,offset,`letter:${index}`);
  scalesX.push(globalX*(.985+random()*.03));
  scalesY.push(globalY*(.985+random()*.03));
  vertical.push((random()-.5)*.024);
  slants.push(globalSlant+(random()-.5)*.016);
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

export function layout(text,x,y,height=28,width=2.2,color='#28344b',maxWidth=900,samples=null,variationSeed=''){
 if(typeof text!=='string'||!text.length||text.length>1500||![x,y,height,width,maxWidth].every(Number.isFinite)||height<6||height>240||maxWidth<height)throw new Error('Некорректный текст или размер надписи');
 const out=[],x0=x,referenceHeight=samples?profileReferenceHeight(samples):null;
 const sampleLabels=samples?Object.keys(samples).sort((a,b)=>b.length-a.length):[];
 let i=0,points=0,spaceScale=height;
 while(i<text.length){
  let token=text[i],glyph;
  if(token==='\n'){
   const random=samples&&variationSeed?seeded(variationSeed,text,i,'line'):null;
   x=x0;y+=height*(random?1.55+random()*.2:1.65);spaceScale=height;i++;continue;
  }
  if(/\s/.test(token)){
   const random=samples&&variationSeed?seeded(variationSeed,text[i],i,'space'):null;
   x+=spaceScale*(samples?(.20+(random?random()*.08:.04)):.65);i++;continue;
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
   if([...token].length>1)ratio=Math.max(.84,Math.min(1.18,ratio));
   glyphHeight=height*ratio;
  }
  const variation=samples&&variationSeed?personalVariation(glyph,token,variationSeed,i,1):null;
  const advance=glyph.advance*glyphHeight*(variation?.advanceScale??1);
  if(advance>maxWidth)throw new Error('Образец шире строки');
  if(x>x0&&x+advance>x0+maxWidth){x=x0;y+=height*1.65;spaceScale=height;}
  for(const path of glyph.paths){
   const renderPath=samples?roundPath(path):path;
   points+=renderPath.length;
   if(out.length>=2000||points>100000)throw new Error('Разделите надпись на части');
   out.push({tool:'fountain',color,width,points:renderPath.map(point=>{
    const transformed=variation?variation.transform(point):point;
    return [Math.round((x+transformed[0]*glyphHeight)*1000)/1000,Math.round((y+transformed[1]*glyphHeight)*1000)/1000,replayPressure(point[2])];
   })});
  }
  x+=advance;spaceScale=glyphHeight;i+=token.length;
 }
 if(!out.length)throw new Error('Пустая надпись');
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
