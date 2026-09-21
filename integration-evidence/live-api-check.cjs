// Explicitly scoped live demo verification. Secrets remain in memory; no environment/config loading.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {parse,stringify,LosslessNumber,isLosslessNumber}=require('../frontend/SafePayJet/node_modules/lossless-json');
const {parseSnapshot}=require('./compare-snapshots.cjs');
const root=path.resolve(__dirname,'..'),report=path.join(__dirname,'LIVE_API_TESTS.md');
const approved={'2468':{email:'priya.nair@gmail.com',role:'CUSTOMER'},'2470':{email:'kavya.sharma@gmail.com',role:'CUSTOMER'},'2498':{email:'rhea.malhotra@gmail.com',role:'RISK_OFFICER'},'2499':{email:'vikram.bhat@gmail.com',role:'SYSTEM_ADMIN'},'2500':{email:'anjali.thomas@gmail.com',role:'AUDITOR'}};
function credentials(){
 const values=new Map();
 for(const line of fs.readFileSync(path.join(root,'userCredentials.txt'),'utf8').split(/\r?\n/)){
  const m=line.match(/^\s*(\d+)\s*,\s*([^,]+)\s*,\s*([^,;]+)[,;]\s*(.+?)\s*$/);
  if(!m)continue;
  const [,id,email,,password]=m;
  if(!approved[id]||email.trim()!==approved[id].email||values.has(id))throw Error('Credential identity is outside the five approved users.');
  values.set(id,{loginIdentifier:email.trim(),password});
 }
 if(values.size!==5)throw Error('Expected exactly five designated credential records.');
 return values;
}
const clean=value=>isLosslessNumber(value)?value.toString():Array.isArray(value)?value.map(clean):value&&typeof value==='object'?Object.fromEntries(Object.entries(value).map(([k,v])=>[k,clean(v)])):value;
const cell=x=>String(x??'—').replace(/\|/g,'/').replace(/[\r\n]/g,' ');
function log(row){
 if(!fs.existsSync(report))fs.writeFileSync(report,'# Live API test/action ledger\n\nOnly designated local demo identities are used. Passwords, tokens, cookies and OTP codes are omitted. Sign-in/refresh/logout can change session and audit rows; GET tests do not intentionally mutate financial data.\n\n| Time UTC | Actor | Test | Request | HTTP | Expected | Result | Correlation | Resource / outcome |\n|---|---|---|---|---|---|---|---|---|\n');
 fs.appendFileSync(report,'| '+row.map(cell).join(' | ')+' |\n');
}
class Client{
 constructor(id,secret){this.id=id;this.secret=secret;this.cookies=new Map();this.token='';}
 async call(test,url,{method='GET',body,key,statuses=[200],headers={}}={}){
  assert.ok(url.startsWith('/')&&!url.startsWith('//'));
  const correlation=crypto.randomUUID();
  const h={Accept:'application/json',Origin:'http://localhost:8000','X-Correlation-ID':correlation,...headers};
  if(this.token)h.Authorization='Bearer '+this.token;
  if(this.cookies.size)h.Cookie=[...this.cookies].map(([k,v])=>k+'='+v).join('; ');
  if(body!==undefined)h['Content-Type']='application/json';
  if(key)h['Idempotency-Key']=key;
  let res;
  try{res=await fetch('http://localhost:8080/api/v1'+url,{method,headers:h,body:body===undefined?undefined:stringify(body),signal:AbortSignal.timeout(30000)});}
  catch{log([new Date().toISOString(),this.id,test,method+' '+url,'UNKNOWN',statuses.join('/'),'STOP',correlation,'Response unavailable; no automatic retry']);throw Error(test+': response unavailable; reconcile before continuing.');}
  for(const cookie of res.headers.getSetCookie()){const first=cookie.split(';')[0],i=first.indexOf('=');this.cookies.set(first.slice(0,i),first.slice(i+1));}
  const text=await res.text();let raw;try{raw=text?clean(parse(text)):undefined;}catch{throw Error(test+': response was not valid JSON.');}
  const details=raw?.transactionId?'transaction '+raw.transactionId+' '+(raw.state||''):raw?.reviewId?'review '+raw.reviewId:raw?.beneficiaryId?'beneficiary '+raw.beneficiaryId:raw?.errorCode||raw?.code||raw?.userId&&'user '+raw.userId||raw?.totalElements&&raw.totalElements+' items'||Array.isArray(raw)&&raw.length+' items'||'';
  log([new Date().toISOString(),this.id,test,method+' '+url,res.status,statuses.join('/'),statuses.includes(res.status)?'PASS':'FAIL',res.headers.get('X-Correlation-ID')||correlation,details]);
  if(!statuses.includes(res.status))throw Error(test+': HTTP '+res.status+' '+(raw?.errorCode||raw?.code||raw?.title||'unexpected result'));
  return {data:raw,status:res.status,headers:res.headers};
 }
 async login(){
  const {data}=await this.call('LOGIN','/auth/login',{method:'POST',body:this.secret});
  assert.equal(data.userId,this.id);assert.ok(data.authorities.includes(approved[this.id].role));
  this.token=data.accessToken;this.secret=null;
 }
 async csrf(){return (await this.call('CSRF','/auth/csrf')).data;}
 async refresh(){
  const csrf=await this.csrf();
  const {data}=await this.call('REFRESH','/auth/refresh',{method:'POST',headers:{[csrf.headerName]:csrf.token}});
  assert.equal(data.userId,this.id);this.token=data.accessToken;
 }
 async logout(){if(!this.token)return;const csrf=await this.csrf();await this.call('LOGOUT','/auth/logout',{method:'POST',headers:{[csrf.headerName]:csrf.token},statuses:[204]});this.token='';this.cookies.clear();}
}
async function clients(){parseSnapshot(fs.readFileSync(path.join(__dirname,'baseline-before-v2.txt'),'utf8'));const secrets=credentials(),result=new Map();for(const [id,secret]of secrets)result.set(id,new Client(id,secret));secrets.clear();return result;}
async function readChecks(){
 const users=await clients(),observed={},failures=[];
 try{
  for(const [id,c]of users){await c.login();observed[id]={role:approved[id].role};
   if(approved[id].role==='CUSTOMER'){
    const profile=(await c.call('CUSTOMER_PROFILE','/users/me')).data;assert.equal(profile.userId,id);
    const accounts=(await c.call('ACCOUNTS','/accounts')).data;observed[id].accounts=[];
    for(const account of accounts.slice(0,2)){
     const d=(await c.call('ACCOUNT_DETAIL','/accounts/'+account.accountId)).data;
     const balance=(await c.call('BALANCE','/accounts/'+account.accountId+'/balance')).data;
     observed[id].accounts.push({accountId:d.accountId,status:d.status,accountType:d.accountType,currency:d.currency,currentBalance:balance.currentBalance,reservedAmount:balance.reservedAmount,availableBalance:balance.availableBalance});
    }
    const beneficiaries=(await c.call('BENEFICIARIES','/beneficiaries')).data;observed[id].beneficiaries=beneficiaries.map(x=>({beneficiaryId:x.beneficiaryId,status:x.status,paymentMethod:x.paymentMethod,maskedDestinationIdentifier:x.maskedDestinationIdentifier}));
    if(beneficiaries[0])await c.call('BENEFICIARY_DETAIL','/beneficiaries/'+beneficiaries[0].beneficiaryId);
    const tx=(await c.call('PAYMENT_LIST','/transactions?page=0&size=5')).data;
    if(tx.items[0]){const tid=tx.items[0].transactionId;observed[id].sampleTransactionId=tid;await c.call('PAYMENT_DETAIL','/transactions/'+tid);await c.call('PAYMENT_AUDIT','/transactions/'+tid+'/audit?page=0&size=5');}
    await c.call('NOTIFICATIONS','/notifications?page=0&size=5');
   }
  }
  const customer=users.get('2468'),other=users.get('2470'),admin=users.get('2499'),risk=users.get('2498'),auditor=users.get('2500');
  await customer.refresh();
  await customer.call('CUSTOMER_ADMIN_DENIED','/admin/users',{statuses:[403]});
  if(observed['2470'].accounts[0])await customer.call('ACCOUNT_OWNERSHIP_DENIED','/accounts/'+observed['2470'].accounts[0].accountId+'/balance',{statuses:[403,404]});
  if(observed['2468'].sampleTransactionId)await other.call('PAYMENT_OWNERSHIP_DENIED','/transactions/'+observed['2468'].sampleTransactionId,{statuses:[403,404]});
  await admin.call('ADMIN_STATS','/admin/operations/stats');
  await admin.call('ADMIN_USERS','/admin/users?role=CUSTOMER&page=0&size=5');
  await admin.call('ADMIN_USER_DETAIL','/admin/users/2468');
  const aa=(await admin.call('ADMIN_ACCOUNTS','/admin/accounts?customerId=2468&page=0&size=5')).data;
  if(aa.items[0]){await admin.call('ADMIN_ACCOUNT_DETAIL','/admin/accounts/'+aa.items[0].accountId);await admin.call('ADMIN_ACCOUNT_FUNDS','/admin/accounts/'+aa.items[0].accountId+'/balance');}
  await admin.call('ADMIN_FAILURES','/admin/operations/failures?page=0&size=5');
  await admin.call('ADMIN_REVIEW_DENIED','/admin/risk-reviews',{statuses:[403]});
  const rq=(await risk.call('RISK_QUEUE','/admin/risk-reviews?sort=PRIORITY&page=0&size=5')).data;
  if(rq.items[0]){await risk.call('RISK_DETAIL','/admin/risk-reviews/'+rq.items[0].reviewId);await risk.call('RISK_TIMELINE','/transactions/'+rq.items[0].transactionId+'/audit?page=0&size=5');}
  await risk.call('RISK_USER_ADMIN_DENIED','/admin/users',{statuses:[403]});
  const auditRoutes=[['AUDIT_LOGS','/audit-logs'],['AUDIT_PAYMENTS','/audit/transactions'],['AUDIT_REVIEWS','/audit/risk-reviews'],['AUDIT_LEDGER','/audit/ledger-postings'],['AUDIT_LEDGER_CHECKS','/audit/reconciliation/ledger'],['AUDIT_RESERVATION_CHECKS','/audit/reconciliation/reservations'],['AUDIT_EXCEPTIONS','/audit/exceptions'],['AUDIT_POLICIES','/audit/risk-policies']];
  for(const [test,url]of auditRoutes){
   const value=(await auditor.call(test,url+'?page=0&size=5')).data;
   const idField=url.endsWith('/transactions')?'transactionId':url.endsWith('/risk-reviews')?'reviewId':url.endsWith('/ledger-postings')?'postingId':url.endsWith('/exceptions')?'exceptionId':url.endsWith('/risk-policies')?'policyVersion':null;
   if(idField&&value.items[0]){const detail=(await auditor.call(test+'_DETAIL',url+'/'+encodeURIComponent(value.items[0][idField]))).data;if(idField==='policyVersion')observed.policy=detail;}
  }
  await auditor.call('AUDITOR_REVIEW_ACCESS_DENIED','/admin/risk-reviews',{statuses:[403]});
  fs.writeFileSync(path.join(__dirname,'live-observations.json'),JSON.stringify(observed,null,2));
  console.log(JSON.stringify({phase:'read-only-api',result:'PASS',users:Object.keys(observed).filter(k=>/^\d+$/.test(k)),observed},null,2));
 }catch(error){failures.push(error.message);}
 finally{for(const client of users.values())try{await client.logout();}catch(error){failures.push(error.message);}}
 if(failures.length)throw Error(failures.join('; '));
}
if(require.main===module){if(process.argv[2]!=='read')throw Error('Use the explicit read phase.');readChecks().catch(e=>{console.error(e.message);process.exitCode=1;});}
module.exports={Client,clients,approved,log,LosslessNumber};
