const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
function load(file, imports, extra = {}) {
  const module = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/ts', file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 }
  }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', extra)(key => imports[key], module, module.exports);
  return module.exports;
}
const protection = load('utils/protection.ts', {});
const hold = {
  transactionId: 9004, transactionRef: 'DEMO-9004', customerName: 'Anika Sharma',
  customerEmail: 'anika.demo@safepay.test', amount: 150000, purpose: 'Deposit',
  beneficiaryName: 'Rohan Gupta', beneficiaryBankAccountNumber: '5012340001',
  riskTier: 'VERY_HIGH', riskReason: 'Amount above INR 1,00,000 (+6); Total score 6: VERY_HIGH',
  createdAt: '2026-09-16T10:00:00.000Z', verification: 'NONE', verificationSentAt: '', verifiedAt: '',
  attemptsLeft: 3, decision: 'PENDING', decidedAt: '', decidedBy: '', decisionNote: '', simulatedCode: ''
};
const verified = { ...hold, verification: 'VERIFIED', verifiedAt: '2026-09-16T10:05:00.000Z' };

function fixture(service = {}) {
  const calls = [], redirects = [];
  const holds = service.holds || [hold];
  const Model = load('viewModels/adminHolds.ts', {
    knockout: ko,
    '../services/apiError': { ApiError },
    '../services/types': {},
    '../utils/protection': protection,
    '../services/transactionService': { newIdempotencyKey: () => 'key-' + calls.length },
    '../services/adminHoldService': {
      adminHoldService: {
        list: async () => { calls.push(['list']); return service.list ? service.list() : holds; },
        sendOtp: async (id) => { calls.push(['sendOtp', id]); return service.sendOtp ? service.sendOtp() : { ...hold, verification: 'OTP_SENT', verificationSentAt: '2026-09-16T10:02:00.000Z' }; },
        release: async (id, key) => { calls.push(['release', id, key]); return service.release ? service.release() : { ...verified, decision: 'RELEASED', decidedBy: 'Demo Admin', decidedAt: '2026-09-16T10:06:00.000Z', decisionNote: 'Released by an administrator after verification.' }; },
        reject: async (id, note, key) => { calls.push(['reject', id, note, key]); return service.reject ? service.reject() : { ...hold, decision: 'REJECTED', decidedBy: 'Demo Admin', decidedAt: '2026-09-16T10:07:00.000Z', decisionNote: note }; }
      }
    }
  }, { window: { location: { replace: url => redirects.push(url) } }, Intl, Date }).AdminHoldsModel;
  return { model: new Model(), calls, redirects };
}

test('the queue summarises what is held and what it is waiting on', async () => {
  const f = fixture({ holds: [hold, verified, { ...hold, transactionId: 9005, decision: 'REJECTED', decidedBy: 'Demo Admin' }] });
  await f.model.load();
  assert.equal(f.model.pending().length, 2);
  assert.equal(f.model.decided().length, 1);
  assert.equal(f.model.heldAmount(), 300000);
  assert.equal(f.model.awaitingCustomer(), 1);
  assert.equal(f.model.readyToRelease(), 1);
  assert.equal(f.model.money(150000), '₹1,50,000.00');
  assert.equal(f.model.masked('5012340001'), '•••• 0001');
  assert.equal(f.model.verificationLabel('OTP_SENT'), 'Code sent, awaiting the customer');
});

test('release is refused until the customer has verified', async () => {
  const f = fixture();
  await f.model.load();
  assert.equal(f.model.canRelease(hold), false);
  await f.model.release(hold);
  assert.match(f.model.error(), /after the customer passes verification/);
  assert.deepEqual(f.calls, [['list']]);
});

test('a verified hold can be released once, with an idempotency key', async () => {
  const f = fixture({ holds: [verified] });
  await f.model.load();
  f.model.select(verified);
  assert.equal(f.model.canRelease(verified), true);
  await f.model.release(verified);
  assert.equal(f.calls[1][0], 'release');
  assert.equal(f.calls[1][1], 9004);
  assert.ok(f.calls[1][2]);
  assert.equal(f.model.holds()[0].decision, 'RELEASED');
  assert.equal(f.model.selected().decision, 'RELEASED');
  assert.match(f.model.notice(), /released/i);
});

test('sending a code updates the row without deciding anything', async () => {
  const f = fixture();
  await f.model.load();
  await f.model.sendCode(hold);
  assert.deepEqual(f.calls[1], ['sendOtp', 9004]);
  assert.equal(f.model.holds()[0].verification, 'OTP_SENT');
  assert.equal(f.model.holds()[0].decision, 'PENDING');
  assert.match(f.model.notice(), /code has been sent/);
});

test('a rejection carries its reason and clears the note', async () => {
  const f = fixture();
  await f.model.load();
  f.model.select(hold);
  f.model.note('Customer could not verify');
  await f.model.reject(hold);
  assert.equal(f.calls[1][0], 'reject');
  assert.equal(f.calls[1][2], 'Customer could not verify');
  assert.equal(f.model.holds()[0].decision, 'REJECTED');
  assert.equal(f.model.note(), '');
  assert.match(f.model.notice(), /stays in the customer/);
});

test('an over-long reason is refused locally', async () => {
  const f = fixture();
  await f.model.load();
  f.model.note('x'.repeat(256));
  await f.model.reject(hold);
  assert.match(f.model.error(), /255 characters/);
  assert.deepEqual(f.calls, [['list']]);
});

test('a conflict from the server is shown as-is and nothing is guessed', async () => {
  const f = fixture({ release: () => { throw new ApiError(409, 'This payment has already been decided.'); } });
  await f.model.load();
  await f.model.release(verified);
  assert.equal(f.model.error(), 'This payment has already been decided.');
  assert.equal(f.model.notice(), '');
});

test('a second action is ignored while one is in flight', async () => {
  let finish;
  const f = fixture({ sendOtp: () => new Promise(resolve => { finish = resolve; }) });
  await f.model.load();
  const first = f.model.sendCode(hold);
  await f.model.sendCode(hold);
  assert.equal(f.calls.filter(call => call[0] === 'sendOtp').length, 1);
  finish({ ...hold, verification: 'OTP_SENT' });
  await first;
});

for (const status of [401, 403]) test('access failure ' + status + ' hides the queue', async () => {
  const f = fixture({ list: () => { throw new ApiError(status, 'Denied'); } });
  await f.model.load();
  assert.equal(f.model.holds().length, 0);
  if (status === 401) assert.deepEqual(f.redirects, ['/admin/login']);
  else assert.equal(f.model.forbidden(), true);
});

test('the queue explains the risk reasons behind a hold', async () => {
  const f = fixture();
  await f.model.load();
  f.model.select(hold);
  assert.equal(f.model.selectedReasons().length, 1);
  assert.equal(f.model.selectedReasons()[0], 'Amount above INR 1,00,000 (+6)');
});

test('the admin page exposes the queue and its actions', () => {
  const html = fs.readFileSync(path.join(__dirname, '../src/ts/views/admin.html'), 'utf8');
  assert.match(html, /holdsPage/);
  assert.match(html, /href="\/admin\/holds"/);
  assert.match(html, /click: \$parent\.sendCode/);
  assert.match(html, /click: \$parent\.release/);
  assert.match(html, /reject\(selected\(\)\)/);
  const hook = fs.readFileSync(path.join(__dirname, '../scripts/hooks/before_serve.js'), 'utf8');
  assert.match(hook, /'\/admin\/holds'/);
});
