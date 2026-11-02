import {env} from 'cloudflare:workers';
import {owner,pair,json,failure,readBody,deviceForOwner} from '../../../lib/relay.mjs';
export const dynamic='force-dynamic';
export async function GET(request:Request){try{const uid=owner(request);try{const s=await deviceForOwner(env,uid);return json({paired:true,online:Date.now()-s.seen<40000,last_seen:s.seen});}catch(error:any){if(error.status===409)return json({paired:false,online:false});throw error;}}catch(error){return failure(error);}}
export async function POST(request:Request){try{const uid=owner(request);const body=await readBody(request);const config=await pair(env,uid,new URL(request.url).origin,body.reset===true);return json({connection:config});}catch(error){return failure(error);}}
