// Encryption/decryption runs in the owner's browser; passwords stay there.
const FORMAT='folio-training-portable-v1',ITERATIONS=310000,encoder=new TextEncoder(),decoder=new TextDecoder();
const to64=bytes=>{let s='';for(let i=0;i<bytes.length;i+=8192)s+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(s).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');};
const from64=s=>{if(typeof s!=='string'||!/^[A-Za-z0-9_-]+$/.test(s))throw new Error('Повреждён файл образцов');const p=s.replaceAll('-','+').replaceAll('_','/');return Uint8Array.from(atob(p+'='.repeat((4-p.length%4)%4)),c=>c.charCodeAt(0));};
async function derive(password,salt,iterations){
 if(typeof password!=='string'||password.length<8||password.length>512)throw new Error('Пароль должен содержать не менее 8 символов');
 const material=await crypto.subtle.importKey('raw',encoder.encode(password),'PBKDF2',false,['deriveKey']);
 return crypto.subtle.deriveKey({name:'PBKDF2',hash:'SHA-256',salt,iterations},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
}
function validate(examples){
 if(!Array.isArray(examples)||!examples.length||examples.length>10000)throw new Error('Нет образцов или их слишком много');
 for(const e of examples)if(!e||e.format!=='folio-training-example-v1'||typeof e.text!=='string'||typeof e.document_id!=='string'||typeof e.page_id!=='string'||!['manual','ocr-reviewed'].includes(e.source)||!['text','formula','unknown'].includes(e.kind)||!Array.isArray(e.strokes))throw new Error('Повреждён образец');
 const text=JSON.stringify(examples);if(encoder.encode(text).length>24*1024*1024)throw new Error('Пока можно перенести не более 24 МБ за один раз');return text;
}
export async function exportExamples(examples,password){
 const text=validate(examples),salt=crypto.getRandomValues(new Uint8Array(16)),iv=crypto.getRandomValues(new Uint8Array(12));
 const encrypted=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:encoder.encode(FORMAT)},await derive(password,salt,ITERATIONS),encoder.encode(text));
 return JSON.stringify({format:FORMAT,kdf:'PBKDF2-SHA256',iterations:ITERATIONS,salt:to64(salt),iv:to64(iv),ciphertext:to64(new Uint8Array(encrypted))});
}
export async function importExamples(raw,password){
 if(typeof raw!=='string'||raw.length>40*1024*1024)throw new Error('Файл слишком большой');let value;try{value=JSON.parse(raw);}catch{throw new Error('Файл не читается');}
 if(value?.format!==FORMAT||value.kdf!=='PBKDF2-SHA256'||!Number.isInteger(value.iterations)||value.iterations<210000||value.iterations>600000)throw new Error('Неподдерживаемый файл');
 const salt=from64(value.salt),iv=from64(value.iv),ciphertext=from64(value.ciphertext);if(salt.length!==16||iv.length!==12||ciphertext.length<16)throw new Error('Повреждён файл');
 let plain;try{plain=await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:encoder.encode(FORMAT)},await derive(password,salt,value.iterations),ciphertext);}catch{throw new Error('Неверный пароль или повреждён файл');}
 try{const examples=JSON.parse(decoder.decode(plain));validate(examples);return examples;}catch{throw new Error('Повреждены образцы');}
}
