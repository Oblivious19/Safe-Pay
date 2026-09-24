const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),ko=require('knockout');
class ApiError extends Error {constructor(status){super('SQL/CORS/security implementation detail');this.status=status;}}
function load(relative,imports={},globals={}){
  const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts',relative),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
  const module={exports:{}};
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{Intl,Date,setInterval:()=>1,clearInterval(){},...globals})(key=>imports[key]||{},module,module.exports);
  return module.exports;
}
const protection=load('utils/protection.ts');
function history(service={}){
  const redirects=[];
  const Model=load('viewModels/transactions.ts',{
    knockout:ko,'../appController':{default:{profile:ko.observable({email:'sample@example.test'})}},
    '../services/apiError':{ApiError},'../utils/protection':protection,
    '../utils/chime':{armAudio(){},rememberSettled(){},chimeIfSettled(){},chimeForPayment(){}},
    '../services/transactionService':{transactionService:{list:async()=>[],...service},newIdempotencyKey:()=> 'stable-key'}
  },{window:{location:{replace:url=>redirects.push(url)}}});
  return {model:new Model(),redirects};
}
const row=(id,extra={})=>({transactionId:id,transactionRef:'REF-'+id,beneficiaryName:'Person '+id,amount:id*100,purpose:'Rent',state:'SETTLED',createdAt:new Date(2026,8,id).toISOString(),...extra});
test('history is newest first, paged and searchable without mutating server data',()=>{
  const {model:m}=history();const rows=Array.from({length:19},(_,i)=>row(i+1));m.transactions(rows);
  assert.equal(m.displayedTransactions().length,8);assert.equal(m.displayedTransactions()[0].transactionId,19);
  assert.equal(rows[0].transactionId,1);assert.equal(m.pageCount(),3);
  m.nextPage();assert.equal(m.currentPage(),2);assert.equal(m.pageReveal(),1);
  m.nextPage();assert.equal(m.displayedTransactions().length,3);m.nextPage();assert.equal(m.currentPage(),3);
  m.previousPage();assert.equal(m.currentPage(),2);
  m.query('REF-2');assert.equal(m.currentPage(),1);assert.equal(m.filtered().length,1);
  m.query('missing');assert.equal(m.filtered().length,0);assert.equal(m.pageCount(),1);m.disconnected();
});
test('history filters pending, settled and cancelled/rejected outcomes',()=>{
  const {model:m}=history();m.transactions(['SETTLED','HARD_HOLD','PROTECTED','CANCELLED','REJECTED'].map((state,i)=>row(i+1,{state})));
  m.filter('pending');assert.equal(m.filtered().length,2);
  m.filter('cancelled');assert.equal(m.filtered().length,2);
  m.filter('settled');assert.equal(m.filtered().length,1);m.disconnected();
});
test('explicit credits are green incoming; legacy transfers remain red outgoing',()=>{
  const {model:m}=history(),debit=row(1),credit=row(2,{direction:'CREDIT'});
  assert.equal(m.directionClass(debit),'money-out');assert.equal(m.directionArrow(debit),'↗');
  assert.equal(m.directionClass(credit),'money-in');assert.equal(m.directionArrow(credit),'↙');
  assert.match(m.signedAmount(debit),/^− /);assert.match(m.signedAmount(credit),/^\+ /);
  assert.doesNotMatch(m.signedAmount(row(3,{state:'CANCELLED'})),/^[+−]/);
  assert.doesNotMatch(m.signedAmount(row(3,{state:'PROTECTED'})),/^[+−]/);m.disconnected();
});
test('history errors offer recovery without exposing backend implementation details',async()=>{
  const {model:m}=history({list:async()=>{throw new ApiError(500);}});await m.load();
  assert.match(m.error(),/connection|try again/i);assert.doesNotMatch(m.error(),/SQL|CORS|implementation/);assert.equal(m.loading(),false);m.disconnected();
});
test('detail errors stay in the open drawer and session expiry redirects',async()=>{
  const {model:m}=history({get:async()=>{throw new ApiError(409);}});
  m.openDetail(row(1));await m.refreshDetail();assert.equal(m.error(),'');assert.match(m.detailError(),/status has changed/);
  m.closeDetail();assert.equal(m.detailError(),'');m.disconnected();
  const f=history({list:async()=>{throw new ApiError(401);}});await f.model.load();
  assert.equal(f.redirects[0],'/login?reason=session-expired');assert.equal(f.model.transactions().length,0);f.model.disconnected();
});
test('profile ignores a late balance response after account switching',async()=>{
  let finish;const app={profile:ko.observable({name:'Sample',email:'sample@example.test',status:'ACTIVE'})};
  const Model=load('viewModels/profile.ts',{knockout:ko,'../appController':{default:app},'../services/apiError':{ApiError},'../services/accountService':{accountService:{funds:id=>id===1?new Promise(r=>finish=r):Promise.resolve({accountId:2,availableToTransfer:200})}}});
  const m=new Model();m.accounts([{accountId:1},{accountId:2}]);m.selectedAccountId(1);m.selectAccount();
  m.selectedAccountId(2);m.selectAccount();await new Promise(setImmediate);finish({accountId:1,availableToTransfer:100});await new Promise(setImmediate);
  assert.equal(m.funds().accountId,2);assert.equal(m.status(),'Active');m.disconnected();
});
test('payment markup has a footer action bar outside the scrolling panel and one login spinner',()=>{
  const read=name=>fs.readFileSync(path.join(__dirname,'../src/ts/views',name+'.html'),'utf8');
  const html=read('send-money');assert.match(html,/<footer class="pay-action-bar">/);assert.match(html,/click: cancelPayment/);assert.match(html,/Awaiting administrator review/);
  assert.equal((read('login').match(/class="spin"/g)||[]).length,1);
});
