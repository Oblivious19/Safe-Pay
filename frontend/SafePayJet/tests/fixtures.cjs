const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');
const {JSDOM}=require('jsdom');
const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://localhost:8001/'});
global.window=dom.window;global.document=dom.window.document;
const ko=require('knockout');
// Match JET's CSP binding pipeline; plain Knockout would incorrectly allow ambient globals.
const jetCache=new Map();
function jetModule(name){
 if(jetCache.has(name))return jetCache.get(name);
 const exports={};jetCache.set(name,exports);
 const source=fs.readFileSync(path.join(__dirname,'../node_modules/@oracle/oraclejet/dist/js/libs/oj/debug',name+'.js'),'utf8');
 vm.compileFunction(source,['define'])((dependencies,factory)=>factory(...dependencies.map(d=>d==='exports'?exports:jetModule(d.replace('ojs/','')))));
 return exports;
}
const {CspExpressionEvaluatorInternal}=jetModule('ojcspexpressionevaluator-internal');
const evaluator=new CspExpressionEvaluatorInternal({globalScope:{oj:{}}});
ko.bindingProvider.instance.parseBindingsString=function(expression,context,node,options){
 const rewritten=ko.expressionRewriting.preProcessBindings(expression,options);
 return evaluator.createEvaluator('{'+rewritten+'}').evaluate([context.$data||{},context,{$element:node}]);
};
const now='2026-09-21T08:00:00Z';
const account={accountId:'8101',maskedAccountNumber:'******8101',accountType:'SAVINGS',bankName:'Demo Bank',ifscCode:'DEMO0001234',currency:'INR',status:'ACTIVE'};
const balance={accountId:'8101',maskedAccountNumber:'******8101',currency:'INR',currentBalance:'900000.00',reservedAmount:'1000.00',availableBalance:'899000.00'};
const beneficiary={beneficiaryId:'9101',beneficiaryName:'Test Recipient',nickname:null,paymentMethod:'BANK_ACCOUNT',bankName:'Demo Bank',maskedDestinationIdentifier:'******3456',ifscCode:'DEMO0001234',relationshipLabel:null,purposeNote:null,status:'ACTIVE',createdAt:now,updatedAt:now};
const profile={userId:'2468',fullName:'Test Customer',email:'customer@example.test',mobileNumber:'+919876543210',status:'ACTIVE'};
const transaction={transactionId:'101',transactionReference:'DEMO-101',sourceAccountId:'8101',maskedSourceAccountNumber:'******8101',beneficiaryId:'9101',beneficiaryName:'Test Recipient',maskedDestinationIdentifier:'******3456',amount:'5000.00',currencyCode:'INR',state:'CREATED',riskTier:null,protectedUntil:null,createdAt:now,updatedAt:now,purpose:null,customerReference:null,terminalReasonCode:null,reservedAmount:'0.00',policyVersion:null,protectionSeconds:0,riskExplanation:null,authorizedAt:null,riskAssessedAt:null,verificationCompletedAt:null,releasedAt:null,settledAt:null,cancelledAt:null,failedAt:null,serverTime:now,protectionRemainingMillis:0,canCancel:true};
const review={reviewId:'501',reviewRound:'1',status:'PENDING',transactionId:'101',transactionReference:'DEMO-101',transactionState:'PENDING_RISK_REVIEW',customerId:'2468',customerName:'Test Customer',maskedCustomerEmail:'t***@example.test',maskedSourceAccountNumber:'******8101',beneficiaryId:'9101',beneficiaryName:'Test Recipient',amount:'100001.00',currencyCode:'INR',riskTier:'VERY_HIGH',policyVersion:'v1',riskExplanation:'Amount requires review.',verificationCompletedAt:now,requestedAt:now,updatedAt:now,purpose:'Medical care',category:'MEDICAL'};
const reviewDetail={review,assignedRiskOfficerId:null,assignedRiskOfficerName:null,decisionReason:null,claimedAt:null,decidedAt:null,decidedByUserId:null,decidedByUserName:null,version:'1'};
const notice={notificationId:'601',notificationReference:'N-601',transactionId:'101',type:'PAYMENT_CREATED',severity:'INFO',title:'Payment saved',message:'Review and authorize your payment.',read:false,createdAt:now,readAt:null};
const user={...profile,roles:['CUSTOMER'],securityVersion:'0',updatedAt:now};
const audit={auditLogId:'701',eventReference:'E-701',actionCode:'PAYMENT_CREATED',actorType:'CUSTOMER',transactionId:'101',outcome:'SUCCESS',occurredAt:now};
const page=(items)=>({items,page:0,size:20,totalElements:String(items.length),totalPages:items.length?1:0,first:true,last:true});
function defaultReply(url,options={}){
 const p=new URL(url).pathname.replace('/api/v1','');
 if(p==='/accounts')return [account];if(p==='/accounts/8101')return account;if(p.endsWith('/balance'))return balance;if(p==='/users/me')return profile;
 if(p==='/beneficiaries')return options.method==='POST'?beneficiary:[beneficiary];if(p.startsWith('/beneficiaries/'))return beneficiary;
 if(p==='/transactions'){const state=new URL(url).searchParams.get('state');return options.method==='POST'?transaction:page(!state||state===transaction.state?[transaction]:[]);}
 if(p.endsWith('/audit')||p==='/audit-logs')return page([audit]);
 if(p.endsWith('/risk-explanation'))return {transactionId:'101',explanation:'Amount policy',riskTier:'MEDIUM',protectionSeconds:10,policyVersion:'v1',riskAssessedAt:now};
 if(p.startsWith('/transactions/'))return transaction;
 if(p==='/notifications')return page([notice]);if(p.startsWith('/notifications/'))return {...notice,read:true};
 if(p==='/admin/operations/stats')return {observedAt:now,paymentsByState:{SETTLED:'12',CREATED:'3'},pendingReviews:'1',exceptionsByStatus:{OPEN:'0'},notificationsByStatus:{SENT:'4'},expiredProtectedPayments:'0',dueSettlementPayments:'0'};
 if(p==='/admin/users')return page([user]);if(p.startsWith('/admin/users/'))return user;
 if(p==='/admin/accounts')return page([{...account,ownerId:'2468',ownerName:'Test Customer'}]);if(p.startsWith('/admin/accounts/'))return {...account,...balance,ownerId:'2468',ownerName:'Test Customer',accountNumber:'0000008101'};
 if(p==='/admin/operations/failures')return page([{source:'TRANSACTION',recordId:'801',transactionId:'101',processingStage:'SETTLEMENT',status:'OPEN',errorCode:'ACCOUNT_INACTIVE',displayExplanation:'Account inactive',retryable:false,attemptCount:'1',occurredAt:now}]);
 if(p==='/admin/risk-reviews'||p==='/audit/risk-reviews')return page([review]);if(p.startsWith('/admin/risk-reviews/')||p.startsWith('/audit/risk-reviews/'))return reviewDetail;
 if(p==='/audit/transactions')return page([transaction]);if(p.startsWith('/audit/transactions/'))return {customerId:'2468',transaction,riskEvidence:[]};
 const ledger={postingId:'901',postingReference:'L-901',postingType:'PAYMENT_SETTLEMENT',transactionId:'101',amount:'5000.00',currencyCode:'INR',status:'POSTED',expectedEntryCount:'2',createdAt:now,postedAt:now,updatedAt:now};
 if(p==='/audit/ledger-postings')return page([ledger]);if(p.startsWith('/audit/ledger-postings/'))return {posting:ledger,entries:[{ledgerEntryId:'902',lineNumber:'1',accountId:'8101',maskedAccountNumber:'******8101',entryType:'DEBIT',amount:'5000.00',currencyCode:'INR',status:'POSTED',createdAt:now}]};
 if(p==='/audit/reconciliation/ledger')return page([{...ledger,postingAmount:'5000.00',debitTotal:'5000.00',creditTotal:'5000.00',reconciliationStatus:'BALANCED'}]);
 if(p==='/audit/reconciliation/reservations')return page([{accountId:'8101',maskedAccountNumber:'******8101',currencyCode:'INR',storedReservedAmount:'1000.00',calculatedReservedAmount:'1500.00',reservationDifference:'-500.00',reconciliationStatus:'MISMATCH',updatedAt:now}]);
 if(p==='/audit/exceptions')return page([{exceptionId:'1001',exceptionReference:'X-1001',transactionId:'101',processingStage:'SETTLEMENT',status:'OPEN',displayExplanation:'Processing issue recorded'}]);if(p.startsWith('/audit/exceptions/'))return {exceptionId:'1001',status:'OPEN',retryCount:'0'};
 const policy={riskPolicyId:'2001',policyVersion:'v1',policyName:'Amount policy',status:'ACTIVE',algorithmType:'AMOUNT',currencyCode:'INR',effectiveFrom:now};
 if(p==='/audit/risk-policies')return page([policy]);if(p.startsWith('/audit/risk-policies/'))return {policy,bands:[{riskPolicyBandId:'2002',bandCode:'LOW',minimumAmount:'1.00',maximumAmount:'5000.00',riskTier:'LOW',protectionSeconds:0,customerCanCancel:false}]};
 throw Error('Unexpected fixture request '+p);
}
function fixture(overrides={},handler=defaultReply,realModules=[]){
 const doc=new JSDOM('<!doctype html><html><body></body></html>',{url:'http://localhost:8001/'});const cache=new Map(),calls=[],navigation=[];
 Object.defineProperty(doc.window.navigator,'locks',{value:{request:(_name,action)=>Promise.resolve().then(action)}});
 const channels=[];class Channel{constructor(){this.listeners=[];channels.push(this);}addEventListener(_name,fn){this.listeners.push(fn);}postMessage(){}close(){}}
 const session=ko.observable({userId:'2468',authorities:['CUSTOMER'],accessToken:'fixture',tokenType:'Bearer',expiresAt:'2099-01-01T00:00:00Z'});
 const auth={session,token:async()=>'fixture',restore:async()=>{},login:async()=>{},logout:async()=>{},register:async()=>profile,passwordProblem:()=>''};
 const context=vm.createContext({BroadcastChannel:Channel,console,URL,URLSearchParams,Headers,Response,TextEncoder,Date,Intl,BigInt,AbortSignal,performance,crypto:require('node:crypto').webcrypto,
 setInterval:()=>1,clearInterval(){},setTimeout:()=>1,clearTimeout(){},requestAnimationFrame:fn=>fn(),document:doc.window.document,window:doc.window,location:doc.window.location,history:doc.window.history,navigator:doc.window.navigator,sessionStorage:doc.window.sessionStorage,Event:doc.window.Event,CustomEvent:doc.window.CustomEvent,
 fetch:async(url,options)=>{calls.push({url,...options});const result=await handler(url,options,calls);return result instanceof Response?result:new Response(JSON.stringify(result));}});
 function load(name){
  name=name.replace(/\\/g,'/').replace(/\.ts$/,'');if(overrides[name])return overrides[name];
  if(name==='knockout')return ko;if(name.startsWith('ojs/'))return {};
  if(name==='services/authService'&&!realModules.includes(name))return auth;
  if(name==='services/realtimeService')return {realtimeStatus:ko.observable('Fixture preview'),startRealtime:()=>()=>{}};
  if(name==='services/refreshLoop')return {refreshLoop:()=>()=>{}};
  if(name==='utils/chime')return {armAudio(){},chimeForPayment(){}};
  if(name==='services/sessionRouteService'&&!realModules.includes(name))return {navigate:(...v)=>navigation.push(v)};
  if(name==='accUtils')return {announce(){}};
  if(!name.startsWith('services/')&&!name.startsWith('utils/')&&!name.startsWith('viewModels/')&&name!=='pageModel')return require(name);
  if(cache.has(name))return cache.get(name).exports;const module={exports:{}};cache.set(name,module);
  const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts',name+'.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
  vm.compileFunction(code,['require','module','exports'],{contextExtensions:[context]})(p=>load(p.startsWith('.')?path.posix.normalize(path.posix.join(path.posix.dirname(name),p)):p),module,module.exports);return module.exports;
 }
 const api=load('services/apiClient');api.configureSession(auth.token,auth.token,()=>session()?.userId||null);
 const bind=(name,model)=>{
  const root=doc.window.document.createElement('div');root.setAttribute('data-bind','with:page');root.innerHTML=fs.readFileSync(path.join(__dirname,'../src/ts/views',name+'.html'),'utf8');doc.window.document.body.appendChild(root);
  ko.applyBindings({page:model,profile:ko.observable(profile),session},root);
  return ()=>{ko.cleanNode(root);root.remove();};
 };
 const confirm=(yes=true)=>{const c=load('pageModel').confirmation();if(!c)throw Error('Expected a confirmation');load('pageModel').confirmation(null);c.resolve(yes);};
 return {load,calls,session,doc,navigation,bind,confirm,channels,close:()=>doc.window.close()};
}
module.exports={fixture,defaultReply,page,account,balance,beneficiary,profile,transaction,review,reviewDetail,notice,user,audit,ko,now};
