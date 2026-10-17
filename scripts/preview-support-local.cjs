// Offline UI fixture only. No production proxy, credentials, persistent data or outbound API requests.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(process.env.SUPPORT_PREVIEW_ROOT||path.join(__dirname,'../Pusula-Super-Admin-Panel/dist'));
const entry=process.env.SUPPORT_PREVIEW_ENTRY==='support.html'?'support.html':'index.html';
const port=Number(process.env.SUPPORT_PREVIEW_PORT||8574);
if(!Number.isInteger(port)||port<1024||port>65535)throw Error('Invalid local preview port');
let ticket={id:1,number:'DST-000001',companyId:101,companyName:'Yerel Demo İşletmesi',userId:7,requester:'Demo Kullanıcı',subject:'Tahsilat sonrası ekran kapanışı',category:'BUG',status:'NEW',priority:'NORMAL',platform:'IOS',appVersion:'1.0 (59)',createdAt:'2026-10-17T08:00:00Z',updatedAt:'2026-10-17T08:00:00Z',unread:true};
let messages=[{id:1,body:'Tahsilat sırasında bağlantı kesildi. Taslak korundu; sonucu kontrol etmek istiyorum.',staff:false,internalNote:false,createdAt:ticket.createdAt,attachments:[]}],keys=new Map();
const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,'http://127.0.0.1');
  const json=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json;charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));};
  if(url.pathname.startsWith('/api/')) {
    if(url.pathname==='/api/auth/authenticate')return json({token:'offline-support-preview-only',role:'SUPER_ADMIN',fullName:'Yerel Önizleme'});
    if(url.pathname==='/api/superadmin/companies')return json([]);
    if(url.pathname==='/api/superadmin/support/unread-count')return json({count:ticket.unread?1:0});
    if(url.pathname==='/api/superadmin/support/tickets')return json({items:[ticket].filter(t=>(!url.searchParams.get('status')||t.status===url.searchParams.get('status'))&&(!url.searchParams.get('category')||t.category===url.searchParams.get('category'))),page:0,hasNext:false,unreadCount:ticket.unread?1:0,canWrite:true});
    const detail=()=>({ticket,messages,nextBefore:null,events:[{eventType:'CREATED',value:'NEW',createdAt:ticket.createdAt}]});
    if(url.pathname==='/api/superadmin/support/tickets/1')return json(detail());
    if(url.pathname==='/api/superadmin/support/tickets/1/read'){ticket.unread=false;res.writeHead(204);return res.end();}
    if(url.pathname==='/api/superadmin/support/tickets/1/messages'){
      let data='';for await(const chunk of req){data+=chunk;if(data.length>10000)return json({message:'Too large'},413);}
      const body=JSON.parse(data);if(!keys.has(body.clientKey)){messages.push({id:messages.length+1,body:body.body,staff:true,internalNote:body.internalNote,createdAt:new Date().toISOString(),attachments:[]});keys.set(body.clientKey,true);if(body.status)ticket.status=body.status;if(body.priority)ticket.priority=body.priority;}
      return json(detail());
    }
    return json({message:'Yerel fixture: bu uç nokta tanımlı değil.'},404);
  }
  const target=path.resolve(root,'.'+(url.pathname==='/'?'/'+entry:url.pathname));
  if(!target.startsWith(root+path.sep)){res.writeHead(403);return res.end();}
  try{
    let data=fs.readFileSync(target);const type=path.extname(target);
    if(type==='.html')data=Buffer.from(data.toString().replace('</head>','<style>body::before{content:"YEREL TEST — CANLI VERİ YOK";position:fixed;bottom:0;right:0;background:#086477;color:white;padding:8px;z-index:99999}</style></head>'));
    res.writeHead(200,{'Content-Type':({'.html':'text/html;charset=utf-8','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml'})[type]||'application/octet-stream'});res.end(data);
  }catch{res.writeHead(404);res.end();}
});
server.listen(port,'127.0.0.1',()=>console.log('Offline support preview: http://127.0.0.1:'+port));
