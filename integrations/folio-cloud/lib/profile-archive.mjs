// Portable handwriting profiles. A password is used only in the user's browser.
const encoder = new TextEncoder(), decoder = new TextDecoder();
const FORMAT = 'folio-handwriting-portable-v1', ITERATIONS = 310000;
const BAD_KEYS = new Set(['__proto__', 'prototype', 'constructor']);
const to64 = bytes => { let s=''; for (let i=0;i<bytes.length;i+=8192) s+=String.fromCharCode(...bytes.subarray(i,i+8192)); return btoa(s).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,''); };
const from64 = value => {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Повреждён файл почерка');
  const s=value.replaceAll('-','+').replaceAll('_','/');
  return Uint8Array.from(atob(s+'='.repeat((4-s.length%4)%4)), c=>c.charCodeAt(0));
};

export function validateProfile(profile) {
  if (!profile || typeof profile !== 'object' || Array.isArray(profile) || !profile.samples || typeof profile.samples !== 'object' || Array.isArray(profile.samples)) throw new Error('Повреждён профиль почерка');
  const entries=Object.entries(profile.samples);
  if (entries.length > 512) throw new Error('Слишком много образцов');
  let count=0; const samples=Object.create(null);
  for (const [label,glyph] of entries) {
    if (!label || label.length>32 || /[\u0000-\u001f\u007f]/.test(label) || BAD_KEYS.has(label) || !glyph || typeof glyph !== 'object' || !Number.isFinite(glyph.advance) || glyph.advance<=0 || glyph.advance>10000 || !Array.isArray(glyph.paths) || !glyph.paths.length || glyph.paths.length>128) throw new Error('Повреждён образец почерка');
    const paths=glyph.paths.map(path=>{
      if (!Array.isArray(path) || !path.length) throw new Error('Пустой штрих в образце');
      count+=path.length; if (count>150000) throw new Error('Профиль слишком большой');
      return path.map(p=>{
        if (!Array.isArray(p) || p.length!==3 || !p.every(Number.isFinite) || Math.abs(p[0])>10000 || Math.abs(p[1])>10000 || p[2]<0 || p[2]>2) throw new Error('Повреждены точки образца');
        return [p[0],p[1],p[2]];
      });
    });
    samples[label]={advance:glyph.advance,paths};
  }
  if (JSON.stringify(samples).length>4*1024*1024) throw new Error('Профиль слишком большой');
  return {samples};
}

async function derive(password,salt,iterations) {
  if (typeof password!=='string' || password.length<8 || password.length>512) throw new Error('Пароль должен содержать не менее 8 символов');
  const material=await crypto.subtle.importKey('raw',encoder.encode(password),'PBKDF2',false,['deriveKey']);
  return crypto.subtle.deriveKey({name:'PBKDF2',hash:'SHA-256',salt,iterations},material,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
}
export async function exportProfile(profile,password) {
  const clean=validateProfile(profile),salt=crypto.getRandomValues(new Uint8Array(16)),iv=crypto.getRandomValues(new Uint8Array(12));
  const key=await derive(password,salt,ITERATIONS);
  const ciphertext=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:encoder.encode(FORMAT)},key,encoder.encode(JSON.stringify(clean)));
  return JSON.stringify({format:FORMAT,kdf:'PBKDF2-SHA256',iterations:ITERATIONS,salt:to64(salt),iv:to64(iv),ciphertext:to64(new Uint8Array(ciphertext))});
}
export async function importProfile(raw,password) {
  if (typeof raw!=='string' || raw.length>8*1024*1024) throw new Error('Файл слишком большой');
  let data; try { data=JSON.parse(raw); } catch { throw new Error('Файл почерка не читается'); }
  if (!data || data.format!==FORMAT || data.kdf!=='PBKDF2-SHA256' || !Number.isInteger(data.iterations) || data.iterations<210000 || data.iterations>600000) throw new Error('Неподдерживаемый файл почерка');
  const salt=from64(data.salt),iv=from64(data.iv),ciphertext=from64(data.ciphertext);
  if (salt.length!==16 || iv.length!==12 || ciphertext.length<16) throw new Error('Повреждён файл почерка');
  const key=await derive(password,salt,data.iterations);
  let plain; try { plain=await crypto.subtle.decrypt({name:'AES-GCM',iv,additionalData:encoder.encode(FORMAT)},key,ciphertext); } catch { throw new Error('Неверный пароль или повреждён файл почерка'); }
  try { return validateProfile(JSON.parse(decoder.decode(plain))); } catch(e) { if (e instanceof SyntaxError) throw new Error('Повреждён профиль почерка'); throw e; }
}
