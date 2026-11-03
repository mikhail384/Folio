// Reviewed, case-sensitive handwriting examples. Individual encrypted objects
// are content-addressed so retries do not replace another example or profile.
import {ApiError,digest,seal,open} from './relay.mjs';

function name(value){if(typeof value!=='string'||!/^[A-Za-z0-9_-]{1,48}$/.test(value))throw new ApiError(400,'Некорректный профиль');return value;}
function sourceId(value){if(typeof value!=='string'||!/^[A-Za-z0-9_-]{1,80}$/.test(value))throw new ApiError(400,'Некорректный источник');return value;}
function clean(raw,document){
 if(!raw||raw.document_id!==document||raw.reviewed!==true||!['calibration','ocr'].includes(raw.source))throw new ApiError(400,'Образец не подтверждён');
 const text=typeof raw.text==='string'?raw.text.normalize('NFC'):'';
 if(!text.trim()||text.length>64||/[\x00-\x1f\x7f]/.test(text)||!['text','formula','unknown'].includes(raw.kind))throw new ApiError(400,'Некорректная подпись');
 if(!Array.isArray(raw.strokes)||!raw.strokes.length||raw.strokes.length>128)throw new ApiError(400,'Некорректный образец');
 let total=0;const strokes=raw.strokes.map(stroke=>{
   if(!stroke||String(stroke.action||'').startsWith('ai:')||stroke.cuts?.length||stroke.tool&&!['pen','fountain','brush','pencil'].includes(stroke.tool))throw new ApiError(400,'Нужны рукописные штрихи');
   const p=stroke.points;if(!Array.isArray(p)||!p.length||p.length%5)throw new ApiError(400,'Повреждены точки');total+=p.length/5;if(total>16000)throw new ApiError(413,'Слишком большой образец');
   for(let i=0;i<p.length;i+=5)if(!p.slice(i,i+5).every(Number.isFinite)||Math.abs(p[i])>1e7||Math.abs(p[i+1])>1e7||p[i+2]<0||p[i+2]>2)throw new ApiError(400,'Повреждены точки');
   return {points:p};
 });
 return {format:'folio-training-example-v1',text,kind:raw.kind,source:raw.source==='ocr'?'ocr-reviewed':'manual',document_id:sourceId(document),page_id:sourceId(raw.page_id),strokes};
}
async function key(env){if(!env.FOLIO_SERVICE_ACCESS)throw new ApiError(503,'Сервис не настроен');return digest('folio-storage-v1:'+env.FOLIO_SERVICE_ACCESS);}

export async function storeExamples(env,uid,profile,document,items){
 name(profile);sourceId(document);if(!Array.isArray(items)||items.length>12)throw new ApiError(400,'Некорректная страница образцов');let added=0,skipped=0;const prepared=[];
 for(const raw of items){if(raw?.reviewed!==true||!['calibration','ocr'].includes(raw?.source)){skipped++;continue;}prepared.push(clean(raw,document));}
 for(const item of prepared){
   const id=await digest(JSON.stringify(item)),object='training-corpus/'+await digest(uid)+'/'+profile+'/'+id;
   const prior=await env.DB.prepare('SELECT id FROM folio_training_corpus WHERE owner=? AND name=? AND id=?').bind(uid,profile,id).first();if(prior)continue;
   await env.BUCKET.put(object,JSON.stringify(await seal(await key(env),item,'corpus:'+uid+':'+profile+':'+id)));
   const result=await env.DB.prepare('INSERT OR IGNORE INTO folio_training_corpus(owner,name,id,object) VALUES(?,?,?,?)').bind(uid,profile,id,object).run();added+=Number(result.meta.changes);
 }
 return {added,skipped};
}

export async function readExamples(env,uid,profile,cursor='',limit=4){
 name(profile);if(cursor&&!/^[A-Za-z0-9_-]{43}$/.test(cursor))throw new ApiError(400,'Некорректный курсор');
 if(!Number.isInteger(limit)||limit<1||limit>4)throw new ApiError(400,'Некорректный размер страницы');
 const rows=await env.DB.prepare('SELECT id,object FROM folio_training_corpus WHERE owner=? AND name=? AND id>? ORDER BY id LIMIT ?').bind(uid,profile,cursor,limit+1).all();
 const examples=[];let last='';for(const row of (rows.results||[]).slice(0,limit)){
   const value=await env.BUCKET.get(row.object);if(!value)throw new ApiError(500,'Образец недоступен');
   examples.push(await open(await key(env),JSON.parse(await value.text()),'corpus:'+uid+':'+profile+':'+row.id));last=row.id;
 }
 const more=(rows.results||[]).length>limit;return {profile,examples,has_more:more,next_cursor:more?last:''};
}
