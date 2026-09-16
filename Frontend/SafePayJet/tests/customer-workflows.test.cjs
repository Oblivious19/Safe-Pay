const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

let fixtureId = 0;
function fixture(replies = [], storage = new Map()) {
  const calls = [], modules = new Map(), timers = new Map(), redirects = [];
  let key = 0, timerId = 0;
  const instance = ++fixtureId;
  const context = vm.createContext({
    Headers, Response, URL, TextEncoder,
    window: { location: { replace: url => redirects.push(url) } },
    crypto: { randomUUID: () => 'fixture-' + instance + '-operation-' + (++key) },
    sessionStorage: {
      getItem: key => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    },
    setTimeout: (callback, delay) => { const id = ++timerId; timers.set(id, { callback, delay }); return id; },
    clearTimeout: id => timers.delete(id),
    fetch: async (url, options) => {
      calls.push({ url, ...options });
      const reply = replies.shift();
      if (!reply) throw new Error('Unexpected request');
      if (reply instanceof Error) throw reply;
      return typeof reply === 'function' ? reply() : reply;
    }
  });
  function load(name) {
    const file = path.resolve(__dirname, '../src/ts/services', name + '.ts');
    if (modules.has(file)) return modules.get(file).exports;
    const module = { exports: {} }; modules.set(file, module);
    const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }
    }).outputText;
    vm.runInContext('(function(require,module,exports){' + code + '\n})', context)(
      p => load(p.replace(/^\.\//, '')), module, module.exports);
    return module.exports;
  }
  return { load, calls, timers, storage, redirects };
}
const json = (body, status = 200, token) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json', ...(token ? { 'X-CSRF-TOKEN': token } : {}) }
});
const tick = () => new Promise(resolve => setImmediate(resolve));

test('beneficiary list preserves active-only default and explicitly requests inactive entries', async () => {
  const f = fixture([json([]), json([]), json({ beneficiaryId: 8, status: 'INACTIVE' })]);
  const api = f.load('api');
  await api.getBeneficiaries('ignored@example.test');
  await api.getBeneficiaries(undefined, true);
  await api.getBeneficiary(8);
  assert.equal(f.calls[0].url, 'http://localhost:8080/api/beneficiaries');
  assert.equal(f.calls[1].url, 'http://localhost:8080/api/beneficiaries?includeInactive=true');
  assert.equal(f.calls[2].url, 'http://localhost:8080/api/beneficiaries/8');
  assert.equal(f.calls.some(c => c.url.includes('userEmail')), false);
});

test('account listing and beneficiary creation retain the selected resource without trusting caller identity', async () => {
  const f = fixture([json([{ accountId: 9 }], 200, 'csrf'), json([]), json({ accountId: 9 }), json({ accountId: 9 })]);
  const api = f.load('api');
  await api.getAccounts('ignored@example.test');
  await api.getBeneficiaries(undefined, true, 9);
  await api.addBeneficiary(9, 'ignored@example.test', { beneficiaryName: 'Recipient', bankAccountNumber: '1234567890', ifsc: 'HDFC0001234', userId: 200 });
  await api.accountService.getCurrent(9);
  assert.equal(f.calls[0].url, 'http://localhost:8080/api/accounts');
  assert.equal(f.calls[1].url, 'http://localhost:8080/api/beneficiaries?accountId=9&includeInactive=true');
  assert.deepEqual(JSON.parse(f.calls[2].body), { accountId: 9, beneficiaryName: 'Recipient', bankAccountNumber: '1234567890', ifsc: 'HDFC0001234' });
  assert.equal(f.calls[2].headers.get('X-CSRF-TOKEN'), 'csrf');
  assert.equal(f.calls[3].url, 'http://localhost:8080/api/accounts/current?accountId=9');
  assert.throws(() => api.getBeneficiaries(undefined, false, -1), error => error.status === 400);
  assert.throws(() => api.addBeneficiary(-1, '', {}), error => error.status === 400);
});

test('beneficiary remove and reactivate use DELETE/PATCH with CSRF and only allowed status', async () => {
  const f = fixture([json({}, 200, 'csrf'), new Response(null, { status: 204 }), json({ status: 'ACTIVE' })]);
  const api = f.load('api');
  await api.deleteBeneficiary(7);
  await api.updateBeneficiaryStatus(7, 'ACTIVE');
  assert.equal(f.calls[1].method, 'DELETE');
  assert.equal(f.calls[1].url.endsWith('/api/beneficiaries/7'), true);
  assert.equal(f.calls[1].body, undefined);
  assert.equal(f.calls[1].headers.get('X-CSRF-TOKEN'), 'csrf');
  assert.equal(f.calls[2].method, 'PATCH');
  assert.equal(f.calls[2].url.endsWith('/api/beneficiaries/7/status'), true);
  assert.deepEqual(JSON.parse(f.calls[2].body), { status: 'ACTIVE' });
  await assert.rejects(api.updateBeneficiaryStatus(7, 'ADMIN'), e => e.status === 400);
  await assert.rejects(api.getBeneficiary(-1), e => e.status === 400);
  assert.equal(f.calls.length, 3);
});

test('profile update uses current session and whitelists editable fields', async () => {
  const f = fixture([json({}, 200, 'csrf'), json({ name: 'Updated' }), json({ name: 'Updated again' })]);
  const users = f.load('api').users;
  await users.updateCurrent({ name: 'Updated', email: 'demo@example.test', phone: '9999999999',
    password: 'new-password', userId: 99, role: 'ADMIN', status: 'ACTIVE', passwordHash: 'ignored' });
  await users.updateCurrent({ name: 'Updated again', email: 'demo@example.test', phone: '9999999999', password: '' });
  assert.equal(f.calls[1].url, 'http://localhost:8080/api/users/current');
  assert.equal(f.calls[1].method, 'PUT');
  assert.equal(f.calls[1].headers.get('X-CSRF-TOKEN'), 'csrf');
  assert.deepEqual(Object.keys(JSON.parse(f.calls[1].body)).sort(), ['email', 'name', 'password', 'phone']);
  assert.equal(Object.hasOwn(JSON.parse(f.calls[2].body), 'password'), false);
});

test('cancellation keeps its key after an uncertain response, then clears it on success', async () => {
  const f = fixture([json({}, 200, 'csrf'), new Error('connection lost'), json({ state: 'CANCELLED' }), json({ state: 'CANCELLED' })]);
  const api = f.load('api');
  await assert.rejects(api.cancelTransaction(2));
  await api.cancelTransaction(2);
  await api.cancelTransaction(2);
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), f.calls[2].headers.get('Idempotency-Key'));
  assert.notEqual(f.calls[2].headers.get('Idempotency-Key'), f.calls[3].headers.get('Idempotency-Key'));
});

test('verification submits only password and retains a key across uncertain retries', async () => {
  const f = fixture([json({}, 200, 'csrf'), new Error('timeout'), json({ state: 'SETTLED' })]);
  const api = f.load('api');
  await assert.rejects(api.verifyTransaction(3, 'fixture-password'));
  const result = await api.verifyTransaction(3, 'fixture-password');
  assert.equal(result.state, 'SETTLED');
  assert.equal(f.calls[1].url, 'http://localhost:8080/api/transactions/3/verify');
  assert.equal(f.calls[1].method, 'POST');
  assert.deepEqual(JSON.parse(f.calls[1].body), { password: 'fixture-password' });
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), f.calls[2].headers.get('Idempotency-Key'));
  await assert.rejects(api.verifyTransaction(3, ''), e => e.status === 400);
});

test('wrong verification password preserves message and explicit retry key after CSRF refresh', async () => {
  const f = fixture([json({}, 200, 'csrf'), json({ message: 'Incorrect password; payment remains on hold' }, 403),
    json({}, 200, 'new-csrf'), json({ state: 'SETTLED' })]);
  const api = f.load('api');
  await assert.rejects(api.verifyTransaction(3, 'wrong'), /Incorrect password; payment remains on hold/);
  await api.verifyTransaction(3, 'correct');
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), f.calls[3].headers.get('Idempotency-Key'));
  assert.equal(f.calls[3].headers.get('X-CSRF-TOKEN'), 'new-csrf');
});

test('retained operation keys never cross session boundaries', async () => {
  const f = fixture([json({}, 200, 'csrf'), new Error('timeout'), json({}, 200, 'next-csrf'), json({ state: 'CANCELLED' })]);
  const api = f.load('api');
  await assert.rejects(api.cancelTransaction(2));
  f.load('apiClient').apiClient.clearSession();
  await api.cancelTransaction(2);
  assert.notEqual(f.calls[1].headers.get('Idempotency-Key'), f.calls[3].headers.get('Idempotency-Key'));
});

test('verification retry survives a tab reload without storing its password or payment details', async () => {
  const f = fixture([json({}, 200, 'csrf'), new Error('timeout')]);
  await assert.rejects(f.load('api').verifyTransaction(3, 'private-fixture-password'));
  const retained = JSON.stringify([...f.storage.values()]);
  assert.equal(retained.includes('private-fixture-password'), false);
  assert.equal(retained.includes('amount'), false);
  const reloaded = fixture([json({}, 200, 'csrf'), json({ state: 'SETTLED' })], f.storage);
  await reloaded.load('api').verifyTransaction(3, 'private-fixture-password');
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), reloaded.calls[1].headers.get('Idempotency-Key'));
  assert.equal(JSON.stringify([...f.storage.values()]).includes('verify:3'), false);
});

test('expired sessions clear private view state; rejected verification and network errors do not log out', () => {
  const f = fixture();
  const { ApiError } = f.load('apiError');
  const { endExpiredSession } = f.load('customerSession');
  let cleared = 0;
  assert.equal(endExpiredSession(new ApiError(403, 'Incorrect password'), () => cleared++), false);
  assert.equal(endExpiredSession(new ApiError(0, 'Network unavailable'), () => cleared++), false);
  assert.equal(cleared, 0); assert.equal(f.redirects.length, 0);
  assert.equal(endExpiredSession(new ApiError(401, 'Login required'), () => cleared++), true);
  assert.equal(cleared, 1); assert.deepEqual(f.redirects, ['/login?reason=session-expired']);
});

test('polling waits five seconds between completed reads and stops on terminal server state', async () => {
  const f = fixture(), { RefreshLoop } = f.load('refreshLoop');
  const received = [];
  let state = 'PROTECTED';
  const loop = new RefreshLoop(async () => state, result => received.push(result),
    error => { throw error; }, () => received.at(-1) === 'PROTECTED');
  loop.start(); await tick();
  assert.deepEqual(received, ['PROTECTED']);
  assert.equal(f.timers.size, 1);
  const [id, timer] = [...f.timers][0];
  assert.equal(timer.delay, 5000);
  state = 'SETTLED'; f.timers.delete(id); timer.callback(); await tick();
  assert.deepEqual(received, ['PROTECTED', 'SETTLED']);
  assert.equal(f.timers.size, 0);
  loop.stop();
});

test('new reads and mutation invalidation suppress stale transaction state', async () => {
  const f = fixture(), { RefreshLoop } = f.load('refreshLoop');
  const pending = [], received = [];
  const loop = new RefreshLoop(() => new Promise(resolve => pending.push(resolve)),
    result => received.push(result), error => { throw error; }, () => false);
  loop.start();
  const newer = loop.refresh();
  pending[1]('SETTLED'); await newer;
  pending[0]('PROTECTED'); await tick();
  assert.deepEqual(received, ['SETTLED']);
  void loop.refresh(); loop.invalidate(); pending[2]('PROTECTED'); await tick();
  assert.deepEqual(received, ['SETTLED']);
  loop.stop();
});

test('leaving and reopening a page ignores old reads and clears polling timers', async () => {
  const f = fixture(), { RefreshLoop } = f.load('refreshLoop');
  const pending = [], received = [], errors = [];
  const loop = new RefreshLoop(() => new Promise((resolve, reject) => pending.push({ resolve, reject })),
    result => received.push(result), error => errors.push(error), () => true);
  loop.start(); loop.stop(); loop.start();
  pending[0].reject(new Error('old page')); pending[1].resolve('HARD_HOLD'); await tick();
  assert.deepEqual(received, ['HARD_HOLD']); assert.equal(errors.length, 0);
  assert.equal(f.timers.size, 1); loop.stop(); assert.equal(f.timers.size, 0);
});

test('beneficiary field validation matches backend formats without losing leading zeros', () => {
  const f = fixture(); const validate = f.load('beneficiaryValidation').validateBeneficiary;
  assert.equal(Object.keys(validate({ beneficiaryName: 'Recipient', bankAccountNumber: '000123', ifsc: 'hdfc0001234' })).length, 0);
  const errors = validate({ beneficiaryName: '', bankAccountNumber: '12AB', ifsc: 'invalid' });
  assert.deepEqual(Object.keys(errors).sort(), ['bankAccountNumber', 'beneficiaryName', 'ifsc']);
});
test('admin credit retry draft survives module reload but clears at an authentication boundary', async () => {
  const storage = new Map(); let f = fixture([], storage);
  f.load('adminCreditDraft').saveCreditDraft({ accountId: 9, amount: '9999999999999999.99', key: 'fixed-key' });
  f = fixture([], storage);
  assert.equal(f.load('adminCreditDraft').readCreditDraft().amount, '9999999999999999.99');
  f.load('apiClient').apiClient.clearSession(); assert.equal(f.load('adminCreditDraft').readCreditDraft(), null);
  assert.equal(storage.has('safepay.adminCreditDraft.v1'), false);
});
test('phone login whitelists one identifier and uses the existing session transport', async () => {
  const f = fixture([json({ role: 'CUSTOMER' })]);
  await f.load('authService').authService.login({ phone: '9876543210', password: ' exact ', email: 'ignored', pin: 'ignored' });
  assert.deepEqual(JSON.parse(f.calls[0].body), { phone: '9876543210', password: ' exact ' });
  assert.equal(f.calls[0].credentials, 'include');
});
test('explicit reviewed-payment retry key remains usable after an already successful response', async () => {
  const f = fixture([json({}, 200, 'csrf'), json({ transactionId: 91 }), json({ transactionId: 91 }), json({ transactionId: 92 })]);
  const api = f.load('api'); const input = { fromAccountId: 1, beneficiaryId: 2, amount: '5000.25', purpose: 'Rent' };
  await api.initiateTransaction(input, 'frozen-create-key');
  await api.initiateTransaction(input, 'frozen-create-key');
  await api.initiateTransaction(input, 'next-intentional-key');
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), 'frozen-create-key');
  assert.equal(f.calls[2].headers.get('Idempotency-Key'), 'frozen-create-key');
  assert.equal(f.calls[3].headers.get('Idempotency-Key'), 'next-intentional-key');
  assert.equal(f.calls[1].body, f.calls[2].body);
});test('pending reviewed payment uses isolated copies and clears on an authentication boundary', () => {
  const f = fixture(), client = f.load('apiClient').apiClient, store = f.load('pendingPayment');
  const session = client.sessionRevision();
  const input = { request: { fromAccountId: 1, beneficiaryId: 2, amount: '5000.25' }, key: 'pending-key', session,
    accountLabel: 'Savings 0001', beneficiaryLabel: 'Recipient 1234' };
  store.rememberPendingPayment(input); input.request.amount = '9999';
  const restored = store.readPendingPayment(session); assert.equal(restored.request.amount, '5000.25');
  restored.request.amount = '7777'; assert.equal(store.readPendingPayment(session).request.amount, '5000.25');
  assert.equal(store.readPendingPayment(session + 1), null);
  store.clearPendingPayment('another-key', session); assert.ok(store.readPendingPayment(session));
  assert.equal(f.storage.size, 0); client.clearSession(); assert.equal(store.readPendingPayment(session), null);
});
