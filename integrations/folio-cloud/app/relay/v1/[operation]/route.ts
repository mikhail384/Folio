import {env} from 'cloudflare:workers';
import {authenticate,claim,reply,result,submit,readBody,json,failure,ApiError} from '../../../../lib/relay.mjs';
import {importTrainingSample} from '../../../../lib/training.mjs';
export const dynamic='force-dynamic';
export async function POST(request:Request,{params}:{params:Promise<{operation:string}>}){try{
 const {operation}=await params;const body=await readBody(request);const device=['poll','reply','training'].includes(operation);if(!device&&!['request','result'].includes(operation))throw new ApiError(404,'Unknown route');
 const session=await authenticate(env,request,body,device?'device':'client');
 if(operation==='request')return json(await submit(env,session,body.id,body.envelope));
 if(operation==='result')return json(await result(env,session,body.id));
 if(operation==='reply')return json(await reply(env,session,body));
 if(operation==='training')return json(await importTrainingSample(env,session,body));
 if(body.wait===false)return json(await claim(env,session));
 const until=Date.now()+9000;do{const task=await claim(env,session);if(!task.idle)return json(task);await new Promise(r=>setTimeout(r,600));}while(Date.now()<until);return json({idle:true});
 }catch(error){return failure(error);}}
