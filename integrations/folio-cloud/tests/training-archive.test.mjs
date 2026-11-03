import {test} from 'node:test';
import assert from 'node:assert/strict';
import {exportExamples,importExamples} from '../lib/training-archive.mjs';

test('training archive encrypts multiple case-sensitive examples and rejects wrong passwords',async()=>{
 const examples=['А','а','F = ma'].map(text=>({format:'folio-training-example-v1',text,kind:text==='F = ma'?'formula':'text',source:'manual',document_id:'doc',page_id:'page',strokes:[{points:[0,0,.5,0,0,10,20,.8,0,0]}]}));
 const archived=await exportExamples(examples,'a long enough password');assert(!archived.includes('F = ma'));
 assert.deepEqual(await importExamples(archived,'a long enough password'),examples);
 await assert.rejects(importExamples(archived,'incorrect password'));
 await assert.rejects(exportExamples(examples,'short'));
});
