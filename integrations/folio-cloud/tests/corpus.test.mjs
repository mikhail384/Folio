import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {storeExamples,readExamples} from '../lib/corpus.mjs';

function environment(){
 const sqlite=new DatabaseSync(':memory:');sqlite.exec('CREATE TABLE folio_training_corpus(owner TEXT,name TEXT,id TEXT,object TEXT,PRIMARY KEY(owner,name,id))');
 const objects=new Map(),DB={prepare(sql){let args=[];return {bind(...x){args=x;return this;},async first(){return sqlite.prepare(sql).get(...args)||null;},async all(){return {results:sqlite.prepare(sql).all(...args)};},async run(){return {meta:{changes:Number(sqlite.prepare(sql).run(...args).changes)}};}};}};
 const BUCKET={async put(k,v){objects.set(k,v);},async get(k){return objects.has(k)?{text:async()=>objects.get(k)}:null;}};
 return {FOLIO_SERVICE_ACCESS:'test-service-access',sqlite,objects,DB,BUCKET};
}
const example=(text,overrides={})=>({document_id:'doc',page_id:'page',text,source:'calibration',reviewed:true,kind:'text',strokes:[{points:[0,0,.2,0,0,10,20,.7,0,0]}],...overrides});

test('encrypted reviewed examples preserve case, formula and owner isolation; retries are safe',async()=>{
 const e=environment(),items=[example('А'),example('а'),example('F = ma',{kind:'formula'}),example('guess',{source:'ocr',reviewed:false})];
 assert.deepEqual(await storeExamples(e,'alice','mine','doc',items),{added:3,skipped:1});
 assert.deepEqual(await storeExamples(e,'alice','mine','doc',items),{added:0,skipped:1});
 assert([...e.objects.values()].every(v=>!v.includes('F = ma')&&!v.includes('"points"')),'encrypted at rest');
 let cursor='',all=[];do{const page=await readExamples(e,'alice','mine',cursor,1);all.push(...page.examples);cursor=page.next_cursor;}while(cursor);
 assert.deepEqual(new Set(all.map(x=>x.text)),new Set(['А','а','F = ma']));assert.equal(all.find(x=>x.text==='F = ma').kind,'formula');
 assert.equal((await readExamples(e,'bob','mine')).examples.length,0);
 await assert.rejects(storeExamples(e,'alice','mine','doc',[example('bad',{strokes:[{action:'ai:test',points:[0,0,.5,0,0]}]})]));
 await assert.rejects(storeExamples(e,'alice','mine','doc',[example('bad',{document_id:'other'})]));
});
