import React, {useCallback,useState} from 'react';
import {createRoot} from 'react-dom/client';
import CommunityChat from './src/components/CommunityChat.jsx';
import './src/styles/theme.css';
import './src/styles/community-chat.css';
const auth={userId:1,username:'mai'};
let id=60;
const rows=Array.from({length:60},(_,i)=>({id:String(i+1),userId:2,username:i%2?'Hoa Mai':'minh',content:i===59?'Chào mọi người!':`Tin nhắn ${i+1}`,sentAt:new Date().toISOString()}));
function Demo(){const [events,setEvents]=useState([]);const [connected,setConnected]=useState(true);const [visible,setVisible]=useState(true);
const request=useCallback(async(action,fields={})=>{if(action==='community_send'){const message={...auth,id:String(++id),content:fields.content,sentAt:new Date().toISOString()};return message;}const page=rows.filter(m=>+m.id<+(fields.before||999)).slice(-50);return {messages:page,hasMore:+page[0]?.id>1};},[]);
return <main style={{height:'100vh',background:'url(/images/background.svg) center / cover'}}><button onClick={()=>setEvents(v=>[...v,{id:String(++id),userId:2,username:'minh',content:'Tin mới',sentAt:new Date().toISOString()}])}>Nhận tin mẫu</button><button onClick={()=>setConnected(v=>!v)}>Đổi kết nối</button><button onClick={()=>setVisible(v=>!v)}>Vào / rời trận</button><CommunityChat auth={auth} connected={connected} onlineCount={32} events={events} request={request} visible={visible}/></main>}
createRoot(document.getElementById('root')).render(<Demo/>);
