const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

// Execute the real TS modules with an isolated mocked transport. No backend or browser required.
function fixture(replies = []) {
  const calls = [], modules = new Map();
  const context = vm.createContext({
    Headers, Response, URL, TextEncoder, crypto: { randomUUID: () => 'test-idempotency-key' },
    fetch: async (url, options) => {
      calls.push({ url, ...options });
      if (!replies.length) throw new Error('Unexpected network request');
      const reply = replies.shift();
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
  return { load, calls };
}
const json = (body, status = 200, token) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json', ...(token ? { 'X-CSRF-TOKEN': token } : {}) }
});
const payment = { fromAccountId: 1, beneficiaryId: 2, amount: '10.25', purpose: 'Demo' };
for (const status of [200,201]) test('payment response '+status+' uses one credentialed protected POST',async()=>{
 const row={transactionId:123,state:'SETTLED',amount:10.25};
 const f=fixture([json({},200,'token'),json(row,status)]);
 const response=await f.load('transactionService').transactionService.create({...payment,userEmail:'ignored',userId:9,riskTier:'LOW',state:'SETTLED',balance:999},'stable-key');
 assert.equal(response.state,'SETTLED');const posts=f.calls.filter(c=>c.method==='POST');assert.equal(posts.length,1);
 assert.equal(posts[0].url,'http://localhost:8080/api/transactions');assert.equal(posts[0].credentials,'include');
 assert.equal(posts[0].headers.get('X-CSRF-TOKEN'),'token');assert.equal(posts[0].headers.get('Idempotency-Key'),'stable-key');
 assert.deepEqual(JSON.parse(posts[0].body),payment);
});
test('phone password login sends password without PIN and preserves cookies', async () => {
  const f = fixture([json({role:'CUSTOMER'})]);
  await f.load('authService').authService.login({phone:'9876543210',password:' raw password '});
  assert.deepEqual(JSON.parse(f.calls[0].body),{phone:'9876543210',password:' raw password '});
  assert.equal(f.calls[0].credentials,'include');
});
test('PIN registration and login use real endpoints, cookies and whitelisted fields', async () => {
  const f = fixture([json({message:'Created'},201), json({role:'CUSTOMER'})]);
  const auth = f.load('authService').authService;
  await auth.register({name:'Demo',email:'demo@example.test',phone:'9876543210',password:'DemoPass123',pin:'135790',pan:'ABCDE1234F',role:'ADMIN'});
  await auth.login({phone:'9876543210',pin:'135790',userId:99});
  assert.equal(f.calls[0].url,'http://localhost:8080/api/auth/register');
  assert.deepEqual(Object.keys(JSON.parse(f.calls[0].body)).sort(),['email','name','password','phone','pin']);
  assert.equal(f.calls[1].url,'http://localhost:8080/api/auth/login');
  assert.deepEqual(JSON.parse(f.calls[1].body),{phone:'9876543210',pin:'135790'});
  for(const call of f.calls) assert.equal(call.credentials,'include');
});
test('beneficiary service uses real session endpoints and CSRF for writes', async () => {
  const row = { beneficiaryId: 3 };
  const f = fixture([json([], 200, 'csrf-test'), json(row, 201), json(row), new Response(null, { status: 204 })]);
  const service = f.load('beneficiaryService').beneficiaryService;
  await service.list();
  await service.create({ beneficiaryName: ' Recipient ', bankAccountNumber: '00123', ifsc: 'hdfc0001234', userId: 99 });
  await service.get(3); await service.deactivate(3);
  assert.deepEqual(f.calls.map(c => [c.method, c.url]), [
    ['GET', 'http://localhost:8080/api/beneficiaries'], ['POST', 'http://localhost:8080/api/beneficiaries'],
    ['GET', 'http://localhost:8080/api/beneficiaries/3'], ['DELETE', 'http://localhost:8080/api/beneficiaries/3']]);
  assert.deepEqual(JSON.parse(f.calls[1].body), { beneficiaryName: 'Recipient', bankAccountNumber: '00123', ifsc: 'HDFC0001234' });
  for (const call of f.calls) assert.equal(call.credentials, 'include');
  assert.equal(f.calls[1].headers.get('X-CSRF-TOKEN'), 'csrf-test');
  assert.equal(f.calls[3].headers.get('X-CSRF-TOKEN'), 'csrf-test'); assert.equal(f.calls[3].body, undefined);
});
test('beneficiary service rejects invalid input before transport', () => {
  const f = fixture(); const service = f.load('beneficiaryService').beneficiaryService;
  assert.throws(() => service.create({ beneficiaryName: '', bankAccountNumber: 'x', ifsc: '' }));
  assert.throws(() => service.get(-1)); assert.equal(f.calls.length, 0);
});
test('profile uses session identity and retains only display fields', async () => {
  const f = fixture([json({ name: 'Customer', email: 'customer@example.test', userId: 1, phone: 'unused' })]);
  const profile = await f.load('profileService').profileService.getCurrent();
  assert.equal(f.calls[0].url, 'http://localhost:8080/api/users/current');
  assert.equal(f.calls[0].credentials, 'include'); assert.equal(f.calls[0].body, undefined);
  assert.deepEqual(Object.keys(profile).sort(), ['email', 'name']);
});

test('default account GET has cookies, no body, no cache and no client identity', async () => {
  const f = fixture([json({ accountId: 1 })]);
  await f.load('accountService').accountService.getCurrent();
  assert.equal(f.calls[0].url, 'http://localhost:8080/api/accounts/current');
  assert.equal(f.calls[0].credentials, 'include'); assert.equal(f.calls[0].cache, 'no-store');
  assert.equal(f.calls[0].body, undefined);
});
test('base URL is configurable without double slash', async () => {
  const f = fixture([json([])]);
  f.load('apiClient').configureApi('https://demo.invalid/');
  await f.load('transactionService').transactionService.list('PROTECTED');
  assert.equal(f.calls[0].url, 'https://demo.invalid/api/transactions?state=PROTECTED');
});
test('registration/login whitelist raw password fields; no automatic validation call', async () => {
  const f = fixture([json({ userId: 1 }, 201), json({ userId: 1, role: 'CUSTOMER' })]);
  const auth = f.load('authService').authService;
  await auth.register({ name: 'Demo', email: 'local@example.test', phone: '0000000000', password: 'fixture-only', role: 'ADMIN' });
  await auth.login({ email: 'local@example.test', password: 'fixture-only', userId: 99 });
  assert.equal(f.calls.length, 2);
  assert.deepEqual(Object.keys(JSON.parse(f.calls[0].body)).sort(), ['email','name','password','phone']);
  assert.deepEqual(Object.keys(JSON.parse(f.calls[1].body)).sort(), ['email','password']);
});
test('blank credentials fail locally', async () => {
  const f = fixture();
  await assert.rejects(f.load('authService').authService.login({ email: 'x', password: ' ' }), e => e.status === 400);
  assert.equal(f.calls.length, 0);
});
test('CSRF is captured from GET and sent with exact transaction payload and retry key', async () => {
  const f = fixture([json({}, 200, 'mock-csrf'), json({ transactionId: 3 }), json({ transactionId: 3 })]);
  const tx = f.load('transactionService').transactionService;
  await tx.create({ ...payment, userEmail: 'ignored', riskTier: 'LOW' }, 'same-key');
  await tx.create(payment, 'same-key');
  assert.equal(f.calls.length, 3);
  assert.equal(f.calls[0].url.endsWith('/api/accounts/current'), true);
  assert.equal(f.calls[1].url.endsWith('/api/transactions'), true);
  assert.equal(f.calls[1].headers.get('X-CSRF-TOKEN'), 'mock-csrf');
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), 'same-key');
  assert.deepEqual(JSON.parse(f.calls[1].body), payment);
  assert.equal(f.calls[1].body, f.calls[2].body);
});
test('missing exposed CSRF blocks protected POST rather than sending an unsafe request', async () => {
  const f = fixture([json({})]);
  await assert.rejects(f.load('transactionService').transactionService.create(payment, 'k'), e => e.kind === 'csrf');
  assert.equal(f.calls.length, 1);
});
test('invalid monetary precision, ID, purpose and key are local errors', async () => {
  const f = fixture(); const tx = f.load('transactionService').transactionService;
  for (const input of [{ ...payment, amount: '1.001' }, { ...payment, amount: '0.00' }, { ...payment, fromAccountId: -1 }, { ...payment, purpose: 'x'.repeat(256) }]) {
    await assert.rejects(tx.create(input, 'key'), e => e.status === 400);
  }
  await assert.rejects(tx.create(payment, ''), e => e.status === 400);
  assert.equal(f.calls.length, 0);
});
test('transaction detail and cancel use clean paths; cancel has no body', async () => {
  const f = fixture([json({}, 200, 'mock-csrf'), json({})]); const tx = f.load('transactionService').transactionService;
  await tx.get(3); await tx.cancel(3, 'cancel-key');
  assert.equal(f.calls[0].url.endsWith('/api/transactions/3'), true);
  assert.equal(f.calls[1].url.endsWith('/api/transactions/3/cancel'), true);
  assert.equal(f.calls[1].body, undefined); assert.equal(f.calls[1].headers.get('Idempotency-Key'), 'cancel-key');
});
for (const status of [400, 401, 403, 404, 409, 500]) {
  test(`central mapping preserves HTTP ${status} without SQL leakage`, async () => {
    const f = fixture([json({ message: 'ORA-00001 SYS.C123' }, status)]);
    await assert.rejects(f.load('accountService').accountService.getCurrent(), e => e.status === status && !e.message.includes('ORA-') && e.name === 'ApiError');
  });
}
test('safe 409 message is retained', async () => {
  const f = fixture([json({ message: 'Email is already registered' }, 409)]);
  await assert.rejects(f.load('accountService').accountService.getCurrent(), /Email is already registered/);
});
test('network/CORS failures and non-JSON failures map cleanly with no retries', async () => {
  const f = fixture([new Error('network'), new Response('<html>bad gateway</html>', { status: 500 })]);
  await assert.rejects(f.load('accountService').accountService.getCurrent(), e => e.kind === 'network' && e.status === 0);
  await assert.rejects(f.load('accountService').accountService.getCurrent(), e => e.status === 500 && !e.message.includes('html'));
  assert.equal(f.calls.length, 2);
});
test('empty 204 and malformed success bodies are handled', async () => {
  const f = fixture([new Response(null, { status: 204 }), new Response('not json')]); const client = f.load('apiClient').apiClient;
  assert.equal(await client.request('/api/accounts/current'), undefined);
  await assert.rejects(client.request('/api/accounts/current'), e => e.kind === 'response');
});
test('logout sends CSRF then forgets it; anonymous logout is safe', async () => {
  const f = fixture([json({}, 200, 'mock-csrf'), json({ message: 'Logged out successfully' }), json({}, 401), json({ message: 'Logged out successfully' })]);
  const auth = f.load('authService').authService;
  await auth.logout(); await auth.logout();
  assert.equal(f.calls[1].headers.get('X-CSRF-TOKEN'), 'mock-csrf');
  assert.equal(f.calls[3].headers.has('X-CSRF-TOKEN'), false);
});
test('ADMIN login selects a real admin GET for CSRF and reports use exact routes', async () => {
  const f = fixture([json({ role: 'ADMIN' }), json({}, 200, 'admin-csrf'), json({ message: 'Logged out successfully' }), json([])]);
  await f.load('authService').authService.login({ email: 'demo@example.test', password: 'fixture-only' });
  await f.load('authService').authService.logout();
  assert.equal(f.calls[1].url.endsWith('/api/admin/reports/transactions/summary'), true);
  await f.load('adminReportService').adminReportService.daily('2026-09-01','2026-09-15');
  assert.equal(f.calls[3].url.endsWith('/api/admin/reports/transactions/daily?from=2026-09-01&to=2026-09-15'), true);
});
test('invalid report dates fail locally', async () => {
  const f = fixture();
  await assert.rejects(f.load('adminReportService').adminReportService.daily('2026-02-30','2026-03-01'), e => e.status === 400);
  assert.equal(f.calls.length, 0);
});
test('old view-model facade ignores identity and retains a failed-payment retry key', async () => {
  const f = fixture([json({ accountId: 1 },200,'mock-csrf'), new Error('timeout'), json({ transactionId: 4 })]);
  const api = f.load('api'); await api.getAccounts('must-not-be-sent');
  await assert.rejects(api.initiateTransaction({ ...payment, userEmail: 'must-not-be-sent' }));
  await api.initiateTransaction({ ...payment, userEmail: 'must-not-be-sent' });
  assert.equal(f.calls.some(x => x.url.includes('userEmail')), false);
  assert.equal(f.calls[1].headers.get('Idempotency-Key'), f.calls[2].headers.get('Idempotency-Key'));
});
test('old in-flight GET cannot restore CSRF after session clear', async () => {
  let resolve; const f = fixture([() => new Promise(r => { resolve = r; }), json({})]);
  const client = f.load('apiClient').apiClient;
  const pending = client.request('/api/accounts/current'); client.clearSession();
  resolve(json({},200,'old-csrf')); await pending;
  await assert.rejects(f.load('transactionService').transactionService.create(payment,'key'), e => e.kind === 'csrf');
  assert.equal(f.calls.length, 2);
});
test('demo credentials open a simulated session without calling the API', async () => {
  const f = fixture();
  const user = await f.load('authService').authService.login({ phone: '9810010001', password: 'DemoPay@123' });
  assert.equal(user.name, 'Anika Sharma');
  assert.equal(user.role, 'CUSTOMER');
  const profile = await f.load('profileService').profileService.getCurrent();
  assert.equal(profile.name, 'Anika Sharma');
  assert.equal(profile.email, 'anika.demo@safepay.test');
  const account = await f.load('accountService').accountService.getCurrent();
  const history = await f.load('transactionService').transactionService.list();
  const spent = history.filter(row => row.state === 'SETTLED').reduce((total, row) => total + row.amount, 0);
  assert.equal(account.balance, 400000 - spent);
  assert.equal(f.calls.length, 0);
});
test('unknown credentials still fail when the API is unreachable', async () => {
  const f = fixture([new Error('connect')]);
  await assert.rejects(f.load('authService').authService.login({ phone: '9876543210', password: 'DemoPay@123' }), e => e.status === 0);
});
