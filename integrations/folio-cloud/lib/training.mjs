// Samples originate on the paired tablet and are encrypted with the channel key.
import {ApiError,digest,getProfile,open,storeProfile} from './relay.mjs';
import {sample} from './handwriting.mjs';
import {validateProfile} from './profile-archive.mjs';

export async function importTrainingSample(env,session,body){
  if(!/^[a-f0-9]{32}$/.test(body.id||''))throw new ApiError(400,'Некорректный идентификатор образца');
  let data;try{data=await open(session.key,body.envelope,'folio:training:'+body.id);}catch{throw new ApiError(400,'Не удалось проверить образец');}
  const label=data?.text,strokes=data?.strokes;
  if(typeof label!=='string'||!label.length||label.length>32||/[\u0000-\u001f\u007f]/.test(label)||['__proto__','constructor','prototype'].includes(label)||!Array.isArray(strokes)||!strokes.length||strokes.length>128)throw new ApiError(400,'Некорректный образец');
  let count=0;
  for(const stroke of strokes){
    const points=stroke?.points;
    if(!Array.isArray(points)||points.length<5||points.length%5)throw new ApiError(400,'Некорректные штрихи образца');
    count+=points.length/5;if(count>16000)throw new ApiError(413,'Слишком большой образец');
    for(let i=0;i<points.length;i+=5){
      if(!points.slice(i,i+5).every(Number.isFinite)||Math.abs(points[i])>10000000||Math.abs(points[i+1])>10000000||points[i+2]<0||points[i+2]>2)throw new ApiError(400,'Некорректные точки образца');
    }
  }
  const hash=await digest(JSON.stringify({text:label,strokes})),key='training-receipts/'+session.channel+'/'+body.id;
  const receipt=await env.BUCKET.get(key);
  if(receipt){const saved=JSON.parse(await receipt.text());if(saved.hash!==hash)throw new ApiError(409,'Образец с таким идентификатором уже сохранён');return {id:body.id,stored:true,already_saved:true};}
  let glyph;try{glyph=sample(strokes);}catch(e){throw new ApiError(400,e.message||'Напишите образец крупнее');}
  const current=validateProfile(await getProfile(env,session.owner,'my-handwriting'));
  const clean=validateProfile({samples:{...current.samples,[label]:glyph}});
  await storeProfile(env,session.owner,'my-handwriting',clean);
  await env.BUCKET.put(key,JSON.stringify({hash}));
  return {id:body.id,stored:true,profile:'my-handwriting'};
}
