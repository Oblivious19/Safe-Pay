// Offline visual QA only. This server never connects to the backend.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const data=require('./fixtures.cjs');
const root=path.resolve(__dirname,'../web');
const names=['account','balance','beneficiary','profile','transaction','review','reviewDetail','notice','user','audit','now'];
const literals=names.map(n=>'const '+n+'='+JSON.stringify(data[n])+';').join('\n');
const replySource=data.defaultReply.toString();
const api='define([],function(){'+literals+'\nconst page='+data.page.toString()+';\n'+replySource+`;
 function request(p,o={}){try{let value=defaultReply("http://fixture/api/v1"+p,o);return Promise.resolve(value);}catch(e){return Promise.reject(e);}}
 function query(p){const q=new URLSearchParams();Object.entries(p).forEach(([k,v])=>{if(v!==undefined&&v!=="")q.set(k,String(v));});return q.size?"?"+q.toString():"";}
 return {API_ORIGIN:"http://fixture.invalid",request,query,configureSession:function(){}};
});`;
const auth=`define(["knockout"],function(ko){
 const q=new URLSearchParams(location.search);const role=q.get("fixtureRole")||sessionStorage.getItem("fixture.role")||"CUSTOMER";sessionStorage.setItem("fixture.role",role);
 const value={userId:role==="CUSTOMER"?"2468":role==="SYSTEM_ADMIN"?"2499":role==="RISK_OFFICER"?"2498":"2500",authorities:[role],accessToken:"offline-fixture",tokenType:"Bearer",expiresAt:"2099-01-01T00:00:00Z"};
 const session=ko.observable(value);return {session,restore:async()=>{},token:async()=>"offline-fixture",renew:async()=>"offline-fixture",login:async()=>session(value),logout:async()=>session(null),register:async()=>({userId:"9999"}),passwordProblem:()=>""};
});`;
const realtime='define(["knockout"],function(ko){return {realtimeStatus:ko.observable("Offline fixture preview"),startRealtime:function(){return function(){};}};});';
http.createServer((req,res)=>{
 const pathname=new URL(req.url,'http://localhost:8001').pathname;
 if(pathname==='/js/services/apiClient.js'||pathname==='/js/services/authService.js'||pathname==='/js/services/realtimeService.js'){
  res.writeHead(200,{'Content-Type':'application/javascript','Cache-Control':'no-store'});res.end(pathname.includes('apiClient')?api:pathname.includes('authService')?auth:realtime);return;
 }
 const sourceRoot=path.resolve(__dirname,'../src');
 const sourceCandidate=path.resolve(sourceRoot,'.'+decodeURIComponent(pathname).replace(/^\/js\//,'/ts/').replace(/\.js$/,'.ts'));
 if(sourceCandidate.startsWith(sourceRoot+path.sep)&&fs.existsSync(sourceCandidate)&&fs.statSync(sourceCandidate).isFile()){
  const ext=path.extname(sourceCandidate);const raw=fs.readFileSync(sourceCandidate,'utf8');
  res.writeHead(200,{'Content-Type':ext==='.ts'?'application/javascript':ext==='.html'?'text/html':'text/css','Cache-Control':'no-store'});
  res.end(ext==='.ts'?require('typescript').transpileModule(raw,{compilerOptions:{module:require('typescript').ModuleKind.AMD,target:require('typescript').ScriptTarget.ES2022}}).outputText:raw);return;
 }
 let target=path.resolve(root,'.'+decodeURIComponent(pathname));
 if(target!==root&&!target.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
 if(!path.extname(target)||target===root)target=path.join(root,'index.html');
 if(!fs.existsSync(target)||!fs.statSync(target).isFile()){res.writeHead(404);res.end('Not found');return;}
 const ext=path.extname(target),types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.woff2':'font/woff2','.json':'application/json'};
 res.writeHead(200,{'Content-Type':types[ext]||'application/octet-stream','Cache-Control':'no-store'});
 if(ext==='.html'&&path.basename(target)==='index.html'){
  let html=fs.readFileSync(target,'utf8');html=html.replace('</body>','<div style="position:fixed;bottom:8px;left:8px;z-index:9999;background:#fff1c6;padding:6px 12px;border-radius:8px;font:12px sans-serif">OFFLINE UI FIXTURES · No backend connection</div></body>');res.end(html);
 }else fs.createReadStream(target).pipe(res);
}).listen(8001,'127.0.0.1',()=>console.log('Offline fixture preview: http://localhost:8001/dashboard'));
