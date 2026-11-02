import {requireChatGPTUser} from './chatgpt-auth';
import Connect from './connect';
export const dynamic='force-dynamic';
export default async function Page(){await requireChatGPTUser('/');return <Connect/>;}
