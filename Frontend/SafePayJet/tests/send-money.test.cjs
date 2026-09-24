const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
class ApiError extends Error { constructor(status) { super('Safe error'); this.status = status; } }
const recipient = { accountId:2,beneficiaryId: 1, beneficiaryName: 'Recipient', bankAccountNumber: '0012345678', ifsc: 'HDFC0001234', status: 'ACTIVE', createdAt: new Date().toISOString() };
const account = { accountId: 2, status: "ACTIVE", accountNumber: '5000001234', accountType: 'SAVINGS', balance: 450000 };
function loadTs(rel) {
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/', rel), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})')(() => ({}), module, module.exports);
  return module.exports;
}
const protection = loadTs('utils/protection.ts');
const categories = loadTs('constants/paymentCategories.ts');
test('category threshold and labels match the reference enum exactly',()=>{
  for(const amount of ['0.01','50000','100000','100000.00','000100000.00']) assert.equal(categories.categoryApplies(amount),false);
  for(const amount of ['100000.01','100001','9999999999999999.99']) assert.equal(categories.categoryApplies(amount),true);
  assert.equal(categories.PAYMENT_CATEGORIES.map(c=>c.value).join(','),'MEDICAL,LOAN,FRIENDS_FAMILY,INVESTMENTS,OTHERS');
  assert.equal(categories.categoryLabel(null),'Not specified');
});
test('category requires a valid choice above one lakh and Others requires a note',async()=>{
  const f=fixture();await f.model.load();f.model.choose(recipient);f.model.amount('100000.01');
  await f.model.continueDetails();assert.ok(f.model.categoryError());assert.notEqual(f.model.step(),'review');
  f.model.category('OTHERS');f.model.purpose('  ');await f.model.continueDetails();assert.ok(f.model.purposeError());
  f.model.purpose('x'.repeat(141));await f.model.continueDetails();assert.ok(f.model.purposeError());
  f.model.purpose('x'.repeat(140));await f.model.continueDetails();assert.equal(f.model.step(),'review');
  f.model.amount('100000.00');assert.equal(f.model.category(),undefined);assert.equal(f.model.highValue(),false);
  f.model.disconnected();
});
test('category and trimmed Others purpose survive the PIN step and an ambiguous retry unchanged',async()=>{
  const posts=[];let count=0;
  const f=await review({create:async(body,key)=>{posts.push({body:{...body},key});if(!count++)throw new ApiError(0);return {...result,amount:100001,state:'HARD_HOLD',category:'OTHERS'};}});
  f.model.amount('100001');f.model.category('OTHERS');f.model.purpose('  Tuition  ');await f.model.confirm();
  assert.equal(f.model.reviewCategory(),'OTHERS');assert.equal(posts.length,0);
  f.model.sPin('123456');await f.model.submitPin();assert.equal(posts.length,1);
  f.model.category('LOAN');f.model.purpose('Changed draft');await f.model.confirm();
  assert.equal(f.model.reviewCategory(),'OTHERS');f.model.sPin('123456');await f.model.submitPin();
  assert.equal(posts.length,2);assert.deepEqual(posts[1],posts[0]);
  assert.equal(posts[0].body.category,'OTHERS');assert.equal(posts[0].body.purpose,'Tuition');f.model.disconnected();
});
const ojet = { 'ojs/ojprogress-circle': {}, 'ojs/ojdialog': {}, 'ojs/ojbutton': {}, 'ojs/ojtrain': {}, 'ojs/ojinputtext': {}, 'ojs/ojavatar': {}, 'ojs/ojmessages': {} };
function fixture(list = async () => [recipient], step, transactions = {}, pin, timer = fn => {fn();return 1;}) {
  let fundsCall=async id=>({accountId:id,balance:450000,reservedBalance:0,minimumBalance:0,availableToTransfer:450000});
  const calls = [], redirects = [], routes = [], module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/send-money.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  // The verification call has its own tests; here it only records that it was rung.
  const rang = [];
  const heard = [];
  class CallStub {
    constructor(onSettled) { this.onSettled = onSettled; this.open = ko.observable(false); }
    ring(tx) { rang.push(tx); this.open(true); }
    dispose() {}
  }
  const imports = { knockout: ko, '../services/apiError': { ApiError }, '../utils/protection': protection, ...ojet,
    '../constants/paymentCategories': categories,
    './verifyCall': { VerifyCallModel: CallStub },
    '../utils/chime': {
      armAudio() {},
      playChime: kind => heard.push(kind),
      chimeForPayment: tx => {
        if (!tx) return;
        if (tx.state === 'SETTLED') heard.push('ok');
        else if (tx.state === 'PROTECTED' || tx.state === 'HARD_HOLD') heard.push('paid');
        else if (tx.state === 'CANCELLED' || tx.state === 'REJECTED') heard.push('off');
      },
      chimeIfSettled: tx => { if (tx && tx.state === 'SETTLED') heard.push('ok'); },
      rememberSettled() {},
      startRing() {}, stopRing() {}
    },
    '../services/demoSession': { demoRequiresPin: () => !!pin, verifyDemoPin: value => value === pin, demoPinHint: () => pin || '' },
    '../services/beneficiaryService': { beneficiaryService: { list: () => { calls.push('beneficiaries'); return list(); } } },
    '../services/accountService': { accountService: { funds: id => fundsCall(id), list: async () => { calls.push('account'); return [account]; } } },
    '../services/transactionService': { transactionService: transactions, newIdempotencyKey: () => 'unique-key-' + routes.length } };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    TextEncoder, setInterval: () => 1, clearInterval: () => {},
    setTimeout: timer, clearTimeout: () => {},
    document: { title: '', getElementById: () => ({ focus() {} }) },
    window: { location: { replace: url => redirects.push(url) } }
  })(p => imports[p], module, module.exports);
  let model;
  model = new module.exports({ params: { step }, router: { go: async route => { routes.push(route); if (route.path === 'send-money') model.parametersChanged(route.params); } } });
  return { model, calls, redirects, routes, source, rang, heard, setFundsCall: value=>fundsCall=value };
}
test('step 1 loads real service lists/account; Continue initially disabled', async () => {
  const f = fixture(); assert.equal(f.model.canContinue(), false); await f.model.load();
  assert.equal(f.model.beneficiaries()[0], recipient); assert.equal(f.model.account(), account);
  assert.deepEqual(f.calls, ['beneficiaries', 'account']); assert.equal(f.model.canContinue(), false);
});
test('empty list cannot continue', async () => { const f = fixture(async () => []); await f.model.load(); assert.equal(f.model.beneficiaries().length, 0); assert.equal(f.model.canContinue(), false); });
test('select and Continue navigate without another API call; back preserves draft', async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); await f.model.next();
  assert.equal(f.model.step(), 'details'); assert.equal(f.model.selected(), recipient); assert.equal(f.calls.length, 2);
  f.model.amount('12.25'); f.model.purpose('Lunch'); await f.model.back();
  assert.equal(f.model.step(), 'beneficiary'); assert.equal(f.model.amount(), '12.25');
});
test('401 redirects to login', async () => { const f = fixture(async () => { throw new ApiError(401); }); await f.model.load(); assert.deepEqual(f.redirects, ['/login?reason=session-expired']); assert.equal(f.model.selected(), null); });
test('direct details entry without draft returns to selection', () => { const f = fixture(); f.model.parametersChanged({ step: 'details' }); assert.equal(f.model.step(), 'beneficiary'); assert.equal(f.routes[0].params.step, 'beneficiary'); });
for (const value of ['', 'abc', '-1', '0', '0.00', '1.234', '10000000000000000', '1e3', '1,000']) test(`invalid amount ${JSON.stringify(value)} makes no extra call`, async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); f.model.amount(value); await f.model.continueDetails();
  assert.ok(f.model.amountError()); assert.equal(f.calls.length, 2); assert.equal(f.model.notice(), '');
  assert.equal(f.routes.length, 0);
});
test('amount above the available balance is blocked before any call', async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); await f.model.next();
  f.model.amount('600000');
  assert.match(f.model.amountMessage(), /Insufficient balance.*₹4,50,000\.00/);
  assert.equal(f.model.amountReady(), false);
  await f.model.continueDetails();
  assert.equal(f.model.step(), 'details'); assert.equal(f.calls.length, 2);
});
test('no invented payment ceiling and live feedback clears for valid amounts', async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); await f.model.next();
  f.model.account({...account,balance:2000000}); f.model.funds({...f.model.funds(),balance:2000000,availableToTransfer:2000000}); f.model.amount('1000000.01'); assert.equal(f.model.amountMessage(), '');
  f.model.amount('abc'); assert.match(f.model.amountMessage(), /digits only/);
  f.model.amount(''); assert.equal(f.model.amountIssue(), '');
  f.model.amount('2500'); assert.equal(f.model.amountMessage(), ''); assert.equal(f.model.amountReady(), true);
  await f.model.continueDetails(); assert.equal(f.model.step(), 'review');
});
test('purpose limit and exact Indian grouping', async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); f.model.amount('450000.50');
  assert.equal(f.model.amountPreview(), '₹4,50,000.50'); assert.equal(f.model.money(450000), '₹4,50,000.00');
  f.model.purpose('x'.repeat(256)); await f.model.continueDetails(); assert.ok(f.model.purposeError());
  f.model.amount('9999999999999999.99'); assert.equal(f.model.amountPreview(), '₹9,99,99,99,99,99,99,999.99');
});
test('valid details navigates to review, disables duplicate Continue and never posts', async () => {
  const f = fixture(); await f.model.load(); f.model.choose(recipient); f.model.amount('0.01');
  const pending = f.model.continueDetails(); assert.equal(f.model.busy(), true);
  await f.model.continueDetails(); await pending; assert.equal(f.model.busy(), false);
  assert.equal(f.model.step(), 'review'); assert.equal(f.routes.length, 1);
  assert.equal(f.routes[0].params.step, 'review'); assert.equal(f.calls.length, 2);
  assert.doesNotMatch(f.source, /initiateTransaction|localStorage|sessionStorage|fetch\(/);
});
test('review preserves beneficiary, amount, optional note, account and balance; back retains draft', async () => {
  const f=fixture(); await f.model.load(); f.model.choose(recipient); await f.model.next();
  f.model.amount('5000.50'); f.model.purpose('Lunch & travel'); await f.model.continueDetails();
  assert.equal(f.model.step(),'review'); assert.equal(f.model.selected(),recipient);
  assert.equal(f.model.amount(),'5000.50'); assert.equal(f.model.purpose(),'Lunch & travel');
  assert.equal(f.model.account(),account); assert.equal(f.model.account().balance,450000);
  await f.model.back(); assert.equal(f.model.step(),'details'); assert.equal(f.model.amount(),'5000.50');
  assert.equal(f.model.selected(),recipient); assert.equal(f.model.purpose(),'Lunch & travel');
  assert.deepEqual(f.calls,['beneficiaries','account']);
});
test('missing optional purpose allows review', async () => {
  const f=fixture(); await f.model.load(); f.model.choose(recipient); f.model.amount('50');
  await f.model.continueDetails(); assert.equal(f.model.step(),'review'); assert.equal(f.model.purposeError(),'');
});
test('invalid amount stays on details with inline error', async () => {
  const f=fixture(); await f.model.load(); f.model.choose(recipient); await f.model.next();
  const routes=f.routes.length; f.model.amount('0'); await f.model.continueDetails();
  assert.equal(f.model.step(),'details'); assert.ok(f.model.amountError()); assert.equal(f.routes.length,routes);
});
test('direct review without draft returns to beneficiary selection', () => {
  const f=fixture(); f.model.parametersChanged({step:'review'});
  assert.equal(f.model.step(),'beneficiary'); assert.equal(f.routes[0].params.step,'beneficiary');
});
test('review route cannot bypass invalid draft validation', async () => {
  const f=fixture(); await f.model.load(); f.model.choose(recipient); f.model.amount('-1');
  f.model.parametersChanged({step:'review'}); assert.equal(f.model.step(),'details');
});
test('review payment action is guarded by busy state', () => {
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
  assert.match(html,/click: confirm, disable: busy/);
  assert.match(html,/Go Back/); assert.match(html,/text: amountPreview/);
});
const result = {transactionId:123, transactionRef:'TXN-123',amount:50,state:'SETTLED',beneficiaryName:'Recipient',protectionExpiresAt:'',protectionSeconds:0};
test('a verified hold stops asking for a call', async () => {
  const settled = { ...result, transactionId: 322, state: 'SETTLED', verification: 'VERIFIED' };
  const f = fixture(undefined, undefined, { create: async () => settled });
  await f.model.load(); f.model.choose(recipient); await f.model.next();
  f.model.amount('150000'); f.model.category('MEDICAL'); await f.model.continueDetails();
  await pay(f.model);
  assert.equal(f.model.needsCall(), false);
  assert.equal(f.rang.length, 0);
  assert.deepEqual(f.heard, ['ok']);
});
test('after a settled payment tapping away closes the receipt', async () => {
  const f = fixture(undefined, undefined, { create: async () => result });
  await f.model.load(); f.model.choose(recipient); await f.model.next();
  f.model.amount('500'); await f.model.continueDetails();
  await pay(f.model);
  assert.equal(f.model.canLeave(), true);
  await f.model.leave();
  assert.equal(f.model.result(), null);
  assert.equal(f.model.step(), 'beneficiary');
  assert.equal(f.model.sheetOpen(), false);
  assert.match(f.source, /canLeave/);
});
async function review(transactions) {
 const f=fixture(undefined,undefined,transactions); await f.model.load();f.model.choose(recipient);f.model.amount('50');f.model.purpose('Lunch');
 await f.model.next();await f.model.continueDetails(); return f;
}
async function pay(model) {
 await model.confirm();
 if (model.pinOpen()) { model.sPin('123456'); await model.submitPin(); }
}

test('reserved funds reduce the spendable limit; exact balance allowed and correction clears the error',async()=>{
  const f=fixture();f.setFundsCall(async id=>({accountId:id,balance:166933,reservedBalance:120000,minimumBalance:0,availableToTransfer:46933}));
  await f.model.load();f.model.choose(recipient);await f.model.next();f.model.amount('46933.01');
  assert.equal(f.model.amountReady(),false);await f.model.continueDetails();assert.equal(f.model.step(),'details');
  assert.match(f.model.amountMessage(),/₹46,933.00/);f.model.amount('46933');
  assert.equal(f.model.amountMessage(),'');assert.equal(f.model.amountReady(),true);
  await f.model.continueDetails();assert.equal(f.model.step(),'review');f.model.disconnected();
});

test('fresh reservations block progression both before Review and before S PIN',async()=>{
  for(const phase of ['review','pin']) {
    const f=await review({create:async()=>{assert.fail('Must not submit');}});
    if(phase==='review')await f.model.back();
    f.setFundsCall(async id=>({accountId:id,balance:450000,reservedBalance:449999,minimumBalance:0,availableToTransfer:1}));
    if(phase==='review') {await f.model.continueDetails();assert.equal(f.model.step(),'details');}
    else {await f.model.confirm();assert.match(f.model.paymentError(),/Insufficient balance/);}
    assert.equal(f.model.pinOpen(),false);assert.equal(f.model.attemptLocked(),false);f.model.disconnected();
  }
});

test('balance request failure fails closed before Review or PIN and can be retried',async()=>{
  for(const phase of ['review','pin']) {
    const f=await review({create:async()=>{assert.fail('Must not submit');}});
    if(phase==='review')await f.model.back();
    f.setFundsCall(async()=>{throw new Error('offline');});
    if(phase==='review')await f.model.continueDetails();else await f.model.confirm();
    assert.equal(f.model.pinOpen(),false);assert.equal(f.model.funds(),null);assert.equal(f.model.amountReady(),false);assert.ok(f.model.fundsError());
    f.setFundsCall(async id=>({accountId:id,balance:50,reservedBalance:0,minimumBalance:0,availableToTransfer:50}));
    await f.model.refreshFunds();assert.equal(f.model.amountReady(),true);f.model.disconnected();
  }
});

test('uncertain payment retry retains original key even when the balance is now unavailable',async()=>{
  const posts=[];const f=await review({create:async(body,key)=>{posts.push({body:{...body},key});throw new ApiError(0);}});
  await pay(f.model);assert.equal(f.model.attemptLocked(),true);
  f.model.funds(null);f.model.fundsError('offline');f.setFundsCall(async()=>{assert.fail('Uncertain retry must not recheck funds');});
  await pay(f.model);assert.equal(posts.length,2);assert.deepEqual(posts[1],posts[0]);f.model.disconnected();
});
test('confirm sends exact body once; double click blocked; response goes to result',async()=>{
 let finish;const posts=[];const f=await review({create:(body,key)=>{posts.push({body,key});return new Promise(r=>finish=r)}});
 await f.model.confirm();assert.equal(posts.length,0);assert.equal(f.model.pinOpen(),true);
 f.model.sPin('123456');const pending=f.model.submitPin();assert.equal(f.model.busy(),true);
 await f.model.confirm();await f.model.submitPin();assert.equal(posts.length,1);
 assert.deepEqual(JSON.parse(JSON.stringify(posts[0].body)),{fromAccountId:2,beneficiaryId:1,amount:'50',purpose:'Lunch'});
 assert.ok(posts[0].key);finish(result);await pending;assert.equal(f.model.step(),'result');assert.equal(f.model.resultTitle(),'Settled immediately');
 await pay(f.model);assert.equal(posts.length,1);
});
for(const state of ['PROTECTED','HARD_HOLD','CANCELLED','REJECTED']) test('server outcome '+state,async()=>{
 const f=await review({create:async()=>({...result,state})});await pay(f.model);assert.equal(f.model.result().state,state);assert.equal(f.model.step(),'result');assert.notEqual(f.model.resultTitle(),'Settled immediately');
});
for(const status of [400,401,403,404,409,500,0]) test('confirm error '+status,async()=>{
 const f=await review({create:async()=>{throw new ApiError(status)}});await pay(f.model);assert.ok(f.model.paymentError());assert.equal(f.model.result(),null);assert.equal(f.model.busy(),false);
 if(status===401)assert.deepEqual(f.redirects,['/login?reason=session-expired']);
});
test('network retry uses frozen payload and same key',async()=>{
 const posts=[];const f=await review({create:async(body,key)=>{posts.push({body:JSON.stringify(body),key});if(posts.length===1)throw new ApiError(0);return result}});
 await pay(f.model);assert.equal(f.model.attemptLocked(),true);f.model.amount('900');await pay(f.model);assert.deepEqual(posts[0],posts[1]);
});
test('final local invalid data never posts and stays on review',async()=>{
 let posts=0;const f=await review({create:async()=>{posts++;return result}});f.model.amount('0');await pay(f.model);assert.equal(posts,0);assert.equal(f.model.step(),'review');assert.ok(f.model.paymentError());
});
test('cancel rereads server state before calling real service with a key',async()=>{
 const protectedResult={...result,state:'PROTECTED',canCancel:true,protectionDeadline:Date.now()+30000,protectionExpiresAt:new Date(Date.now()+30000).toISOString(),protectionSeconds:30};const calls=[];
 const f=await review({create:async()=>protectedResult,get:async id=>{calls.push(['get',id]);return protectedResult},cancel:async(id,key)=>{calls.push(['cancel',id,key]);return {...result,state:'CANCELLED'}}});
 await pay(f.model);await f.model.cancelPayment();assert.equal(calls[0][0],'get');assert.equal(calls[1][0],'cancel');assert.ok(calls[1][2]);assert.equal(f.model.result().state,'CANCELLED');
 assert.ok(f.heard.includes('off'));
});
test('server settled state and ambiguous expiry prevent cancellation',async()=>{
 let posts=0;const f=await review({create:async()=>({...result,state:'PROTECTED',canCancel:true,protectionDeadline:Date.now()+30000,protectionExpiresAt:new Date(Date.now()+30000).toISOString()}),get:async()=>result,cancel:async()=>{posts++;return result}});
 await pay(f.model);await f.model.cancelPayment();assert.equal(posts,0);assert.equal(f.model.result().state,'SETTLED');
 f.model.result({...result,state:'PROTECTED',protectionExpiresAt:'2026-09-15T15:00:00'});assert.equal(f.model.canCancel(),false);
});

test('hard hold cancellation rereads state and retries with the same key',async()=>{
 const held={...result,state:'HARD_HOLD',riskTier:'VERY_HIGH',canCancel:true};const calls=[];
 const f=await review({create:async()=>held,get:async()=>held,cancel:async(id,key)=>{calls.push([id,key]);if(calls.length===1)throw new ApiError(0);return {...held,state:'CANCELLED',canCancel:false};}});
 await pay(f.model);assert.equal(f.model.canCancel(),true);
 await f.model.cancelPayment();assert.ok(f.model.paymentError());
 await f.model.cancelPayment();assert.deepEqual(calls[0],calls[1]);assert.ok(calls[0][1]);
 assert.equal(f.model.result().state,'CANCELLED');assert.equal(f.model.canCancel(),false);
 f.model.disconnected();
});

test('admin approval before hard hold cancellation prevents the cancel POST',async()=>{
 let posts=0;const f=await review({create:async()=>({...result,state:'HARD_HOLD',canCancel:true}),get:async()=>result,cancel:async()=>{posts++;return result;}});
 await pay(f.model);await f.model.cancelPayment();assert.equal(posts,0);assert.equal(f.model.result().state,'SETTLED');
 f.model.disconnected();
});

test('receipt polling survives temporary failures and clears the error on recovery',async()=>{
 let gets=0;const held={...result,state:'HARD_HOLD',canCancel:true};
 const f=await review({create:async()=>held,get:async()=>{if(!gets++)throw new ApiError(0);return {...result,canCancel:false};}});
 await pay(f.model);assert.ok(f.model.timer);
 await f.model.refreshResult();assert.ok(f.model.timer);assert.match(f.model.paymentError(),/retry automatically/);
 assert.equal(f.model.result().state,'HARD_HOLD');
 await f.model.refreshResult();assert.equal(f.model.paymentError(),'');assert.equal(f.model.result().state,'SETTLED');assert.equal(f.model.timer,undefined);
 f.model.disconnected();
});

test('receipt polling stops on session expiry',async()=>{
 const f=await review({create:async()=>({...result,state:'HARD_HOLD',canCancel:true}),get:async()=>{throw new ApiError(401);}});
 await pay(f.model);await f.model.refreshResult();assert.equal(f.model.timer,undefined);assert.equal(f.model.result(),null);
 assert.deepEqual(f.redirects,['/login?reason=session-expired']);f.model.disconnected();
});
test('elapsed countdown never settles payment locally',async()=>{
 const f=await review({create:async()=>({...result,state:'PROTECTED',protectionSeconds:10,canCancel:false,protectionDeadline:Date.now()-1000,protectionExpiresAt:new Date(Date.now()-1000).toISOString()})});
 await pay(f.model);assert.equal(f.model.remaining(),0);assert.equal(f.model.result().state,'PROTECTED');assert.equal(f.model.canCancel(),false);
});
test('development server supports review route', async () => {
  const config=await require('../scripts/hooks/before_serve')({}); const req={method:'GET',url:'/send-money/review'};
  config.preMiddleware[0](req,{},()=>{}); assert.equal(req.url,'/index.html');
});
test('choosing a recipient opens the sheet directly and retains payment review', async () => {
  const f = fixture(); await f.model.load();
  assert.equal(f.model.sheetOpen(), false);
  f.model.pick(recipient); await Promise.resolve();
  assert.equal(f.model.step(), 'details'); assert.equal(f.model.sheetOpen(), true);
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
  assert.match(html,/pay-overlay/);
  assert.doesNotMatch(html,/oj-train/);
  assert.match(html,/click: \$parent\.pick/);
  assert.match(html,/Review payment/);
  assert.match(html,/click: confirm, disable: busy/);
  assert.match(html,/<footer class="pay-action-bar">/);
  assert.match(html,/paymentModal: \{open: sheetOpen, close: back\}/);
});
test('development server supports profile route', async () => {
  const config=await require('../scripts/hooks/before_serve')({}); const req={method:'GET',url:'/profile'};
  config.preMiddleware[0](req,{},()=>{}); assert.equal(req.url,'/index.html');
});
test('loading blocks Continue and leaving clears draft', async () => {
  let finish; const f = fixture(() => new Promise(resolve => { finish = resolve; })); const pending = f.model.load();
  assert.equal(f.model.loading(), true); assert.equal(f.model.canContinue(), false);
  finish([recipient]); await pending; f.model.choose(recipient); f.model.amount('12'); f.model.disconnected();
  assert.equal(f.model.selected(), null); assert.equal(f.model.amount(), '');
});

test('confirmation only opens the PIN card, not a payment request', async () => {
 let posts=0;const f=await review({create:async()=>{posts++;return result;}});
 await f.model.confirm();assert.equal(posts,0);assert.equal(f.model.pinOpen(),true);
 assert.equal(f.model.pinAmount(),'₹50.00');assert.equal(f.model.sPin(),'');
 f.model.amount('900');assert.equal(f.model.pinAmount(),'₹50.00');
 f.model.sPin('123');await f.model.back();assert.equal(f.model.step(),'review');
 assert.equal(f.model.pinOpen(),false);assert.equal(f.model.sPin(),'');
 await f.model.confirm();assert.equal(f.model.pinAmount(),'₹900.00');assert.equal(posts,0);
});
for(const value of ['', '12345', '1234567', 'abcdef', '１２３４５６', ' 123456', '000000']) {
 test('invalid simulated PIN never posts: '+JSON.stringify(value),async()=>{
  let posts=0;const f=await review({create:async()=>{posts++;return result;}});
  await f.model.confirm();f.model.sPin(value);await f.model.submitPin();
  assert.equal(posts,0);assert.ok(f.model.pinError());assert.equal(f.model.pinOpen(),true);
  assert.equal(f.model.result(),null);assert.equal(f.model.busy(),false);
 });
}
for(const [amount,state,riskTier] of [['50','SETTLED','LOW'],['15000','PROTECTED','MEDIUM'],['75000','PROTECTED','HIGH'],['150000','HARD_HOLD','VERY_HIGH']]) {
 test('correct six-digit input waits for explicit confirmation for '+riskTier,async()=>{
  const posts=[];const f=await review({create:async body=>{posts.push(body);return {...result,amount:Number(amount),state,riskTier};}});
  f.model.amount(amount);if(riskTier==='VERY_HIGH')f.model.category('MEDICAL');await f.model.confirm();
  f.model.onPinInput(null,{target:{value:'000000'}});await f.model.submitPin();assert.equal(posts.length,0);assert.ok(f.model.pinError());
  f.model.onPinInput(null,{target:{value:'123456'}});await new Promise(resolve=>setImmediate(resolve));
  assert.equal(posts.length,0);assert.equal(f.model.pinOpen(),true);await f.model.submitPin();
  assert.equal(posts.length,1);assert.equal(f.model.sPin(),'');assert.equal(f.model.pinError(),'');
  assert.equal(f.model.pinOpen(),false);assert.equal(f.model.result().state,state);
  assert.deepEqual(Object.keys(posts[0]).sort(),riskTier==='VERY_HIGH'?['amount','beneficiaryId','category','fromAccountId','purpose']:['amount','beneficiaryId','fromAccountId','purpose']);
  assert.equal(posts[0].amount,amount);f.model.disconnected();
 });
}
test('explicit PIN confirmation waits only one second on the PIN card, prevents duplicates and cancels on leaving',async()=>{
 for(const leave of [false,true]) {
  const timers=[],posts=[];let controlled=false;
  const f=fixture(undefined,undefined,{create:async body=>{posts.push(body);return result;}},undefined,(fn,ms)=>{
   if(controlled)timers.push({fn,ms});else fn();return 1;
  });
  await f.model.load();f.model.choose(recipient);await f.model.next();f.model.amount('50');await f.model.continueDetails();await f.model.confirm();
  f.model.onPinInput(null,{target:{value:'123456'}});assert.equal(posts.length,0);
  controlled=true;const pending=f.model.submitPin();assert.equal(timers.length,1);assert.equal(timers[0].ms,1000);
  assert.equal(f.model.busy(),true);assert.equal(posts.length,0);assert.equal(f.model.pinOpen(),true);
  await f.model.submitPin();assert.equal(timers.length,1);
  if(leave)f.model.disconnected();else timers[0].fn();
  await pending;assert.equal(posts.length,leave?0:1);assert.equal(f.model.pinOpen(),false);
  if(!leave)f.model.disconnected();
 }
});

test('recipient, review and PIN navigation have no artificial timers or full-card loaders',async()=>{
 const timers=[];const f=fixture(undefined,undefined,{},undefined,(fn,ms)=>{timers.push(ms);return 1;});
 await f.model.load();f.model.choose(recipient);await f.model.next();assert.equal(f.model.step(),'details');
 f.model.amount('50');await f.model.continueDetails();assert.equal(f.model.step(),'review');
 await f.model.confirm();assert.equal(f.model.pinOpen(),true);assert.deepEqual(timers,[]);
 const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
 assert.doesNotMatch(html,/pay-stage-transition|transitionLabel|showProcessFlux|Preparing S PIN/);
 assert.match(html,/Processing payment/);f.model.disconnected();
});

test('PIN cannot submit before confirmation and is cleared on navigation/disconnect',async()=>{
 let posts=0;const f=await review({create:async()=>{posts++;return result;}});
 f.model.sPin('123456');await f.model.submitPin();assert.equal(posts,0);
 await f.model.confirm();f.model.sPin('123');f.model.parametersChanged({step:'details'});
 assert.equal(f.model.pinOpen(),false);assert.equal(f.model.sPin(),'');
 await f.model.continueDetails();await f.model.confirm();f.model.sPin('123');f.model.disconnected();
 assert.equal(f.model.pinOpen(),false);assert.equal(f.model.sPin(),'');await f.model.submitPin();assert.equal(posts,0);
});
test('each new payment asks again and never remembers PIN verification',async()=>{
 let posts=0;const f=await review({create:async()=>{posts++;return result;}});
 await pay(f.model);await f.model.leave();f.model.choose(recipient);f.model.amount('60');
 await f.model.next();await f.model.continueDetails();await f.model.confirm();
 assert.equal(posts,1);assert.equal(f.model.pinOpen(),true);assert.equal(f.model.sPin(),'');
});
test('PIN card has a single masked field and a small simulation label',()=>{
 const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
 assert.equal((html.match(/id="payment-s-pin"/g)||[]).length,1);
 assert.match(html, /id="payment-s-pin" type="password" inputmode="numeric"/);
 assert.match(html,/<small[^>]*>Simulated S PIN<\/small>/);
 assert.doesNotMatch(html,/Confirm PIN|123456/);
});
