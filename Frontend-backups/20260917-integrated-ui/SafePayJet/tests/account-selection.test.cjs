const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
const tick = () => new Promise(resolve => setImmediate(resolve));
const accounts = [1, 2, 3].map(accountId => ({ accountId, accountNumber: '50000000000' + accountId,
  accountType: 'SAVINGS', balance: 50000, status: accountId === 3 ? 'BLOCKED' : 'ACTIVE' }));
const beneficiary = accountId => ({ accountId, beneficiaryId: accountId * 10, beneficiaryName: 'Recipient ' + accountId,
  bankAccountNumber: '1234567890', ifsc: 'HDFC0001234', status: 'ACTIVE' });
function pendingStore() {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/services/pendingPayment.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(module,exports){' + code + '\n})', {})(module, module.exports);
  return module.exports;
}
function fixture(screen, overrides = {}, store = pendingStore()) {
  const calls = [], module = { exports: {} }; let keyCounter = 0;
  const api = {
    newIdempotencyKey: () => "create-key-" + (++keyCounter), apiClient: { sessionRevision: () => 0 },
    getAccounts: async () => accounts,
    getBeneficiaries: async (email, inactive, accountId) => { calls.push(['list', accountId, inactive]); return [beneficiary(accountId)]; },
    initiateTransaction: async input => { calls.push(['send', input]); return { transactionId: 1, state: 'SETTLED' }; },
    addBeneficiary: async (accountId, email, input) => { calls.push(['add', accountId, email, input]); return beneficiary(accountId); },
    getBeneficiary: async id => beneficiary(id / 10),
    validateBeneficiary: () => ({}), transactionService: {}, ...overrides
  };
  const imports = { knockout: ko, '../appController': { default: { profile: ko.observable(null) } },
    '../services/api': api, '../accUtils': { announce() {} }, '../services/pendingPayment': store,
    '../services/customerSession': { endExpiredSession: () => false }, '../services/apiError': { ApiError: class extends Error {} },
    '../services/refreshLoop': { RefreshLoop: class { start() {} stop() {} invalidate() {} async refresh() {} } } };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/viewModels', screen + '.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', { document: { title: '', getElementById: () => ({ focus() {} }) }, Intl, TextEncoder })(p => imports[p], module, module.exports);
  const model = new module.exports();
  model.connected();
  return { model, calls, store };
}

for (const screen of ['send-money', 'beneficiaries']) {
  test(`${screen}: switching account clears previous beneficiaries and ignores a late response`, async () => {
    let resolveFirst;
    const { model } = fixture(screen, { getBeneficiaries: async (email, inactive, id) => id === 1
      ? new Promise(resolve => { resolveFirst = resolve; }) : [beneficiary(id)] });
    await tick();
    model.accountId('2');
    assert.equal(model.beneficiaries().length, 0);
    if (screen === 'send-money') assert.equal(model.beneficiaryId(), '');
    await tick();
    assert.equal(model.beneficiaries()[0].accountId, 2);
    resolveFirst([beneficiary(1)]); await tick();
    assert.equal(model.beneficiaries()[0].accountId, 2);
    assert.equal(model.loading(), false);
    model.disconnected();
  });

  test(`${screen}: an empty account selection never requests all accounts' beneficiaries`, async () => {
    const { model, calls } = fixture(screen); await tick();
    model.accountId(''); await tick();
    assert.equal(model.beneficiaries().length, 0); assert.equal(model.loading(), false);
    assert.deepEqual(calls.filter(call => call[0] === 'list').map(call => call[1]), [1]);
    model.disconnected();
  });

  test(`${screen}: responses cannot add beneficiaries belonging to another account`, async () => {
    const { model } = fixture(screen, { getBeneficiaries: async () => [beneficiary(1), beneficiary(2)] });
    await tick(); assert.equal(model.beneficiaries().length, 1); assert.equal(model.beneficiaries()[0].accountId, 1);
    model.disconnected();
  });
}

test('payment uses the selected account and its beneficiary, blocking stale IDs and blocked accounts', async () => {
  const { model, calls } = fixture('send-money'); await tick();
  model.accountId('2'); await tick(); model.amount('5000.00');
  model.reviewPayment(); await model.send();
  const sent = calls.find(call => call[0] === 'send')[1];
  assert.equal(sent.fromAccountId, 2); assert.equal(sent.beneficiaryId, 20); assert.equal(sent.amount, '5000.00');
  model.beneficiaryId('10'); model.amount('5000'); await model.send();
  model.accountId('3'); await tick(); model.amount('5000'); await model.send();
  assert.equal(calls.filter(call => call[0] === 'send').length, 1);
  assert.equal(model.canSend(), false); model.disconnected();
});

test('beneficiary creation uses selected account and refreshes only its list', async () => {
  const { model, calls } = fixture('beneficiaries'); await tick();
  model.accountId('2'); await tick();
  model.beneficiaryName('Person'); model.bankAccountNumber('1234567890'); model.ifsc('hdfc0001234');
  await model.save();
  const added = calls.find(call => call[0] === 'add');
  assert.equal(added[1], 2); assert.equal(added[2], ''); assert.equal(added[3].ifsc, 'HDFC0001234');
  assert.equal(model.beneficiaries()[0].accountId, 2);
  assert.equal(model.saving(), false); model.disconnected();
});

test('late beneficiary details cannot reopen a recipient after switching account', async () => {
  let resolveDetails;
  const { model } = fixture('beneficiaries', { getBeneficiary: () => new Promise(resolve => { resolveDetails = resolve; }) });
  await tick(); const pending = model.details(beneficiary(1));
  model.accountId('2'); await tick(); resolveDetails(beneficiary(1)); await pending;
  assert.equal(model.selected(), null); assert.equal(model.busyId(), null);
  model.disconnected();
});

test('payment review does not submit; uncertain retry freezes account, beneficiary and exact amount', async () => {
  const posts = []; let attempts = 0;
  const { model } = fixture('send-money', { initiateTransaction: async input => {
    posts.push({ ...input }); if (++attempts === 1) throw new Error('Timeout'); return { transactionId: 1, state: 'SETTLED' };
  } });
  await tick(); model.amount('10000.25'); model.purpose('Rent'); model.reviewPayment();
  assert.equal(posts.length, 0); await model.send(); assert.equal(model.attemptLocked(), true);
  model.amount('9999'); model.purpose('Changed after timeout'); model.editReview();
  assert.ok(model.reviewDraft()); await model.send();
  assert.deepEqual(posts[0], posts[1]); assert.equal(posts[1].fromAccountId, 1); assert.equal(posts[1].amount, '10000.25');
  assert.equal(model.attemptLocked(), false); assert.equal(model.reviewDraft(), null); model.disconnected();
});
test('inline cancellation reads current server state and respects canCancel=false', async () => {
  let cancellations = 0;
  const { model } = fixture('send-money', { transactionService: { get: async () => ({ transactionId: 1, state: 'SETTLED', canCancel: false }) },
    cancelTransaction: async () => { cancellations++; return {}; } });
  await tick(); model.result({ transactionId: 1, state: 'PROTECTED', canCancel: true }); await model.cancelResult();
  assert.equal(cancellations, 0); assert.equal(model.result().state, 'SETTLED'); model.disconnected();
});
test('protection countdown uses the server duration despite a timezone-free expiry and never settles locally', async () => {
  const { model } = fixture('send-money'); await tick();
  model.acceptResult({ transactionId: 1, state: 'PROTECTED', protectionRemainingMillis: 10000, protectionExpiresAt: '2026-09-16T14:00:00', canCancel: true });
  assert.equal(model.remainingSeconds(), 10); assert.equal(model.canCancelResult(), true);
  model.receiptTime -= 11000; model.clockTick(model.clockTick() + 1);
  assert.equal(model.remainingSeconds(), 0); assert.equal(model.result().state, 'PROTECTED');
  assert.match(model.countdown(), /server/); model.disconnected();
});
test('beneficiary form displays field validation before making a creation request', async () => {
  const { model, calls } = fixture('beneficiaries', { validateBeneficiary: () => ({ ifsc: 'Enter a valid IFSC' }) });
  await tick(); model.beneficiaryName('Person'); model.bankAccountNumber('00123'); model.ifsc('bad'); await model.save();
  assert.equal(model.fieldErrors().ifsc, 'Enter a valid IFSC'); assert.equal(calls.some(call => call[0] === 'add'), false);
  model.disconnected();
});
test('an in-flight inline cancellation blocks a second payment submission and refresh', async () => {
  let finishCancel, posts = 0;
  const { model } = fixture('send-money', { transactionService: { get: async () => ({ transactionId: 1, state: 'PROTECTED', canCancel: true }) },
    cancelTransaction: () => new Promise(resolve => { finishCancel = resolve; }),
    initiateTransaction: async () => { posts++; return {}; } });
  await tick(); model.result({ transactionId: 1, state: 'PROTECTED', canCancel: true }); const pending = model.cancelResult(); await tick();
  model.amount('5000'); model.reviewPayment(); await model.send(); assert.equal(model.reviewDraft(), null); assert.equal(posts, 0);
  finishCancel({ transactionId: 1, state: 'CANCELLED', canCancel: false }); await pending;
  assert.equal(model.cancelling(), false); model.disconnected();
});
test('late successful payment response after disconnect retains its key for replay; a later intentional payment uses a new key', async () => {
  const attempts = []; let finishFirst;
  const { model } = fixture('send-money', { initiateTransaction: async (input, key) => {
    attempts.push({ input: { ...input }, key });
    if (attempts.length === 1) return new Promise(resolve => { finishFirst = resolve; });
    return { transactionId: attempts.length === 2 ? 91 : 92, state: 'SETTLED' };
  } });
  await tick(); model.amount('5000.25'); model.purpose('Rent'); model.reviewPayment();
  const pending = model.send(); model.disconnected(); finishFirst({ transactionId: 91, state: 'SETTLED' }); await pending;
  assert.equal(model.attemptLocked(), true); assert.equal(model.result(), null);
  model.connected(); await tick(); await model.send();
  assert.equal(attempts.length, 2); assert.ok(attempts[0].key); assert.equal(attempts[0].key, attempts[1].key);
  assert.deepEqual(attempts[0].input, attempts[1].input); assert.equal(model.result().transactionId, 91);
  model.amount('5000.25'); model.purpose('Rent'); model.reviewPayment(); await model.send();
  assert.equal(attempts.length, 3); assert.notEqual(attempts[1].key, attempts[2].key);
  assert.deepEqual(attempts[1].input, attempts[2].input); model.disconnected();
});
test('frozen payment cannot be replayed across an authentication boundary', async () => {
  let session = 1, attempts = 0;
  const { model } = fixture('send-money', { apiClient: { sessionRevision: () => session }, initiateTransaction: async () => { attempts++; throw new Error('Unknown'); } });
  await tick(); model.amount('5000'); model.reviewPayment(); await model.send();
  session = 2; await model.send(); assert.equal(attempts, 1); assert.equal(model.reviewDraft(), null); assert.equal(model.attemptLocked(), false);
  assert.match(model.error(), /session changed/); model.disconnected();
});
test('late confirmed inline cancellation is reread after reconnect and is never submitted twice', async () => {
  let finishCancel, cancellations = 0, serverState = 'PROTECTED';
  const { model } = fixture('send-money', { transactionService: { get: async () => ({ transactionId: 1, state: serverState, canCancel: serverState === 'PROTECTED' }) },
    cancelTransaction: () => { cancellations++; return new Promise(resolve => { finishCancel = resolve; }); } });
  await tick(); model.result({ transactionId: 1, state: 'PROTECTED', canCancel: true }); const pending = model.cancelResult(); await tick();
  model.disconnected(); serverState = 'CANCELLED'; finishCancel({ transactionId: 1, state: serverState, canCancel: false }); await pending;
  model.connected(); await tick(); await model.cancelResult();
  assert.equal(cancellations, 1); assert.equal(model.result().state, 'CANCELLED'); model.disconnected();
});
test('new view restores an unacknowledged payment and replays its exact key before permitting a new intent', async () => {
  const store = pendingStore(), attempts = []; let finishFirst, sequence = 0;
  const transport = { newIdempotencyKey: () => 'shared-create-' + (++sequence), initiateTransaction: async (input, key) => {
    attempts.push({ input: { ...input }, key });
    if (attempts.length === 1) return new Promise(resolve => { finishFirst = resolve; });
    return { transactionId: attempts.length === 2 ? 71 : 72, state: 'SETTLED' };
  } };
  const first = fixture('send-money', transport, store).model;
  await tick(); first.accountId('2'); await tick(); first.amount('5000.25'); first.purpose('Rent'); first.reviewPayment();
  const original = first.send(); first.disconnected();
  const second = fixture('send-money', transport, store).model;
  assert.equal(second.attemptLocked(), true); assert.equal(second.accounts().length, 0);
  assert.equal(second.reviewDraft().fromAccountId, 2); assert.match(second.reviewAccountLabel(), /0002/);
  assert.match(second.reviewBeneficiaryLabel(), /Recipient 2/);
  finishFirst({ transactionId: 71, state: 'SETTLED' }); await original;
  assert.ok(store.readPendingPayment(0)); assert.equal(first.result(), null);
  await second.send(); await tick();
  assert.equal(attempts[0].key, attempts[1].key); assert.deepEqual(attempts[0].input, attempts[1].input);
  assert.equal(store.readPendingPayment(0), null); assert.equal(second.result().transactionId, 71);
  assert.equal(second.accounts().length, 3); assert.equal(second.loading(), false);
  second.accountId('2'); await tick(); second.amount('5000.25'); second.purpose('Rent'); second.reviewPayment(); await second.send();
  assert.notEqual(attempts[1].key, attempts[2].key); assert.deepEqual(attempts[1].input, attempts[2].input);
  second.disconnected();
});
test('mid-flight session change discards old payment success without clearing the new session pending intent', async () => {
  const store = pendingStore(); let session = 1, finish;
  const { model } = fixture('send-money', { apiClient: { sessionRevision: () => session },
    initiateTransaction: () => new Promise(resolve => { finish = resolve; }) }, store);
  await tick(); model.amount('5000'); model.reviewPayment(); const posted = model.send();
  session = 2; store.clearPendingPayment();
  store.rememberPendingPayment({ request: { fromAccountId: 2, beneficiaryId: 20, amount: '1.00' }, key: 'new-session-key', session,
    accountLabel: 'New account', beneficiaryLabel: 'New recipient' });
  finish({ transactionId: 91, state: 'SETTLED' }); await posted;
  assert.equal(model.result(), null); assert.equal(model.reviewDraft(), null); assert.equal(model.accounts().length, 0);
  assert.equal(model.beneficiaries().length, 0); assert.equal(model.sending(), false); assert.match(model.error(), /session changed/);
  assert.equal(store.readPendingPayment(2).key, 'new-session-key'); model.disconnected();
});
test('an unrelated reviewed draft cannot silently replace an earlier pending payment', async () => {
  const store = pendingStore(); let posts = 0;
  const { model } = fixture('send-money', { initiateTransaction: async () => { posts++; return { state: 'SETTLED' }; } }, store);
  await tick(); model.amount('5000'); model.reviewPayment();
  store.rememberPendingPayment({ request: { fromAccountId: 2, beneficiaryId: 20, amount: '9.00', purpose: 'Earlier' }, key: 'earlier-key', session: 0,
    accountLabel: 'Earlier account', beneficiaryLabel: 'Earlier recipient' });
  await model.send(); assert.equal(posts, 0); assert.equal(model.reviewDraft().amount, '9.00'); assert.equal(model.attemptLocked(), true);
  assert.match(model.error(), /earlier payment/); model.disconnected();
});
