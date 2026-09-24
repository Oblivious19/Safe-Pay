const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),ko=require('knockout');
class ApiError extends Error {constructor(status){super('Test failure');this.status=status;}}
function load(file,imports={},scope={}) {
  const m={exports:{}};
  const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts',file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{
    Intl,TextEncoder,document:{title:''},window:{location:{replace(){}}},setInterval(){return 1;},clearInterval(){},...scope
  })(key=>imports[key]||(key==='../constants/paymentCategories' ? load('constants/paymentCategories.ts') : {}),m,m.exports);return m.exports;
}
const protection=load('utils/protection.ts');
const account={accountId:1,accountNumber:'500000000001',balance:500000,status:'ACTIVE',accountType:'SAVINGS'};
const credit={transactionId:7,fromAccountId:99,toAccountId:1,direction:'CREDIT',counterpartyName:'Sender',senderName:'Sender',
  beneficiaryName:'Receiver',state:'SETTLED',amount:1000,createdAt:'2026-09-24T12:00:00',transactionRef:'TEST-7'};
function dashboard() {
  const timers=new Map(),redirects=[];let id=0,accounts=[account],rows=[],accountCall=async()=>accounts,transactionCall=async()=>rows;
  let fundsCall=async id=>({accountId:id,balance:accounts.find(a=>a.accountId===id).balance,reservedBalance:0,minimumBalance:0,availableToTransfer:accounts.find(a=>a.accountId===id).balance});
  const document={title:'',visibilityState:'visible'};
  const Model=load('viewModels/dashboard.ts',{'knockout':ko,'../services/apiError':{ApiError},'../accUtils':{announce(){}},
    '../utils/protection':protection,'../services/accountService':{accountService:{list:()=>accountCall(),funds:id=>fundsCall(id)}},
    '../services/transactionService':{transactionService:{list:()=>transactionCall()}},'../utils/chime':{armAudio(){},chimeForPayment(){}}},
    {document,window:{location:{replace:url=>redirects.push(url)}},setInterval:(fn,ms)=>{timers.set(++id,{fn,ms});return id;},clearInterval:id=>timers.delete(id)});
  return {model:new Model(),timers,redirects,document,setAccounts:v=>accounts=v,setRows:v=>rows=v,
    setAccountCall:v=>accountCall=v,setTransactionCall:v=>transactionCall=v,setFundsCall:v=>fundsCall=v};
}

test('dashboard current balance uses server available funds and never falls back to total on failure',async()=>{
  const f=dashboard();f.setAccounts([{...account,balance:166933}]);
  f.setFundsCall(async id=>({accountId:id,balance:166933,reservedBalance:120000,minimumBalance:0,availableToTransfer:46933}));
  await f.model.load();assert.equal(f.model.funds().availableToTransfer,46933);assert.equal(f.model.funds().reservedBalance,120000);
  f.setFundsCall(async()=>{throw new Error('offline');});await f.model.refreshFunds();
  assert.equal(f.model.funds(),null);assert.ok(f.model.fundsError());assert.equal(f.model.canSend(),false);
  f.setFundsCall(async id=>({accountId:id,balance:120000,reservedBalance:120000,minimumBalance:0,availableToTransfer:0}));
  await f.model.refreshFunds();assert.equal(f.model.funds().availableToTransfer,0);assert.equal(f.model.fundsError(),'');f.model.disconnected();
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/dashboard.html'),'utf8');
  assert.match(html,/>Current Balance</);assert.match(html,/formatMoney\(funds\(\).availableToTransfer\)/);
});

test('dashboard ignores funds arriving for a previously selected account',async()=>{
  const f=dashboard();f.setAccounts([account,{...account,accountId:2,balance:100}]);await f.model.load();
  let finish;f.setFundsCall(()=>new Promise(resolve=>finish=resolve));const pending=f.model.refreshFunds();
  f.setFundsCall(async id=>({accountId:id,balance:100,reservedBalance:20,minimumBalance:0,availableToTransfer:80}));
  f.model.selectedAccountId(2);f.model.selectAccount();await new Promise(setImmediate);
  finish({accountId:1,balance:500000,reservedBalance:0,availableToTransfer:500000});await pending;
  assert.equal(f.model.funds().accountId,2);assert.equal(f.model.funds().availableToTransfer,80);f.model.disconnected();
});
test('idle receiver dashboard keeps polling and loads a new credit and balance without a reload',async()=>{
  const f=dashboard();await f.model.load();assert.equal(f.model.pending().length,0);
  assert.ok([...f.timers.values()].some(t=>t.ms===3000));
  f.setAccounts([{...account,balance:501000}]);f.setRows([credit]);await f.model.refreshPending();
  assert.equal(f.model.account().balance,501000);assert.equal(f.model.displayName(f.model.recent()[0]),'Sender');
  assert.equal(f.model.signedAmount(credit),'+ ₹1,000.00');assert.ok(f.timers.size>0);
  f.model.disconnected();assert.equal(f.timers.size,0);
});
test('dashboard refresh preserves account selection and signs transfers between owned accounts correctly',async()=>{
  const f=dashboard();f.setAccounts([account,{...account,accountId:2,balance:10000}]);await f.model.load();
  f.model.selectedAccountId(2);f.model.selectAccount();
  const own={...credit,fromAccountId:1,toAccountId:2,direction:'DEBIT'};f.setRows([own]);
  f.setAccounts([{...account,balance:499000},{...account,accountId:2,balance:11000}]);await f.model.refreshPending();
  assert.equal(f.model.selectedAccountId(),2);assert.equal(f.model.account().balance,11000);assert.equal(f.model.isCredit(own),true);
  f.model.selectedAccountId(1);f.model.selectAccount();assert.equal(f.model.isCredit(own),false);f.model.disconnected();
});
test('balance polling skips overlaps, ignores late responses and never restores data after logout',async()=>{
  const f=dashboard();await f.model.load();let finish,calls=0;
  f.setAccountCall(()=>{calls++;return new Promise(r=>finish=r);});
  const pending=f.model.refreshPending();await f.model.refreshPending();assert.equal(calls,1);
  f.model.disconnected();finish([{...account,balance:999999}]);await pending;assert.equal(f.model.account().balance,500000);
  const g=dashboard();await g.model.load();g.setTransactionCall(async()=>{throw new ApiError(401);});await g.model.refreshPending();
  assert.equal(g.model.account(),null);assert.equal(g.model.accounts().length,0);assert.equal(g.timers.size,0);
  assert.deepEqual(g.redirects,['/login?reason=session-expired']);
});
test('transient refresh failure keeps the last confirmed balance and warns instead of inventing zero',async()=>{
  const f=dashboard();await f.model.load();f.setAccountCall(async()=>{throw new ApiError(500);});await f.model.refreshPending();
  assert.equal(f.model.account().balance,500000);assert.match(f.model.transactionError(),/last loaded/);f.model.disconnected();
});
test('empty transaction history keeps polling for incoming credits',async()=>{
  const timers=[];let rows=[];
  const Model=load('viewModels/transactions.ts',{'knockout':ko,'../appController':{default:{profile:ko.observable(null)}},
    '../services/apiError':{ApiError},'../utils/protection':protection,
    '../services/transactionService':{transactionService:{list:async()=>rows}},
    '../utils/chime':{rememberSettled(){},chimeIfSettled(){}}},{setInterval:(fn,ms)=>{timers.push({fn,ms});return timers.length;}});
  const m=new Model();await m.load();assert.ok(timers.some(t=>t.ms===3000));rows=[credit];await m.refresh();
  assert.equal(m.transactions().length,1);assert.equal(m.displayName(credit),'Sender');m.disconnected();
});
test('payment result refreshes the server balance and available funds without clearing the draft',async()=>{
  let current={...account},funds={accountId:1,balance:500000,availableToTransfer:495000,reservedBalance:0};
  const Model=load('viewModels/send-money.ts',{'knockout':ko,'../services/apiError':{ApiError},'../utils/protection':protection,
    '../services/accountService':{accountService:{list:async()=>[current],funds:async()=>funds}},
    '../services/beneficiaryService':{beneficiaryService:{list:async()=>[]}},'../utils/chime':{chimeForPayment(){}}});
  const m=new Model({router:{go:async()=>{}}});await m.load();m.amount('1000');m.purpose('Lunch');
  current={...account,balance:499000};funds={...funds,balance:499000,availableToTransfer:494000};
  m.acceptResult({...credit,direction:'DEBIT'});await new Promise(setImmediate);
  assert.equal(m.account().balance,499000);assert.equal(m.funds().availableToTransfer,494000);
  assert.equal(m.amount(),'1000');assert.equal(m.purpose(),'Lunch');m.disconnected();
});
test('profile balance refresh updates all accounts and keeps the selected account',async()=>{
  const rows=[{...account,balance:499000},{...account,accountId:2,balance:11000}];
  const Model=load('viewModels/profile.ts',{'knockout':ko,'../appController':{default:{profile:ko.observable(null)}},
    '../services/accountService':{accountService:{list:async()=>rows,funds:async id=>({accountId:id,balance:11000})}}});
  const m=new Model();m.loading(false);m.selectedAccountId(2);await m.refreshBalances();
  assert.equal(m.account().accountId,2);assert.equal(m.account().balance,11000);assert.equal(m.accounts()[0].balance,499000);m.disconnected();
});

test('admin account view refreshes balances and ignores a response for a previously selected user',async()=>{
  let finish,rows=[{...account,balance:'500000.00'}],request=async()=>rows;
  const {AdminUsersModel}=load('viewModels/adminUsers.ts',{'knockout':ko,'../services/apiError':{ApiError},
    '../services/adminUserService':{adminUserService:{accounts:()=>request()}}});
  const m=new AdminUsersModel();await m.select({userId:1});rows=[{...account,balance:'499000.00'}];
  await m.refreshBalances();assert.equal(m.account().balance,'499000.00');
  request=()=>new Promise(r=>finish=r);const pending=m.refreshBalances();
  request=async()=>[{...account,accountId:2,balance:'11000.00'}];await m.select({userId:2});
  finish([{...account,balance:'old-user'}]);await pending;
  assert.equal(m.account().accountId,2);assert.equal(m.account().balance,'11000.00');m.disconnected();
});

test('transaction history never restarts polling after a session-expired response',async()=>{
  const timers=[];
  const Model=load('viewModels/transactions.ts',{'knockout':ko,'../appController':{default:{profile:ko.observable(null)}},
    '../services/apiError':{ApiError},'../utils/protection':protection,
    '../services/transactionService':{transactionService:{list:async()=>{throw new ApiError(401);}}}},
    {setInterval:fn=>{timers.push(fn);return timers.length;}});
  const m=new Model();await m.load();assert.equal(timers.length,0);assert.equal(m.transactions().length,0);m.disconnected();
});
