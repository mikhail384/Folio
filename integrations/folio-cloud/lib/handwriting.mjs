import font from './font.json' with {type:'json'};
// The installed Folio 1.18 compares float pressure with the double 0.02.
// Keep the minimum just above that boundary until those tablets are updated.
const replayPressure=p=>Math.max(0.02000001,Math.min(2,Number.isFinite(p)?p:1));
export function layout(text,x,y,height=28,width=2.2,color='#28344b',maxWidth=900,samples=null){if(typeof text!=='string'||!text.length||text.length>1500||![x,y,height,width,maxWidth].every(Number.isFinite)||height<6||height>240||maxWidth<height)throw new Error('Некорректный текст или размер надписи');const out=[],x0=x;let i=0,points=0;
 while(i<text.length){let token=text[i],glyph;if(token==='\n'){x=x0;y+=height*1.65;i++;continue;}if(/\s/.test(token)){x+=height*.65;i++;continue;}
 if(samples){token=Object.keys(samples).sort((a,b)=>b.length-a.length).find(k=>text.startsWith(k,i));if(!token)throw new Error('Для вашего почерка нет образца: '+text[i]);glyph=samples[token];}
 else{const shape=font[token]||font[token.toUpperCase()];if(!shape)throw new Error('Нет образца символа: '+token);const lower=token!==token.toUpperCase();glyph={advance:lower?1.2:1.3,paths:shape.split('|').map(path=>path.split(' ').map(word=>{let [px,py]=word.split(',').map(Number);py=py/10*(lower?.75:1)+(lower?.25:0);return [px/10+.12*(1-py),py,1];}))};}
 const advance=glyph.advance*height;if(advance>maxWidth)throw new Error('Образец шире строки');if(x>x0&&x+advance>x0+maxWidth){x=x0;y+=height*1.65;}for(const path of glyph.paths){points+=path.length;if(out.length>=2000||points>100000)throw new Error('Разделите надпись на части');out.push({tool:'fountain',color,width,points:path.map(p=>[Math.round((x+p[0]*height)*1000)/1000,Math.round((y+p[1]*height)*1000)/1000,replayPressure(p[2])])});}x+=advance;i+=token.length;
 }if(!out.length)throw new Error('Пустая надпись');return out;
}
export function sample(strokes){const paths=strokes.map(s=>{const out=[];for(let i=0;i<s.points.length;i+=5)out.push([s.points[i],s.points[i+1],s.points[i+2]]);return out;});const pts=paths.flat();if(!pts.length)throw new Error('Выделите образец лассо');const x=Math.min(...pts.map(p=>p[0])),y=Math.min(...pts.map(p=>p[1])),h=Math.max(...pts.map(p=>p[1]))-y,w=Math.max(...pts.map(p=>p[0]))-x;if(h<1||w<.1)throw new Error('Образец слишком мал');return {advance:w/h+.25,paths:paths.map(path=>path.map(p=>[(p[0]-x)/h,(p[1]-y)/h,p[2]]))};}
