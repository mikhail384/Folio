import {test} from 'node:test';
import assert from 'node:assert/strict';
import {exportProfile,importProfile,validateProfile} from '../lib/profile-archive.mjs';

test('encrypted profile transfers across accounts and rejects incorrect passwords and changes',async()=>{
  const source={samples:{'Привет':{advance:2.8,paths:[[[0,0,1],[1,1,.5]]]},'я':{advance:1.1,paths:[[[0,.1,1],[.8,.9,.6]]]}}};
  const file=await exportProfile(source,'a strong secret');
  assert(!file.includes('Привет'));assert.deepEqual(JSON.parse(JSON.stringify(await importProfile(file,'a strong secret'))),source);
  await assert.rejects(importProfile(file,'wrong password'));
  const data=JSON.parse(file);data.ciphertext=(data.ciphertext[0]==='A'?'B':'A')+data.ciphertext.slice(1);
  await assert.rejects(importProfile(JSON.stringify(data),'a strong secret'));
  await assert.rejects(exportProfile(source,'short'));
});

test('malformed profile data is rejected before import',()=>{
  assert.throws(()=>validateProfile({samples:{'а':{advance:Infinity,paths:[[[0,0,1]]]}}}));
  assert.throws(()=>validateProfile({samples:{'а':{advance:1,paths:[[[0,0,99]]]}}}));
  assert.throws(()=>validateProfile(JSON.parse('{"samples":{"__proto__":{"advance":1,"paths":[[[0,0,1]]]}}}')));
});
