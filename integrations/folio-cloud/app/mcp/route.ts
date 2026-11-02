import {env} from 'cloudflare:workers';
import {rpc} from '../../lib/mcp.mjs';
import {readBody,json,failure} from '../../lib/relay.mjs';
export const dynamic='force-dynamic';
export async function POST(request:Request){try{const result=await rpc(env,request,await readBody(request));return result?json(result):new Response(null,{status:202});}catch(error){return failure(error);}}
export async function GET(){return new Response(null,{status:405,headers:{Allow:'POST'}});}
