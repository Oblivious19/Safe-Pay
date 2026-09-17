const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('typescript');
const ko = require('knockout');
class ApiError extends Error { constructor(status) { super('fixture'); this.status = status; } }
// Isolated test fixtures only. Production dashboard has no fallback/mock data.
const account = { accountId: 1, userId: 2, accountNumber: '500000001234', accountType: 'SAVINGS', status: 'ACTIVE', balance: 450000 };
const payment = (id, state = 'SETTLED', riskTier = 'LOW') => ({
  transactionId: id, state, riskTier, amount: 100.25, beneficiaryName: 'Fixture recipient',
  createdAt: `2026-09-${String(id).padStart(2,'0')}T12:00:00`, protectionExpiresAt: ''
});
function loadProtection() {
  const source = fs.readFileSync(path.join(__dirname,'../src/ts/utils/protection.ts'),'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})')(()=>({}),module,module.exports);
  return module.exports;
}
const protection = loadProtection();
function fixture(accountCall = () => Promise.resolve(account), transactionCall = () => Promise.resolve([])) {
  const calls = [], redirects = [], module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname,'../src/ts/viewModels/dashboard.ts'),'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const imports = {
    knockout: ko, '../accUtils': { announce: () => {} }, '../services/apiError': { ApiError }, '../utils/protection': protection,
    'ojs/ojdialog': {}, 'ojs/ojbutton': {}, 'ojs/ojavatar': {},
    '../services/accountService': { accountService: { getCurrent: (...args) => { calls.push(['account', ...args]); return accountCall(); } } },
    '../services/transactionService': { transactionService: { list: (...args) => { calls.push(['transactions', ...args]); return transactionCall(); }, get: async () => ({}), cancel: async () => ({}) }, newIdempotencyKey: () => 'k' },
    '../utils/chime': { armAudio() {}, chimeForPayment() {} }
  };
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})', { document: { title: '' }, Intl, setInterval: () => 1, clearInterval: () => {},
    window: { location: { replace: url => redirects.push(url) } } })(p => imports[p],module,module.exports);
  return { model: new module.exports(), calls, redirects };
}
test('dashboard loads the two session-scoped resources without identity arguments', async () => {
  const f = fixture(); await f.model.load();
  assert.deepEqual(f.calls, [['account'],['transactions']]);
  assert.equal(f.model.account(),account); assert.equal(f.model.canSend(),true);
  assert.equal('email' in f.model,false); assert.equal('loadAccounts' in f.model,false);
});
test('full Indian money grouping and masked account, never internal ID', () => {
  const { model } = fixture();
  assert.equal(model.formatMoney(450000),'₹4,50,000.00');
  assert.equal(model.formatMoney(150000.5),'₹1,50,000.50');
  assert.equal(model.maskAccount(account.accountNumber),'•••• 1234');
});
test('recent payments are newest first and limited to five without changing source array', async () => {
  const list = Array.from({length:7},(_,i)=>payment(i+1));
  const { model } = fixture(undefined,()=>Promise.resolve(list)); await model.load();
  assert.equal(model.recent().length,5); assert.equal(model.recent()[0].transactionId,7);
  assert.equal(list[0].transactionId,1);
});
test('pending area reflects server states, not invented timers or risk decisions', async () => {
  const { model } = fixture(undefined,()=>Promise.resolve([payment(1),payment(2,'PROTECTED','HIGH'),payment(3,'HARD_HOLD','VERY_HIGH')]));
  await model.load(); assert.equal(model.pending().length,2);
  assert.equal(model.statusLabel('HARD_HOLD'),'Held for extra review');
  assert.equal(model.riskClass(payment(1,'PROTECTED','LOW')),'risk-neutral');
  assert.equal(model.riskClass(payment(1,'PROTECTED','MEDIUM')),'risk-amber');
  assert.equal(model.riskClass(payment(1,'PROTECTED','HIGH')),'risk-orange');
  assert.equal(model.riskClass(payment(1,'HARD_HOLD','LOW')),'risk-red');
  assert.equal(model.riskClass(undefined),'risk-neutral');
});
test('empty payments are not an error and contain no invented data', async () => {
  const { model } = fixture(); await model.load();
  assert.equal(model.recent().length,0); assert.equal(model.pending().length,0); assert.equal(model.transactionError(),'');
});
for (const resource of ['account','transactions']) test(`401 on ${resource} hides all customer data`, async () => {
  const fail=()=>Promise.reject(new ApiError(401));
  const { model, redirects } = fixture(resource==='account'?fail:undefined,resource==='transactions'?fail:undefined);
  await model.load(); assert.equal(model.sessionExpired(),true); assert.equal(model.account(),null);
  assert.equal(model.transactions().length,0); assert.equal(model.canSend(),false);
  assert.deepEqual(redirects, ['/login?reason=session-expired']);
});
test('404 account produces unavailable state, not zero balance', async () => {
  const { model } = fixture(()=>Promise.reject(new ApiError(404))); await model.load();
  assert.equal(model.account(),null); assert.match(model.accountError(),/not available/); assert.equal(model.canSend(),false);
});
test('transaction failure preserves real account and shows error, not empty history', async () => {
  const { model } = fixture(undefined,()=>Promise.reject(new ApiError(500))); await model.load();
  assert.equal(model.account(),account); assert.notEqual(model.transactionError(),''); assert.equal(model.canSend(),false);
});
test('blocked and closed accounts cannot activate Send Money', async () => {
  for(const status of ['BLOCKED','CLOSED']) {
    const { model }=fixture(()=>Promise.resolve({...account,status})); await model.load(); assert.equal(model.canSend(),false);
  }
});
test('loading remains active until both responses arrive; disconnected responses are ignored', async () => {
  let finish; const { model } = fixture(()=>new Promise(resolve=>{finish=resolve;}));
  const pending=model.load(); assert.equal(model.loading(),true); assert.equal(model.account(),null);
  model.disconnected(); finish(account); await pending; assert.equal(model.account(),null);
});
test('dashboard markup has no email loader, secrets or debug fields', () => {
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/dashboard.html'),'utf8');
  assert.doesNotMatch(html,/Customer email|Load account|password|userId|accountId|riskReason|riskScore/);
  assert.match(html,/aria-busy/); assert.match(html,/dashboard-empty/); assert.match(html,/sessionExpired/);
});
