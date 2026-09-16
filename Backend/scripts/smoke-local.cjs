// Run only against the disposable local/H2 simulator after starting the backend.
// Requires Node 18+ and SAFEPAY_DEMO_ADMIN_PASSWORD matching the local server.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const base = process.env.SAFEPAY_SMOKE_URL || 'http://localhost:18081';
if (!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base)) throw new Error('Use a local simulator URL');
if (!process.env.SAFEPAY_DEMO_ADMIN_PASSWORD) throw new Error('Set the local demo administrator password');
let checks = 0;
function check(label, actual, expected) { assert.deepEqual(actual, expected, label); checks++; }
function client() {
  let cookie = '', csrf = '';
  return async function request(method, path, body, expected = 200, key, withCsrf = true) {
    const headers = { Cookie: cookie };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (csrf && withCsrf) headers['X-CSRF-TOKEN'] = csrf;
    if (key) headers['Idempotency-Key'] = key;
    const response = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const session = response.headers.get('set-cookie');
    if (session) cookie = session.split(';')[0];
    csrf = response.headers.get('X-CSRF-TOKEN') || csrf;
    const text = await response.text();
    check(method + ' ' + path + ': ' + text, response.status, expected);
    return text ? JSON.parse(text) : null;
  };
}
async function main() {
  let ready = false;
  for (let attempt = 0; attempt < 100; attempt++) {
    try { if ((await fetch(base + '/api/accounts/current')).status === 401) { ready = true; break; } } catch {}
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  assert.ok(ready, 'Backend became ready within 30 seconds');
  const customer = client(), admin = client(), anonymous = client();
  const suffix = randomUUID().slice(0, 8);
  const email = 'smoke-' + suffix + '@example.test', password = randomUUID() + '#A1';
  const phone = String(Math.floor(1000000000 + Math.random() * 8999999999));
  await customer('POST', '/api/auth/register', { name: 'Smoke Customer', email, phone, password }, 201);
  await customer('POST', '/api/auth/login', { email, password });
  const account = await customer('GET', '/api/accounts/current');
  check('Registration minimum', Number(account.balance), 5000);
  await anonymous('GET', '/api/admin/accounts', undefined, 401);
  await customer('GET', '/api/admin/accounts', undefined, 403);
  await admin('POST', '/api/auth/login', {
    email: process.env.SAFEPAY_DEMO_ADMIN_EMAIL || 'admin@safepay.local',
    password: process.env.SAFEPAY_DEMO_ADMIN_PASSWORD
  });
  await admin('GET', '/api/admin/accounts');
  await admin('PUT', '/api/admin/accounts/' + account.accountId, { balance: '500000.00', accountType: 'CURRENT' });
  const profile = { name: 'Updated Customer', email: 'updated-' + suffix + '@example.test', phone };
  await customer('PUT', '/api/users/current', profile, 403, undefined, false);
  await customer('PUT', '/api/users/current', profile);
  check('Session profile follows edited email', (await customer('GET', '/api/users/current')).email, profile.email);
  const beneficiary = await customer('POST', '/api/beneficiaries', {
    beneficiaryName: 'Demo Recipient', bankAccountNumber: '123456789012', ifsc: 'SBIN0001234'
  }, 201);
  const beneficiaryPath = '/api/beneficiaries/' + beneficiary.beneficiaryId;
  await customer('GET', beneficiaryPath);
  await customer('DELETE', beneficiaryPath, undefined, 204);
  check('Default list excludes inactive', (await customer('GET', '/api/beneficiaries')).length, 0);
  check('Opt-in list includes inactive', (await customer('GET', '/api/beneficiaries?includeInactive=true'))[0].status, 'INACTIVE');
  await customer('PATCH', beneficiaryPath + '/status', { status: 'ACTIVE' });
  const payment = { fromAccountId: account.accountId, beneficiaryId: beneficiary.beneficiaryId, amount: '5000.00', purpose: 'Local smoke test' };
  const initiateKey = randomUUID();
  const instant = await customer('POST', '/api/transactions', payment, 200, initiateKey);
  check('New-beneficiary INR 5000 payment remains LOW', instant.riskTier, 'LOW');
  check('LOW immediately settles', instant.state, 'SETTLED');
  check('LOW has no protection timer', instant.protectionSeconds, 0);
  check('LOW initiation replay', (await customer('POST', '/api/transactions', payment, 200, initiateKey)).transactionId, instant.transactionId);
  check('LOW replay debits only once', Number((await customer('GET', '/api/accounts/current')).balance), 495000);
  const hardHoldPayment = { ...payment, amount: '110000.00', purpose: 'Hard-hold smoke test' };
  const heldInitiateKey = randomUUID();
  const held = await customer('POST', '/api/transactions', hardHoldPayment, 200, heldInitiateKey);
  check('Above INR 100000 is VERY_HIGH', held.riskTier, 'VERY_HIGH');
  check('VERY_HIGH requires verification', held.state, 'HARD_HOLD');
  check('Hard-hold initiation replay', (await customer('POST', '/api/transactions', hardHoldPayment, 200, heldInitiateKey)).transactionId, held.transactionId);
  await admin('PUT', '/api/admin/accounts/' + account.accountId, { balance: '114999.99', accountType: 'CURRENT' }, 409);
  check('Rejected balance edit preserves all held funds', Number((await customer('GET', '/api/accounts/current')).balance), 495000);
  const verifyKey = randomUUID(), txPath = '/api/transactions/' + held.transactionId;
  await customer('POST', txPath + '/verify', { password: 'incorrect' }, 403, verifyKey);
  check('Wrong password retains hold', (await customer('GET', txPath)).state, 'HARD_HOLD');
  check('Verification settles', (await customer('POST', txPath + '/verify', { password }, 200, verifyKey)).state, 'SETTLED');
  await customer('POST', txPath + '/verify', { password }, 200, verifyKey);
  check('Verification replay debits only once', Number((await customer('GET', '/api/accounts/current')).balance), 385000);
  const timed = await customer('POST', '/api/transactions', { ...payment, amount: '20000.00' }, 200, randomUUID());
  check('INR 20000 is MEDIUM', timed.riskTier, 'MEDIUM');
  check('MEDIUM uses timed protection', timed.state, 'PROTECTED');
  check('MEDIUM protection is 10 seconds', timed.protectionSeconds, 10);
  const cancelKey = randomUUID();
  check('Cancel response is fresh', (await customer('POST', '/api/transactions/' + timed.transactionId + '/cancel', undefined, 200, cancelKey)).state, 'CANCELLED');
  await customer('POST', '/api/transactions/' + timed.transactionId + '/cancel', undefined, 200, cancelKey);
  check('Cancellation preserves posted balance', Number((await customer('GET', '/api/accounts/current')).balance), 385000);
  const high = await customer('POST', '/api/transactions', { ...payment, amount: '60000.00' }, 200, randomUUID());
  check('INR 60000 is HIGH', high.riskTier, 'HIGH');
  check('HIGH uses timed protection', high.state, 'PROTECTED');
  check('HIGH protection is 60 seconds', high.protectionSeconds, 60);
  const highCancelKey = randomUUID();
  check('HIGH can be cancelled in protection', (await customer('POST', '/api/transactions/' + high.transactionId + '/cancel', undefined, 200, highCancelKey)).state, 'CANCELLED');
  await customer('POST', '/api/transactions/' + high.transactionId + '/cancel', undefined, 200, highCancelKey);
  check('HIGH cancellation replay does not debit', Number((await customer('GET', '/api/accounts/current')).balance), 385000);
  const summary = await admin('GET', '/api/admin/reports/transactions/summary');
  assert.ok(summary.totalTransactions >= 4 && summary.settledTransactions >= 2 && summary.cancelledTransactions >= 2); checks++;
  const today = new Date().toLocaleDateString('en-CA');
  const daily = await admin('GET', '/api/admin/reports/transactions/daily?from=' + today + '&to=' + today);
  assert.ok(daily.length > 0); checks++;
  console.log('PASS: ' + checks + ' local HTTP checks covering registration, sessions/CSRF, profile, beneficiary lifecycle, admin balance, all four amount-only risk tiers, verification/cancellation replay and reports.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
