import {env} from 'cloudflare:workers';
export const dynamic='force-dynamic';
export async function GET(){const e=env as any;try{await e.DB.prepare('SELECT 1').first();return Response.json({service:'Folio Cloud',protocol:1,database:true,storage:!!e.BUCKET,configured:!!e.FOLIO_SERVICE_ACCESS},{headers:{'Cache-Control':'no-store'}});}catch{return Response.json({service:'Folio Cloud',ready:false},{status:503});}}
