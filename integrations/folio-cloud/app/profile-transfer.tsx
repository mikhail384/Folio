'use client';
import {useState} from 'react';
import {exportProfile,importProfile} from '../lib/profile-archive.mjs';

export default function ProfileTransfer(){
  const [name,setName]=useState('my-handwriting'),[password,setPassword]=useState(''),[file,setFile]=useState<File|null>(null),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
  async function transfer(mode:'export'|'import'){
    setBusy(true);setMessage('');
    try{
      if(!/^[A-Za-z0-9_-]{1,48}$/.test(name))throw new Error('В имени используйте латиницу, цифры, дефис и подчёркивание');
      if(mode==='export'){
        const response=await fetch('/api/profiles?name='+encodeURIComponent(name),{cache:'no-store'}),data:any=await response.json();
        if(!response.ok)throw new Error(data.error||'Не удалось открыть профиль');
        if(!Object.keys(data.profile.samples).length)throw new Error('В этом профиле пока нет образцов');
        const archive=await exportProfile(data.profile,password),url=URL.createObjectURL(new Blob([archive],{type:'application/json'}));
        const link=document.createElement('a');link.href=url;link.download=name+'.folio-handwriting';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
        setMessage('Файл сохранён. Для импорта на другом аккаунте понадобится этот пароль.');
      } else {
        if(!file)throw new Error('Выберите файл почерка');
        if(file.size>8*1024*1024)throw new Error('Файл слишком большой');
        const profile=await importProfile(await file.text(),password);
        const response=await fetch('/api/profiles',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,profile})}),data:any=await response.json();
        if(!response.ok)throw new Error(data.error||'Не удалось импортировать профиль');
        setMessage(`Добавлено образцов: ${data.imported}. Всего в профиле: ${data.total}.`);
      }
      setPassword('');
    }catch(e:any){setMessage(e.message||'Не удалось перенести профиль');}finally{setBusy(false);}
  }
  return <section className="profile-transfer"><div><span className="eyebrow">ПОЧЕРК С ВАМИ</span><h2>Перенос профиля</h2><p>Сохраните образцы в защищённый паролем файл и откройте его в другом аккаунте. Совпадающие образцы при импорте обновятся. Для автоматических находок используйте имя my-handwriting-auto; подтверждённые вручную образцы находятся в my-handwriting.</p></div><div className="profile-form"><label>Имя профиля<input value={name} onChange={e=>setName(e.target.value)} autoComplete="off"/></label><label>Пароль файла<input type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete="new-password" placeholder="Не менее 8 символов"/></label><label>Файл для импорта<input type="file" accept=".folio-handwriting,application/json" onChange={e=>setFile(e.target.files?.[0]||null)}/></label><div className="profile-actions"><button disabled={busy} onClick={()=>transfer('export')}>Скачать профиль</button><button disabled={busy} onClick={()=>transfer('import')}>Импортировать</button></div>{message&&<p role="status">{message}</p>}</div></section>;
}
