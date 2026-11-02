import {env} from 'cloudflare:workers';
import {owner,getProfile,listProfiles,storeProfile,json,failure,readBody,ApiError} from '../../../lib/relay.mjs';
import {validateProfile} from '../../../lib/profile-archive.mjs';
export const dynamic='force-dynamic';

export async function GET(request:Request) {
  try {
    const uid=owner(request),name=new URL(request.url).searchParams.get('name');
    if (!name) return json(await listProfiles(env,uid));
    return json({name,profile:validateProfile(await getProfile(env,uid,name))});
  } catch(error) { return failure(error); }
}

export async function POST(request:Request) {
  try {
    const uid=owner(request);
    if (request.headers.get('origin')!==new URL(request.url).origin || !request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) throw new ApiError(403,'Импорт разрешён только с сайта Folio');
    const body=await readBody(request),name=body.name;
    if (typeof name!=='string' || !/^[A-Za-z0-9_-]{1,48}$/.test(name)) throw new ApiError(400,'Имя профиля: латиница, цифры, дефис или подчёркивание');
    const incoming=validateProfile(body.profile);
    const current=validateProfile(await getProfile(env,uid,name));
    const merged=validateProfile({samples:{...current.samples,...incoming.samples}});
    await storeProfile(env,uid,name,merged);
    return json({name,imported:Object.keys(incoming.samples).length,total:Object.keys(merged.samples).length});
  } catch(error) { return failure(error); }
}
