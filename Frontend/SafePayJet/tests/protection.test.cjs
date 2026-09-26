const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');

function loadProtection(file = 'utils/protection.ts') {
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/', file), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})')(() => ({}), module, module.exports);
  return module.exports;
}
const p = loadProtection();
const categories = loadProtection('constants/paymentCategories.ts');

test('status and tier labels stay human', () => {
  assert.equal(p.statusLabel('PROTECTED'), 'Protected — you can still cancel');
  assert.equal(p.statusLabel('HARD_HOLD'), 'Held for extra review');
  assert.equal(p.tierLabel('HIGH'), 'Longer pause');
});
test('risk reasons drop the score line', () => {
  const reasons = p.explainReasons('Amount at most INR 10,000 (+0); Device evidence is unknown (+2); Total score 3: MEDIUM');
  assert.equal(reasons.length, 2);
  assert.ok(reasons[0].includes('Amount at most INR 10,000'));
  assert.ok(!reasons.some((line) => /Total score/.test(line)));
});
test('cancel eligibility uses server expiry only', () => {
  const future = new Date(Date.now() + 20000).toISOString();
  const tx = { state: 'PROTECTED', canCancel:true, protectionDeadline:Date.parse(future), protectionExpiresAt: future };
  assert.equal(p.canCancelPayment(tx, Date.now()), true);
  assert.equal(p.canCancelPayment({ ...tx, state: 'SETTLED' }, Date.now()), false);
  assert.equal(p.canCancelPayment({ state: 'PROTECTED', protectionExpiresAt: '2026-09-15T15:00:00' }, Date.now()), false);
});
test('flux phases and arc follow progress', () => {
  assert.equal(p.phaseFor(0), 'held');
  assert.equal(p.phaseFor(40), 'cancellable');
  assert.equal(p.phaseFor(100), 'settling');
  assert.equal(p.arcOffset(100), 0);
  assert.ok(p.arcOffset(0) > 250);
  assert.equal(p.fluxLetters('held').length, 4);
});
test('countdown formats remaining seconds', () => {
  assert.equal(p.formatCountdown(9), '0:09');
  assert.equal(p.formatCountdown(60), '1:00');
});
test('progress kind follows state and pause length', () => {
  assert.equal(p.progressKind(null, true), 'check');
  assert.equal(p.progressKind({ state: 'PROTECTED', protectionSeconds: 10 }), 'short');
  assert.equal(p.progressKind({ state: 'PROTECTED', protectionSeconds: 60 }), 'long');
  assert.equal(p.progressKind({ state: 'HARD_HOLD' }), 'hold');
  assert.equal(p.progressKind({ state: 'SETTLED' }), 'done');
  assert.equal(p.progressKind({ state: 'CANCELLED' }), 'cancelled');
});

test('hard holds can be cancelled without a timer only with server permission', () => {
  const tx = {state:'HARD_HOLD',canCancel:true,protectionSeconds:0};
  assert.equal(p.canCancelPayment(tx,Date.now()),true);
  assert.equal(p.canCancelPayment(tx,Date.now()+86400000),true);
  for (const canCancel of [false,undefined]) assert.equal(p.canCancelPayment({...tx,canCancel},Date.now()),false);
  for (const state of ['SETTLED','CANCELLED','CREATED']) assert.equal(p.canCancelPayment({...tx,state},Date.now()),false);
  assert.equal(p.canCancelPayment({...tx,direction:'CREDIT'},Date.now()),false);
});

test('payment cards have no manual Refresh status action', () => {
  for(const name of ['send-money','transactions','dashboard']) {
    const html=fs.readFileSync(path.join(__dirname,'../src/ts/views',name+'.html'),'utf8');
    assert.doesNotMatch(html,/Refresh status|click:\s*(?:refreshResult|refreshDetail)/i);
  }
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
  assert.match(html,/isProtected\(\).*\|\| isHold\(\)/);
});

test('medium and high risk share the same gauge with their actual remaining time',()=>{
  const now=100000;
  for(const [riskTier,protectionSeconds,remaining] of [['MEDIUM',10,5],['HIGH',30,15]]) {
    const tx={state:'PROTECTED',riskTier,protectionSeconds,protectionDeadline:now+remaining*1000};
    const gauge=p.paymentGauge(tx,now);
    assert.equal(gauge.mode,'timed');
    assert.equal(gauge.label,p.formatCountdown(remaining));
    assert.equal(gauge.offset,p.FLUX_ARC/2);
  }
});

test('very high risk waits for administrator review, never an invented countdown',()=>{
  const tx={state:'HARD_HOLD',riskTier:'VERY_HIGH',protectionSeconds:0};
  const gauge=p.paymentGauge(tx,Date.now());
  assert.equal(gauge.mode,'waiting');assert.equal(gauge.label,'Awaiting review');
  assert.match(gauge.caption,/No timed release/);
  assert.equal(p.paymentGauge(tx,Date.now()+86400000).label,'Awaiting review');
});

test('elapsed or missing protection deadline waits for server status instead of settling locally',()=>{
  for(const protectionDeadline of [undefined,100]) {
    const tx={state:'PROTECTED',riskTier:'HIGH',protectionSeconds:60,protectionDeadline};
    const gauge=p.paymentGauge(tx,200);
    assert.equal(gauge.mode,'waiting');assert.equal(gauge.label,'Checking status');
    assert.equal(tx.state,'PROTECTED');assert.doesNotMatch(gauge.label,/0:00|Settled/);
  }
});

test('all settled tiers share the completed semicircle and final states do not animate',()=>{
  for(const riskTier of ['LOW','MEDIUM','HIGH','VERY_HIGH']) {
    const gauge=p.paymentGauge({state:'SETTLED',riskTier},Date.now());
    assert.equal(gauge.mode,'settled');assert.equal(gauge.offset,0);assert.equal(gauge.label,'Settled');
  }
  assert.equal(p.paymentGauge({state:'CANCELLED'},0).label,'Cancelled');
  assert.equal(p.paymentGauge({state:'REJECTED'},0).label,'Not approved');
  assert.equal(p.paymentGauge({state:'REJECTED'},0).mode,'stopped');
  assert.equal(p.paymentGauge(null,0).mode,'waiting');
});

test('payment template uses a common semicircle instead of tier-specific bars or rings',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/send-money.html'),'utf8');
  assert.match(html,/css: 'gauge-' \+ gauge\(\)\.mode/);
  assert.match(html,/payment-arc-value/);
  assert.doesNotMatch(html,/long-track|hold-pulse|done-burst|cancel-track|oj-progress-circle|sheetKind/);
  const admin=fs.readFileSync(path.join(__dirname,'../src/ts/views/admin.html'),'utf8');
  assert.match(admin,/admin-hold-review/);assert.match(admin,/<p class="admin-review-amount"/);
});

test('transactions show session email without using it as caller identity', async () => {
  const profile = ko.observable({ email: 'current@example.test' });
  const calls = [], module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/transactions.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const imports = {
    knockout: ko, '../appController': { default: { profile } }, '../accUtils': { announce() {} },
    '../constants/paymentCategories': categories, '../services/types': {}, '../utils/protection': p, '../services/apiError': {},
    '../utils/chime': { armAudio() {}, chimeForPayment() {}, chimeIfSettled() {}, rememberSettled() {}, playChime() {}, startRing() {}, stopRing() {} },
    '../services/transactionService': { transactionService: { list: async (...args) => { calls.push(args); return []; } }, newIdempotencyKey: () => 'k' },
    './verifyCall': { VerifyCallModel: class { open = ko.observable(false); ring() {} dispose() {} } },
    'ojs/ojdialog': {}, 'ojs/ojbutton': {}, 'ojs/ojavatar': {}, 'ojs/ojdrawerpopup': {}
  };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', { setInterval: () => 1, clearInterval: () => {} })(key => imports[key], module, module.exports);
  const model = new module.exports();
  assert.equal(model.signedInEmail(), 'current@example.test');
  profile({ email: 'different@example.test' });
  assert.equal(model.signedInEmail(), 'different@example.test');
  profile(null); assert.equal(model.signedInEmail(), '');
  await model.load(); assert.ok(calls.length > 0);
  assert.ok(calls.every(args => args.length === 0));
  assert.equal(model.error(), '');
  assert.doesNotMatch(source, /shreya@example|this\.email|userEmail/);
  const html = fs.readFileSync(path.join(__dirname, '../src/ts/views/transactions.html'), 'utf8');
  assert.match(html, /Signed in as/);
  assert.match(html, /text: signedInEmail/);
});

test('a loaded list is kept even if receipt bookkeeping throws', async () => {
  const profile = ko.observable({ email: 'current@example.test' });
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/transactions.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const row = { transactionId: 1, state: 'SETTLED', amount: 111111, beneficiaryName: 'Rohan Gupta', createdAt: '2026-09-16T19:10:22.000Z', riskTier: 'VERY_HIGH' };
  const imports = {
    knockout: ko, '../appController': { default: { profile } }, '../accUtils': { announce() {} },
    '../constants/paymentCategories': categories, '../services/types': {}, '../utils/protection': p, '../services/apiError': {},
    '../utils/chime': { armAudio() {}, chimeForPayment() {}, chimeIfSettled() {}, rememberSettled() { throw new Error('chime'); }, playChime() {}, startRing() {}, stopRing() {} },
    '../services/transactionService': { transactionService: { list: async () => [row] }, newIdempotencyKey: () => 'k' },
    './verifyCall': { VerifyCallModel: class { open = ko.observable(false); ring() {} dispose() {} } },
    'ojs/ojdialog': {}, 'ojs/ojbutton': {}, 'ojs/ojavatar': {}, 'ojs/ojdrawerpopup': {}
  };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', { setInterval: () => 1, clearInterval: () => {}, Intl, Date })(key => imports[key], module, module.exports);
  const model = new module.exports();
  await model.load();
  assert.equal(model.transactions().length, 1);
  assert.equal(model.error(), '');
});

test('cancelling a protected payment plays the cancel chime', async () => {
  const profile = ko.observable({ email: 'current@example.test' });
  const module = { exports: {} };
  const heard = [];
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels/transactions.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const protectedRow = {
    canCancel:true,protectionDeadline:Date.now()+30000,transactionId: 8, state: 'PROTECTED', amount: 62000, beneficiaryName: 'Rohan Gupta',
    createdAt: new Date().toISOString(), protectionExpiresAt: new Date(Date.now() + 30000).toISOString(),
    protectionSeconds: 60, riskTier: 'HIGH'
  };
  const cancelled = { ...protectedRow, state: 'CANCELLED', cancelledAt: new Date().toISOString() };
  const imports = {
    knockout: ko, '../appController': { default: { profile } }, '../accUtils': { announce() {} },
    '../constants/paymentCategories': categories, '../services/types': {}, '../utils/protection': p, '../services/apiError': {},
    '../utils/chime': {
      armAudio() { heard.push('arm'); },
      chimeForPayment(tx) { if (tx && tx.state === 'CANCELLED') heard.push('off'); },
      chimeIfSettled() {}, rememberSettled() {}, playChime() {}, startRing() {}, stopRing() {}
    },
    '../services/transactionService': {
      transactionService: { list: async () => [protectedRow], get: async () => protectedRow, cancel: async () => cancelled },
      newIdempotencyKey: () => 'k'
    },
    './verifyCall': { VerifyCallModel: class { open = ko.observable(false); ring() {} dispose() {} } },
    'ojs/ojdialog': {}, 'ojs/ojbutton': {}, 'ojs/ojavatar': {}, 'ojs/ojdrawerpopup': {}
  };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    setInterval: () => 1, clearInterval: () => {}, Intl, Date, document: { title: '' }
  })(key => imports[key], module, module.exports);
  const model = new module.exports();
  await model.load();
  await model.cancel(protectedRow);
  assert.equal(model.transactions()[0].state, 'CANCELLED');
  assert.ok(heard.includes('off'));
});
