import {DatabaseSync} from 'node:sqlite';
import {mkdirSync,readFileSync,writeFileSync,renameSync,rmSync} from 'node:fs';
import {join,dirname,resolve,sep} from 'node:path';
import {randomUUID} from 'node:crypto';

export function openStorage(directory){
  mkdirSync(directory,{recursive:true,mode:0o700});
  const database=new DatabaseSync(join(directory,'folio.sqlite'));
  database.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;');
  database.exec(`CREATE TABLE IF NOT EXISTS folio_sessions (owner TEXT PRIMARY KEY,channel TEXT NOT NULL UNIQUE,secret TEXT NOT NULL,seen INTEGER NOT NULL DEFAULT 0);
  CREATE TABLE IF NOT EXISTS folio_tasks (id TEXT PRIMARY KEY,channel TEXT NOT NULL,state INTEGER NOT NULL,lease TEXT,created INTEGER NOT NULL,expires INTEGER NOT NULL);
  CREATE INDEX IF NOT EXISTS folio_tasks_channel_idx ON folio_tasks(channel,state,created);
  CREATE TABLE IF NOT EXISTS folio_profiles (owner TEXT NOT NULL,name TEXT NOT NULL,object TEXT NOT NULL,PRIMARY KEY(owner,name));
  CREATE TABLE IF NOT EXISTS oidc (model TEXT NOT NULL,id TEXT NOT NULL,payload TEXT NOT NULL,expires INTEGER,uid TEXT,user_code TEXT,grant_id TEXT,PRIMARY KEY(model,id));
  CREATE INDEX IF NOT EXISTS oidc_uid ON oidc(model,uid); CREATE INDEX IF NOT EXISTS oidc_user_code ON oidc(model,user_code); CREATE INDEX IF NOT EXISTS oidc_grant ON oidc(grant_id);
  CREATE TABLE IF NOT EXISTS web_sessions (id TEXT PRIMARY KEY,expires INTEGER NOT NULL);`);
  const DB={prepare(sql){let values=[];return {
    bind(...v){values=v;return this;},
    async first(){return database.prepare(sql).get(...values)||null;},
    async all(){return {results:database.prepare(sql).all(...values)};},
    async run(){return {meta:{changes:Number(database.prepare(sql).run(...values).changes)}};}
  };}};
  const root=resolve(directory,'objects');mkdirSync(root,{recursive:true,mode:0o700});
  function objectPath(key){
    if(typeof key!=='string'||key.length>250||!/^[A-Za-z0-9_/-]+$/.test(key))throw new Error('Invalid object key');
    const path=resolve(root,key);if(!path.startsWith(root+sep))throw new Error('Invalid object path');return path;
  }
  const BUCKET={
    async put(key,value){const path=objectPath(key);mkdirSync(dirname(path),{recursive:true,mode:0o700});const tmp=path+'.'+randomUUID()+'.tmp';try{writeFileSync(tmp,value,{mode:0o600});renameSync(tmp,path);}finally{rmSync(tmp,{force:true});}},
    async get(key){try{const value=readFileSync(objectPath(key),'utf8');return {text:async()=>value};}catch(e){if(e.code==='ENOENT')return null;throw e;}},
    async delete(keys){for(const key of Array.isArray(keys)?keys:[keys])rmSync(objectPath(key),{force:true});}
  };
  class OidcAdapter{
    constructor(model){this.model=model;}
    async upsert(id,payload,expiresIn){const expiry=typeof expiresIn==='number'?Math.floor(Date.now()/1000)+expiresIn:null;
      database.prepare('INSERT INTO oidc(model,id,payload,expires,uid,user_code,grant_id) VALUES(?,?,?,?,?,?,?) ON CONFLICT(model,id) DO UPDATE SET payload=excluded.payload,expires=excluded.expires,uid=excluded.uid,user_code=excluded.user_code,grant_id=excluded.grant_id').run(this.model,id,JSON.stringify(payload),expiry,payload.uid||null,payload.userCode||null,payload.grantId||null);}
    read(sql,value){const row=database.prepare(sql).get(this.model,value,Math.floor(Date.now()/1000));return row?JSON.parse(row.payload):undefined;}
    async find(id){return this.read('SELECT payload FROM oidc WHERE model=? AND id=? AND (expires IS NULL OR expires>?)',id);}
    async findByUid(uid){return this.read('SELECT payload FROM oidc WHERE model=? AND uid=? AND (expires IS NULL OR expires>?)',uid);}
    async findByUserCode(code){return this.read('SELECT payload FROM oidc WHERE model=? AND user_code=? AND (expires IS NULL OR expires>?)',code);}
    async destroy(id){database.prepare('DELETE FROM oidc WHERE model=? AND id=?').run(this.model,id);}
    async consume(id){const data=await this.find(id);if(data){data.consumed=Math.floor(Date.now()/1000);database.prepare('UPDATE oidc SET payload=? WHERE model=? AND id=?').run(JSON.stringify(data),this.model,id);}}
    async revokeByGrantId(id){database.prepare('DELETE FROM oidc WHERE grant_id=?').run(id);}
  }
  return {DB,BUCKET,database,OidcAdapter,close(){database.close();}};
}
