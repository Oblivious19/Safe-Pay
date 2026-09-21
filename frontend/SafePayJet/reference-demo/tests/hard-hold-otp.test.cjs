const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const storage = () => {
  const store = new Map();
  return {
    getItem: key => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => store.set(key, String(value)),
    removeItem: key => store.delete(key)
  };
};

// Each fixture gets its own context and its own empty storages.
function fixture() {
  const calls = [], modules = new Map();
  const bank = storage();
  const context = vm.createContext({
    Headers, Response, URL, TextEncoder, crypto: { randomUUID: () => 'test-idempotency-key' },
    sessionStorage: storage(), localStorage: bank,
    fetch: async (url, options) => { calls.push({ url, ...options }); throw new Error('Unexpected network request'); }
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
  return { load, calls, bank };
}

/** Reaches into the simulated bank storage to age a code without moving any clock. */
function expireCode(bank, id) {
  const holds = JSON.parse(bank.getItem('safepay.demo.holds'));
  holds.find(hold => hold.transactionId === id).codeExpiresAt = new Date(Date.now() - 1000).toISOString();
  bank.setItem('safepay.demo.holds', JSON.stringify(holds));
}

// A payment above INR 1,00,000 scores VERY_HIGH, which is the only path into HARD_HOLD.
function heldPayment(demo, amount = '150000') {
  demo.beginDemo('anika');
  return demo.demoCreatePayment({ fromAccountId: 2001, beneficiaryId: 501, amount }, 'key-' + amount);
}

function thrown(run) {
  try { run(); return null; } catch (error) { return error; }
}

test('a very high risk payment is held, queued for review and unverified', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  assert.equal(row.state, 'HARD_HOLD');
  assert.equal(row.verification, 'NONE');
  assert.equal(row.settledAt, '');

  const queue = demo.demoHeldPayments();
  assert.ok(queue.length >= 1);
  assert.equal(queue.find(item => item.transactionId === row.transactionId).decision, 'PENDING');
  assert.equal(queue.find(item => item.transactionId === row.transactionId).customerName, 'Anika Sharma');
  assert.match(queue.find(item => item.transactionId === row.transactionId).riskReason, /VERY_HIGH$/);
});

test('a held amount is reserved, so it cannot be spent twice', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const account = demo.demoAccount();
  const reserved = demo.demoPayments().filter(item => item.state === 'PROTECTED' || item.state === 'HARD_HOLD')
    .reduce((total, item) => total + item.amount, 0);
  assert.equal(demo.demoAvailableBalance(), Math.round((account.balance - reserved) * 100) / 100);
  assert.equal(account.balance, 395500);
});

test('a code must be requested before it can be entered', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const error = thrown(() => demo.demoVerifyHoldOtp(row.transactionId, '123456'));
  assert.equal(error.status, 409);
  assert.match(error.message, /Ask for a verification code/);
  assert.equal(demo.demoPayment(row.transactionId).state, 'HARD_HOLD');
});

test('requesting a code marks the hold as sent and masks the destination', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const challenge = demo.demoSendHoldOtp(row.transactionId);
  assert.match(challenge.simulatedCode, /^\d{6}$/);
  assert.equal(challenge.sentTo, 'an••••••••@safepay.test');
  assert.equal(challenge.attemptsLeft, 3);
  assert.ok(Date.parse(challenge.expiresAt) > Date.now());
  assert.equal(demo.demoPayment(row.transactionId).verification, 'OTP_SENT');
  assert.equal(demo.demoHeldPayments().find(item => item.transactionId === row.transactionId).verification, 'OTP_SENT');
});

test('a correct code settles the hold once and moves the money', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const before = demo.demoAccount().balance;
  const code = demo.demoSendHoldOtp(row.transactionId).simulatedCode;

  const settled = demo.demoVerifyHoldOtp(row.transactionId, code);
  assert.equal(settled.state, 'SETTLED');
  assert.equal(settled.verification, 'VERIFIED');
  assert.ok(settled.verifiedAt);
  assert.equal(demo.demoAccount().balance, Math.round((before - row.amount) * 100) / 100);

  const hold = demo.demoHeldPayments().find(item => item.transactionId === row.transactionId);
  assert.equal(hold.decision, 'RELEASED');
  assert.equal(hold.decidedBy, 'Customer verification');
  // Replaying the same code must not debit the account a second time.
  const error = thrown(() => demo.demoVerifyHoldOtp(row.transactionId, code));
  assert.equal(error.status, 409);
  assert.equal(demo.demoAccount().balance, Math.round((before - row.amount) * 100) / 100);
});

test('wrong codes count down, then lock the attempt and keep the money held', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const before = demo.demoAccount().balance;
  const code = demo.demoSendHoldOtp(row.transactionId).simulatedCode;
  const wrong = String((Number(code) + 1) % 1000000).padStart(6, '0');

  assert.match(thrown(() => demo.demoVerifyHoldOtp(row.transactionId, wrong)).message, /2 attempts left/);
  assert.equal(demo.demoHoldAttemptsLeft(row.transactionId), 2);
  assert.match(thrown(() => demo.demoVerifyHoldOtp(row.transactionId, wrong)).message, /1 attempt left/);
  assert.match(thrown(() => demo.demoVerifyHoldOtp(row.transactionId, wrong)).message, /Too many incorrect codes/);

  assert.equal(demo.demoPayment(row.transactionId).state, 'HARD_HOLD');
  assert.equal(demo.demoPayment(row.transactionId).verification, 'FAILED');
  assert.equal(demo.demoAccount().balance, before);
  // A fresh code reopens verification with a full set of attempts.
  const next = demo.demoSendHoldOtp(row.transactionId);
  assert.equal(next.attemptsLeft, 3);
  assert.equal(demo.demoVerifyHoldOtp(row.transactionId, next.simulatedCode).state, 'SETTLED');
});

test('an expired code is refused and needs a resend', () => {
  const f = fixture();
  const demo = f.load('demoSession');
  const row = heldPayment(demo);
  const code = demo.demoSendHoldOtp(row.transactionId).simulatedCode;
  expireCode(f.bank, row.transactionId);

  const error = thrown(() => demo.demoVerifyHoldOtp(row.transactionId, code));
  assert.equal(error.status, 410);
  assert.match(error.message, /expired/);
  assert.equal(demo.demoPayment(row.transactionId).state, 'HARD_HOLD');
  assert.equal(demo.demoPayment(row.transactionId).verification, 'FAILED');
  assert.equal(demo.demoVerifyHoldOtp(row.transactionId, demo.demoSendHoldOtp(row.transactionId).simulatedCode).state, 'SETTLED');
});

test('an admin cannot release a hold the customer has not verified', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const error = thrown(() => demo.demoAdminRelease(row.transactionId, 'Demo Admin'));
  assert.equal(error.status, 409);
  assert.match(error.message, /after the customer passes verification/);
  assert.equal(demo.demoPayment(row.transactionId).state, 'HARD_HOLD');
});

test('an admin resend lets the customer verify and settle', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const sent = demo.demoAdminSendOtp(row.transactionId, 'Demo Admin');
  assert.equal(sent.verification, 'OTP_SENT');
  assert.match(sent.decisionNote, /Code sent by Demo Admin/);
  assert.equal(demo.demoVerifyHoldOtp(row.transactionId, sent.simulatedCode).state, 'SETTLED');
});

test('rejecting a hold refunds nothing and cannot be decided twice', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const before = demo.demoAccount().balance;
  const rejected = demo.demoAdminReject(row.transactionId, 'Demo Admin', ' Customer could not verify ');
  assert.equal(rejected.decision, 'REJECTED');
  assert.equal(rejected.decisionNote, 'Customer could not verify');

  const payment = demo.demoPayment(row.transactionId);
  assert.equal(payment.state, 'REJECTED');
  assert.equal(demo.demoAccount().balance, before);
  const reserved = demo.demoPayments().filter(item => item.state === 'PROTECTED' || item.state === 'HARD_HOLD')
    .reduce((total, item) => total + item.amount, 0);
  assert.equal(demo.demoAvailableBalance(), Math.round((before - reserved) * 100) / 100);
  assert.equal(thrown(() => demo.demoAdminReject(row.transactionId, 'Demo Admin', '')).status, 409);
  assert.equal(thrown(() => demo.demoSendHoldOtp(row.transactionId)).status, 409);
});

test('an admin decision reaches the customer after a sign out and back in', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  const before = demo.demoAccount().balance;
  const code = demo.demoSendHoldOtp(row.transactionId).simulatedCode;
  demo.endDemo();

  demo.beginDemo('admin');
  const queue = demo.demoHeldPayments();
  assert.ok(queue.some(item => item.transactionId === row.transactionId));
  assert.equal(queue.find(item => item.transactionId === row.transactionId).customerEmail, 'anika.demo@safepay.test');
  demo.endDemo();

  demo.beginDemo('anika');
  assert.equal(demo.demoPayment(row.transactionId).state, 'HARD_HOLD');
  assert.equal(demo.demoVerifyHoldOtp(row.transactionId, code).state, 'SETTLED');
  assert.equal(demo.demoAccount().balance, Math.round((before - row.amount) * 100) / 100);
});

test('the service layer keeps demo verification local and validates the code shape', async () => {
  const f = fixture();
  const demo = f.load('demoSession');
  const { transactionService } = f.load('transactionService');
  const row = heldPayment(demo);

  await assert.rejects(transactionService.verifyHold(row.transactionId, '12', 'key'), /6-digit/);
  const challenge = await transactionService.requestVerification(row.transactionId);
  const settled = await transactionService.verifyHold(challenge.transactionId, challenge.simulatedCode, 'key');
  assert.equal(settled.state, 'SETTLED');
  assert.equal(f.calls.length, 0);
});

test('an admin credit funds a simulated account well enough to trigger a hold', async () => {
  const f = fixture();
  const demo = f.load('demoSession');
  const { adminUserService } = f.load('adminUserService');
  demo.beginDemo('admin');

  const users = await adminUserService.users();
  assert.equal(users.length, 3);
  const anika = users.find(user => user.email === 'anika.demo@safepay.test');
  const account = await adminUserService.account(anika.userId);
  assert.equal(account.accountId, 2001);
  assert.equal(account.balance, '395500.00');

  const receipt = await adminUserService.credit(account.accountId, '500000.00');
  assert.equal(receipt.balanceBefore, '395500.00');
  assert.equal(receipt.balanceAfter, '895500.00');
  await assert.rejects(adminUserService.credit(account.accountId, '-5'), /positive amount/);
  demo.endDemo();

  // The customer sees the credited balance and can now hold a larger payment.
  demo.beginDemo('anika');
  assert.equal(demo.demoAccount().balance, 895500);
  const row = demo.demoCreatePayment({ fromAccountId: 2001, beneficiaryId: 501, amount: '600000' }, 'big');
  assert.equal(row.state, 'HARD_HOLD');
  assert.equal(f.calls.length, 0);
});

test('the admin hold service reads and decides on the demo queue', async () => {
  const f = fixture();
  const demo = f.load('demoSession');
  const { adminHoldService } = f.load('adminHoldService');
  const row = heldPayment(demo);
  demo.endDemo();
  demo.beginDemo('admin');

  assert.ok((await adminHoldService.list()).some(item => item.transactionId === row.transactionId));
  await assert.rejects(adminHoldService.release(row.transactionId, 'key'), /after the customer passes verification/);
  const sent = await adminHoldService.sendOtp(row.transactionId);
  assert.equal(sent.verification, 'OTP_SENT');
  const rejected = await adminHoldService.reject(row.transactionId, 'Suspected mule account', 'key');
  assert.equal(rejected.decision, 'REJECTED');
  assert.equal(rejected.decidedBy, 'Demo Admin');
  assert.equal(f.calls.length, 0);
});

test('admin reports count customer payments after an admin signs in', () => {
  const demo = fixture().load('demoSession');
  const row = heldPayment(demo);
  demo.endDemo();
  demo.beginDemo('admin');
  const summary = demo.demoTransactionSummary();
  assert.ok(summary.totalTransactions >= 1);
  assert.ok(summary.hardHolds >= 1);
  assert.ok(summary.highRiskTransactions >= 1);
  assert.ok(summary.totalAmount >= row.amount);
  const daily = demo.demoDailyReports('2020-01-01', '2030-01-01');
  assert.ok(daily.length >= 1);
  assert.ok(demo.demoHeldPayments().some(item => item.transactionId === row.transactionId));
});
