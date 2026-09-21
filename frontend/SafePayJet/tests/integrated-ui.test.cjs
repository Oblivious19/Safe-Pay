const {test}=require('node:test'),assert=require('node:assert/strict');
const {fixture,page,account,balance,beneficiary,transaction,reviewDetail,defaultReply,ko,now}=require('./fixtures.cjs');

for(const name of ['dashboard','profile','beneficiaries','send-money','transactions','notifications']) test('canonical '+name+' template renders canonical data and its interactive branches',async()=>{
  const f=fixture();const Model=f.load('viewModels/'+name),m=new Model({params:{},router:{go:async()=>{}}});
  if(m.load)await m.load();
  const unbind=f.bind(name,m);
  if(name==='beneficiaries'){
   assert.notEqual(f.doc.window.document.querySelector('.recipient-pay').style.display,'none');
   m.includeInactive(true);m.rows([{...beneficiary,status:'DISABLED'}]);
   assert.equal(f.doc.window.document.querySelector('.recipient-pay').style.display,'none');
   m.openAdd();m.paymentMethod('UPI');await m.select(beneficiary);m.back();m.rows([]);
  }
  if(name==='send-money'){
   m.pick(beneficiary);m.amount('5000');await m.continueDetails();m.step('result');
   for(const state of ['CREATED','PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','RELEASED','SETTLED','CANCELLED','FAILED']){m.result({...transaction,state,protectionSeconds:60,protectionRemainingMillis:60000});}
  }
  if(name==='transactions'){m.open(transaction);await m.refreshDetail();for(const state of ['CREATED','PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','SETTLED'])m.detail({...transaction,state});m.challenge({challengeId:'111',transactionId:'101',status:'ISSUED',maskedDestination:'t***@example.test',expiresAt:now,resendAvailableAt:now,remainingIssues:2,serverTime:now});}
  unbind();m.disconnected();f.close();
});

test('login and every registration step bind with returned Customer ID',()=>{
 const f=fixture();for(const name of ['login','register']){const M=f.load('viewModels/'+name),m=new M({});const unbind=f.bind(name,m);if(name==='register'){for(const step of ['mobile','details','password','success'])m.step(step);m.userId('9007199254740993');}unbind();m.disconnected?.();}f.close();
});

test('every staff tab and safe detail projection renders, including null category and signed reservation difference',async()=>{
 for(const area of ['admin','risk','audit']){
  const f=fixture();f.session({...f.session(),authorities:[area==='admin'?'SYSTEM_ADMIN':area==='risk'?'RISK_OFFICER':'AUDITOR']});
  const Model=f.load('viewModels/'+area),m=new Model({params:{}});const unbind=f.bind(area,m);
  for(const tab of m.tabs){m.parametersChanged({page:tab.key});await new Promise(setImmediate);await m.search();if(m.rows().length){m.open(m.rows()[0]);await new Promise(setImmediate);assert.equal(m.error(),'');m.closeDetail();}}
  assert.equal(m.display({reservationDifference:'-500.00'},'reservationDifference'),'−₹500.00');
  for(const call of f.calls){const query=new URL(call.url).searchParams;for(const value of query.values())assert.notEqual(value,'undefined','All filters must be omitted from requests');}
  unbind();m.disconnected();f.close();
 }
});

test('saving a payment makes one CREATE and no implicit AUTHORIZE',async()=>{
 const f=fixture();const M=f.load('viewModels/send-money'),m=new M({router:{go:async()=>{}}});await m.load();m.pick(beneficiary);m.amount('5000.01');await m.continueDetails();await m.confirm();
 const posts=f.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);assert.match(posts[0].url,/\/transactions$/);assert.match(posts[0].body,/"amount":5000.01/);assert.equal(m.result().state,'CREATED');m.disconnected();f.close();
});

test('existing uncertain payment prevents a replacement CREATE',async()=>{
 const f=fixture();const M=f.load('viewModels/send-money'),m=new M({router:{go:async()=>{}}});await m.load();m.pick(beneficiary);m.amount('5000');await m.continueDetails();
 f.load('services/transactionService').recoveryStore().begin('2468',{operation:'CANCEL',transactionId:'101'});await m.confirm();assert.equal(f.calls.filter(c=>c.method==='POST').length,0);assert.equal(m.attemptLocked(),true);m.disconnected();f.close();
});

test('late account balance cannot overwrite a newly selected account',async()=>{
 let resolve;const f=fixture({},async(url,options)=>url.endsWith('/8101/balance')?new Promise(r=>resolve=r):url.endsWith('/8102/balance')?{...balance,accountId:'8102',availableBalance:'10.00'}:defaultReply(url,options));
 const M=f.load('viewModels/send-money'),m=new M();m.accounts([account,{...account,accountId:'8102'}]);m.selectedAccountId('8101');const first=m.selectAccount();m.selectedAccountId('8102');await m.selectAccount();resolve(balance);await first;assert.equal(m.funds().accountId,'8102');assert.equal(m.funds().availableBalance,'10.00');m.disconnected();f.close();
});

test('review unknown outcome retains its exact key and reason; another decision is blocked',async()=>{
 let writes=0;const f=fixture({},(url,options)=>{if(options.method==='POST'){if(++writes===1)throw Error('offline');return reviewDetail;}return defaultReply(url,options);});f.session({...f.session(),userId:'2498',authorities:['RISK_OFFICER']});
 const s=f.load('services/staffService');await assert.rejects(s.decide('501','reject','Original reason'));await assert.rejects(s.decide('501','approve','Changed reason'),/previous/);await s.decide();
 const calls=f.calls.filter(c=>c.method==='POST');assert.equal(calls.length,2);assert.equal(calls[0].headers['Idempotency-Key'],calls[1].headers['Idempotency-Key']);assert.equal(calls[0].body,calls[1].body);assert.equal(s.reviewPending(),false);assert.equal(f.doc.window.sessionStorage.length,0);f.close();
});

test('review recovery marker does not persist internal note text',async()=>{
 const f=fixture({},()=>{throw Error('offline');});f.session({...f.session(),userId:'2498',authorities:['RISK_OFFICER']});const s=f.load('services/staffService');await assert.rejects(s.decide('501','notes','Sensitive internal note'));const raw=f.doc.window.sessionStorage.getItem('safepay.pending-review.v1');assert.ok(raw);assert.ok(!raw.includes('Sensitive'));assert.equal(s.canReplayReview(),true);s.clearReviewRecovery();f.close();
});

test('review commands use review ID, required reason and separate note field',async()=>{
 const f=fixture();f.session({...f.session(),userId:'2498',authorities:['RISK_OFFICER']});const s=f.load('services/staffService');await assert.rejects(s.decide('501','reject',''),/reason/);await s.decide('501','notes','Evidence checked');const call=f.calls.find(c=>c.method==='POST');assert.match(call.url,/\/risk-reviews\/501\/notes$/);assert.equal(call.body,'{"note":"Evidence checked"}');f.close();
});

test('auditor controls cannot send review or user mutations',async()=>{
 const f=fixture();f.session({...f.session(),authorities:['AUDITOR']});const M=f.load('viewModels/audit'),m=new M({params:{page:'reviews'}});await m.search();m.open(m.rows()[0]);await new Promise(setImmediate);await m.approve();await m.changeStatus();assert.equal(f.calls.filter(c=>c.method!=='GET').length,0);assert.equal(m.canReview(),false);assert.equal(m.canAdmin(),false);m.disconnected();f.close();
});

test('customer terminal states never offer cancellation or authorization',()=>{
 const f=fixture(),M=f.load('viewModels/transactions'),m=new M();m.stale(false);
 for(const state of ['SETTLED','CANCELLED','FAILED','UNKNOWN']){m.detail({...transaction,state,canCancel:false});assert.equal(m.canAuthorize(),false);assert.equal(m.canCancel(),false);}
 m.detail({...transaction,state:'PROTECTED',canCancel:true});m.remaining(0);assert.equal(m.canCancel(),false);m.remaining(10);assert.equal(m.canCancel(),true);m.stale(true);assert.equal(m.canCancel(),false);m.disconnected();f.close();
});
test('empty and unavailable customer responses render without invented balances or data',async()=>{
 for(const failure of [false,true])for(const name of ['dashboard','profile','beneficiaries','send-money','transactions','notifications']){
  const f=fixture({},(url,options)=>{if(failure)throw Error('offline');const value=defaultReply(url,options);return Array.isArray(value)?[]:value.items?page([]):value;});
  const M=f.load('viewModels/'+name),m=new M({});await m.load();const unbind=f.bind(name,m);
  if(failure)assert.ok((m.error||m.accountError)());if(name==='dashboard')assert.equal(m.balance(),null);
  unbind();m.disconnected();f.close();
 }
});

test('one refresh serves simultaneous expiry requests and uses the dedicated CSRF endpoint',async()=>{
 let refreshes=0;const f=fixture({},(url,options)=>{
  if(url.endsWith('/auth/csrf'))return {headerName:'X-XSRF-TOKEN',token:'fixture-csrf'};
  if(url.endsWith('/auth/refresh')){refreshes++;assert.equal(options.headers['X-XSRF-TOKEN'],'fixture-csrf');return {accessToken:'fixture-token',tokenType:'Bearer',userId:'2468',authorities:['CUSTOMER'],expiresAt:'2099-01-01T00:00:00Z'};}
  throw Error('Unexpected endpoint');
 },['services/authService']);
 const auth=f.load('services/authService');const tokens=await Promise.all([auth.token(),auth.token(),auth.token()]);assert.equal(refreshes,1);assert.equal(tokens.length,3);assert.equal(f.doc.window.sessionStorage.length,0);assert.equal(f.doc.window.localStorage.length,0);f.close();
});

test('refresh identity changes invalidate the session instead of exposing another user',async()=>{
 const f=fixture({},url=>url.endsWith('/auth/csrf')?{headerName:'X-XSRF-TOKEN',token:'fixture-csrf'}:{accessToken:'fixture-token',tokenType:'Bearer',userId:'2470',authorities:['CUSTOMER'],expiresAt:'2099-01-01T00:00:00Z'},['services/authService']);
 const auth=f.load('services/authService');auth.session({accessToken:'expired',tokenType:'Bearer',userId:'2468',authorities:['CUSTOMER'],expiresAt:'2000-01-01T00:00:00Z'});await assert.rejects(auth.renew(),/identity changed/);assert.equal(auth.session(),null);f.close();
});

test('route authorization distinguishes all four canonical roles',()=>{
 const f=fixture({},defaultReply,['services/sessionRouteService']);const routes=f.load('services/sessionRouteService');
 const matrix={CUSTOMER:['dashboard','transactions','notifications','profile'],SYSTEM_ADMIN:['admin'],RISK_OFFICER:['risk'],AUDITOR:['audit']};
 for(const [role,allowed]of Object.entries(matrix)){f.session({...f.session(),authorities:[role]});for(const path of ['dashboard','transactions','notifications','profile','admin','risk','audit'])assert.equal(routes.mayOpen(path),allowed.includes(path),role+' '+path);}
 f.session(null);assert.equal(routes.mayOpen('dashboard'),false);assert.equal(routes.mayOpen('login'),true);f.close();
});

test('unknown OTP response replays only the original code, key and challenge without storing the code',async()=>{
 let attempts=0;const f=fixture({},(url,options)=>{if(++attempts===1)throw Error('offline');return {transactionId:'101',challengeId:'9007199254740993',verified:true,transactionState:'PENDING_RISK_REVIEW'};});
 const otp=f.load('services/otpService');await assert.rejects(otp.verify('101','9007199254740993','123456'));await assert.rejects(otp.verify('101','9007199254740993','654321'),/previous verification/);assert.equal(f.doc.window.sessionStorage.length,0);await otp.verify('101','','',true);assert.equal(f.calls.length,2);assert.equal(f.calls[0].body,f.calls[1].body);assert.equal(f.calls[0].headers['Idempotency-Key'],f.calls[1].headers['Idempotency-Key']);assert.match(f.calls[0].body,/"challengeId":9007199254740993/);assert.equal(otp.verificationPending(),false);f.close();
});

test('confirmed wrong OTP consumes no client replay and exposes remaining attempts',async()=>{
 const f=fixture({},()=>new Response('{"detail":"Incorrect code","errorCode":"OTP_INVALID","remainingAttempts":2}',{status:422}));
 const otp=f.load('services/otpService');await assert.rejects(otp.verify('101','111','123456'),e=>e.remainingAttempts===2);assert.equal(otp.verificationPending(),false);f.close();
});
test('changing payment while its confirmation is open cannot authorize a different payment',async()=>{
 const f=fixture(),M=f.load('viewModels/transactions'),m=new M();m.detailId('101');m.detail(transaction);m.stale(false);
 const pending=m.authorize();m.detailId('102');m.detail({...transaction,transactionId:'102'});f.confirm(true);await pending;
 assert.equal(f.calls.filter(c=>c.method==='POST').length,0);m.disconnected();f.close();
});
