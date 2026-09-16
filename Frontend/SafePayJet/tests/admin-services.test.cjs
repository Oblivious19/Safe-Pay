const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
function load(file, imports = {}, globals = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/', file), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    TextEncoder, URLSearchParams, document: { title: '', getElementById: () => ({ focus() {} }) }, ...globals
  })(p => ({ '../services/apiError': { ApiError }, './apiError': { ApiError, resourceId: id => {
    if (!Number.isSafeInteger(id) || id < 1) throw new ApiError(400, 'Invalid ID'); return id;
  } }, ...imports })[p] || {}, module, module.exports);
  return module.exports;
}
function sessions({ customer = async () => ({ name: 'Verified', email: 'verified@example.test' }), admin = async () => [] } = {}) {
  const calls = [], roles = [];
  const service = load('services/sessionService.ts', {
    './apiClient': { apiClient: { useAdminCsrf: role => roles.push(role) } },
    './profileService': { profileService: { getCurrent: () => { calls.push('customer'); return customer(); } } },
    './adminService': { adminService: { users: () => { calls.push('admin'); return admin(); } } }
  }).sessionService;
  return { service, calls, roles };
}
test('admin restoration requires a successful protected admin response', async () => {
  const f = sessions(); const session = await f.service.restore(true);
  assert.equal(session.role, 'ADMIN'); assert.equal(session.profile, null);
  assert.deepEqual(f.calls, ['admin']); assert.deepEqual(f.roles, [true]);
});
test('customer role denial probes admin and configures admin CSRF', async () => {
  const f = sessions({ customer: async () => { throw new ApiError(403, 'Forbidden'); } });
  assert.equal((await f.service.restore()).role, 'ADMIN'); assert.deepEqual(f.calls, ['customer', 'admin']);
});
for (const error of [new ApiError(401, 'Expired'), new ApiError(0, 'Network')]) {
  test(`session ${error.status} failure cannot establish another role`, async () => {
    const f = sessions({ customer: async () => { throw error; } });
    await assert.rejects(f.service.restore()); assert.deepEqual(f.calls, ['customer']); assert.equal(f.roles.length, 0);
  });
}
test('admin mutations send CSRF and exact decimal balance', async () => {
  const calls = [];
  const service = load('services/adminService.ts', { './apiClient': { apiClient: { request: async (url, options) => { calls.push({ url, options }); return {}; } } } }).adminService;
  await service.setUserStatus(2, 'SUSPENDED'); await service.setAccountStatus(3, 'BLOCKED');
  await service.updateAccount(3, '9999999999999999.99', 'CURRENT');
  assert.ok(calls.every(call => call.options.csrf === true));
  assert.equal(calls[2].options.method, 'PUT'); assert.equal(calls[2].options.body.balance, '9999999999999999.99');
  assert.throws(() => service.updateAccount(3, '5000.001', 'CURRENT'), /valid balance/);
  assert.equal(calls.length, 3);
});

test('admin new endpoints whitelist provisioning, use plural accounts and send a credit idempotency key', async () => {
  const calls = [];
  const service = load('services/adminService.ts', {
    './adminCreditDraft': load('services/adminCreditDraft.ts'),
    './apiClient': { apiClient: { request: async (url, options) => { calls.push({ url, options }); return {}; } } }
  }).adminService;
  await service.user(1); await service.userAccounts(1);
  await service.createUser({ name: ' Person ', email: 'person@example.test', phone: '9876543210', initialPassword: ' exact initial ', role: 'ADMIN', status: 'ACTIVE' });
  await service.credit(2, '9999999999999999.99', 'same-credit-key'); await service.setUserStatus(1, 'LOCKED');
  assert.equal(calls[1].url, '/api/admin/users/1/accounts');
  assert.deepEqual(Object.keys(calls[2].options.body).sort(), ['email', 'initialPassword', 'name', 'phone']);
  assert.equal(calls[2].options.body.initialPassword, ' exact initial ');
  assert.equal(calls[3].options.idempotencyKey, 'same-credit-key'); assert.equal(calls[3].options.csrf, true);
  assert.equal(calls[3].options.body.amount, '9999999999999999.99');
  assert.throws(() => service.credit(2, '0', 'same-credit-key')); assert.throws(() => service.createUser({ name: 'x', email: 'x@x.test', phone: '9876543210', initialPassword: 'short' }));
});