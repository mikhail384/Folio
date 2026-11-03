import {createApplication} from '../app/integrations/folio-vps/server.mjs';
const chunks=[];for await(const chunk of process.stdin)chunks.push(chunk);
const {token}=JSON.parse(Buffer.concat(chunks).toString('utf8'));
if(typeof token!=='string'||token.length<32)throw new Error('Invalid setup token');
const app=createApplication({origin:'https://109.66.236.217:17443',dataDir:'/var/lib/folio-canvas-test',setupToken:token});
app.close();
