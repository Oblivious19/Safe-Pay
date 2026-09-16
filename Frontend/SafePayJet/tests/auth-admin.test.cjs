const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
function load(file, imports = {}, globals = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/', file), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    TextEncoder, URLSearchParams, document: { title: '', getElementById: () => ({ focus() {} }) }, ...globals
  })(p => ({ knockout: ko, '../services/apiError': { ApiError }, './apiError': { ApiError, resourceId: id => {
    if (!Number.isSafeInteger(id) || id < 1) throw new ApiError(400, 'Invalid ID'); return id;
  } }, ...imports })[p] || {}, module, module.exports);
  return module.exports;
}
function registration(register = async () => ({})) {
  const calls = [], redirects = [];
  const Model = load('viewModels/register.ts', { '../services/authService': { authService: { register: input => { calls.push(input); return register(input); } } } }, { window: { location: { assign: url => redirects.push(url) } } });
  const model = new Model(); model.name(' A Customer '); model.email(' user@example.test '); model.phone('9876543210'); model.password(' exact password '); model.confirmPassword(' exact password ');
  return { model, calls, redirects };
}
test('registration sends only customer fields and exact password, then returns to login', async () => {
  const f = registration(); await f.model.register();
  assert.deepEqual(Object.keys(f.calls[0]).sort(), ['email', 'name', 'password', 'phone']);
  assert.equal(f.calls[0].name, 'A Customer'); assert.equal(f.calls[0].password, ' exact password ');
  assert.deepEqual(f.redirects, ['/login?registered=1']); assert.equal(f.model.password(), '');
});
for (const [field, value] of [['phone', '123'], ['email', 'bad'], ['confirmPassword', 'different'], ['password', 'é'.repeat(37)]]) {
  test(`registration rejects invalid ${field} before request`, async () => {
    const f = registration(); f.model[field](value); await f.model.register();
    assert.equal(f.calls.length, 0); assert.ok(f.model.error());
  });
}
test('registration ignores duplicate submit and late result after disconnect', async () => {
  let finish; const f = registration(() => new Promise(resolve => { finish = resolve; }));
  const pending = f.model.register(); await f.model.register(); assert.equal(f.calls.length, 1);
  f.model.disconnected(); finish({}); await pending; assert.equal(f.redirects.length, 0); assert.equal(f.model.password(), '');
});
test('registration displays server duplicate conflict without navigating', async () => {
  const f = registration(async () => { throw new ApiError(409, 'Phone is already registered'); });
  await f.model.register(); assert.match(f.model.error(), /Phone/); assert.equal(f.redirects.length, 0);
});
function administration(overrides = {}) {
  const redirects = [], calls = [];
  const user = { userId: 1, name: 'Customer', email: 'user@example.test', phone: '9876543210', role: 'CUSTOMER', status: 'ACTIVE' };
  const account = { accountId: 2, userId: 1, accountNumber: '1000000001', balance: 25000, accountType: 'SAVINGS', status: 'ACTIVE' };
  const admin = { users: async () => [user], accounts: async () => [account], user: async () => user, userAccounts: async () => [{ ...account, balance: '25000.00' }, { ...account, accountId: 3, balance: '90000.25' }], createUser: async () => ({ ...user, userId: 5, role: 'ADMIN' }), setUserStatus: async (id, status) => ({ ...user, status }), setAccountStatus: async (id, status) => ({ ...account, status }), updateAccount: async (id, balance, accountType) => ({ ...account, balance, accountType }), ...overrides };
  const Model = load('viewModels/admin.ts', {
    '../services/adminService': { adminService: admin },
    '../services/adminCreditDraft': load('services/adminCreditDraft.ts'),
    '../services/apiClient': { apiClient: { useAdminCsrf() {} } },
    '../services/adminReportService': { adminReportService: { summary: async () => ({}), daily: async (from, to) => { calls.push([from, to]); return []; } } }
  }, { window: { location: { replace: url => redirects.push(url) } }, crypto: { randomUUID: () => 'credit-retry-key' } });
  return { model: new Model(), redirects, calls, user, account };
}
test('admin page verifies role before loading account data', async () => {
  let accounts = 0;
  const f = administration({ users: async () => { throw new ApiError(403, 'Forbidden'); }, accounts: async () => { accounts++; return []; } });
  await f.model.load(); assert.equal(accounts, 0); assert.equal(f.model.verified(), false); assert.ok(f.model.usersError());
});
test('failed user status change preserves displayed server state', async () => {
  const f = administration({ setUserStatus: async () => { throw new ApiError(409, 'Status changed concurrently'); } });
  await f.model.load(); await f.model.toggleUser(f.model.users()[0]);
  assert.equal(f.model.users()[0].status, 'ACTIVE'); assert.match(f.model.actionError(), /concurrently/);
});
test('admin status controls update only after response and suppress duplicate clicks', async () => {
  let finish, calls = 0; const f = administration({ setUserStatus: () => { calls++; return new Promise(resolve => { finish = resolve; }); } });
  await f.model.load(); const pending = f.model.toggleUser(f.model.users()[0]); await f.model.toggleUser(f.model.users()[0]);
  assert.equal(calls, 1); assert.equal(f.model.users()[0].status, 'ACTIVE');
  finish({ ...f.user, status: 'SUSPENDED' }); await pending; assert.equal(f.model.users()[0].status, 'SUSPENDED');
});
test('account editing requires explicit balance and displays returned balance/type', async () => {
  const f = administration(); await f.model.load(); f.model.editAccount(f.model.accounts()[0]);
  assert.equal(f.model.balance(), ''); f.model.balance('30000.25'); f.model.accountType('CURRENT'); await f.model.saveAccount();
  assert.equal(f.model.accounts()[0].balance, '30000.25'); assert.equal(f.model.accounts()[0].accountType, 'CURRENT'); assert.equal(f.model.editingId(), null);
});
test('expired admin session clears private rows and returns to login', async () => {
  const f = administration({ setUserStatus: async () => { throw new ApiError(401, 'Expired'); } });
  await f.model.load(); await f.model.toggleUser(f.model.users()[0]);
  assert.equal(f.model.verified(), false); assert.equal(f.model.users().length, 0); assert.equal(f.model.accounts().length, 0);
  assert.deepEqual(f.redirects, ['/login?reason=session-expired']);
});
test('daily report sends selected date range', async () => {
  const f = administration(); await f.model.load(); f.model.from('2026-09-01'); f.model.to('2026-09-15');
  await f.model.loadDaily(); assert.deepEqual(f.calls, [['2026-09-01', '2026-09-15']]);
});
test('admin reconnect can load after disconnecting during a mutation', async () => {
  let finish;
  const f = administration({ setUserStatus: () => new Promise(resolve => { finish = resolve; }) });
  await f.model.load(); const pending = f.model.toggleUser(f.model.users()[0]);
  f.model.disconnected(); assert.equal(f.model.busy(), false); assert.equal(f.model.reportsLoading(), false);
  await f.model.load(); assert.equal(f.model.verified(), true);
  finish({ ...f.user, status: 'SUSPENDED' }); await pending;
  assert.equal(f.model.users()[0].status, 'ACTIVE'); assert.equal(f.model.notice(), '');
});

test('admin details expose every owned account and valid status choices', async () => {
  const f = administration(); await f.model.load(); await f.model.viewUser(f.user);
  assert.equal(f.model.userAccounts().length, 2); assert.equal(f.model.selectedUser().userId, 1);
  f.model.desiredStatus('LOCKED'); await f.model.saveUserStatus();
  assert.equal(f.model.users()[0].status, 'LOCKED'); assert.equal(f.model.selectedUser().status, 'LOCKED');
  assert.equal(f.model.exactMoney('9999999999999999.99'), '₹9,99,99,99,99,99,99,999.99');
});
test('admin provisioning clears initial password and adds only confirmed server result', async () => {
  const calls = []; const f = administration({ createUser: async value => { calls.push(value); return { userId: 5, role: 'ADMIN', status: 'ACTIVE' }; } });
  await f.model.load(); f.model.provisionName('Person'); f.model.provisionEmail('person@example.test'); f.model.provisionPhone('9876543210'); f.model.initialPassword('private initial');
  await f.model.provision(); assert.equal(calls[0].initialPassword, 'private initial'); assert.equal(f.model.initialPassword(), '');
  assert.equal(f.model.users().find(user => user.userId === 5).status, 'ACTIVE');
  assert.equal(f.model.users().find(user => user.userId === 5).role, 'ADMIN');
  assert.match(f.model.notice(), /Administrator created without a bank account/);
  assert.equal(f.model.accounts().length, 1);
});
test('admin credit retry retains frozen account, exact amount and key, then applies receipt', async () => {
  const calls = []; let attempt = 0;
  const f = administration({ credit: async (id, amount, key) => {
    calls.push({ id, amount, key }); if (++attempt === 1) throw new ApiError(0, 'Unknown result');
    return { accountId: id, amount, balanceBefore: '90000.25', balanceAfter: '90100.50', createdAt: 'server-time', description: 'interest' };
  } });
  await f.model.load(); await f.model.viewUser(f.user); f.model.creditAccountId('3'); f.model.creditAmount('100.25'); f.model.creditConfirmed(true);
  await f.model.submitCredit(); assert.equal(f.model.creditDraft().accountId, 3); assert.equal(f.model.disabled(), true);
  f.model.creditAccountId('2'); f.model.creditAmount('5000'); await f.model.submitCredit();
  assert.deepEqual(calls[0], calls[1]); assert.equal(calls[0].amount, '100.25');
  assert.equal(f.model.creditDraft(), null); assert.equal(f.model.creditReceipt().balanceAfter, '90100.50');
  assert.equal(f.model.userAccounts().find(account => account.accountId === 3).balance, '90100.50');
});
test('admin credit confirmation and duplicate-submit guards prevent extra requests', async () => {
  let finish, calls = 0;
  const f = administration({ credit: () => { calls++; return new Promise(resolve => { finish = resolve; }); } });
  await f.model.load(); await f.model.viewUser(f.user); f.model.creditAmount('100'); await f.model.submitCredit(); assert.equal(calls, 0);
  f.model.creditConfirmed(true); const pending = f.model.submitCredit(); await f.model.submitCredit(); assert.equal(calls, 1);
  finish({ accountId: 2, amount: '100', balanceBefore: '25000', balanceAfter: '25100', createdAt: 'now' }); await pending;
  assert.equal(f.model.busy(), false);
});
test('revoked administrator access clears details and private rows', async () => {
  const f = administration({ setUserStatus: async () => { throw new ApiError(403, 'Denied'); } });
  await f.model.load(); await f.model.viewUser(f.user); f.model.desiredStatus('SUSPENDED'); await f.model.saveUserStatus();
  assert.equal(f.model.selectedUser(), null); assert.equal(f.model.userAccounts().length, 0); assert.equal(f.model.verified(), false);
});
test('late successful credit across reconnect reuses the retained original operation key', async () => {
  const calls = []; let finishFirst;
  const f = administration({ credit: async (id, amount, key) => {
    calls.push({ id, amount, key });
    if (calls.length === 1) return new Promise(resolve => { finishFirst = resolve; });
    return { accountId: id, amount, balanceBefore: '25000', balanceAfter: '25100', createdAt: 'original-time' };
  } });
  await f.model.load(); await f.model.viewUser(f.user); f.model.creditAmount('100'); f.model.creditConfirmed(true);
  const pending = f.model.submitCredit(); f.model.disconnected(); await f.model.load();
  finishFirst({ accountId: 2, amount: '100', balanceBefore: '25000', balanceAfter: '25100', createdAt: 'original-time' }); await pending;
  assert.ok(f.model.creditDraft()); await f.model.submitCredit();
  assert.deepEqual(calls[0], calls[1]); assert.equal(f.model.creditReceipt().createdAt, 'original-time');
});