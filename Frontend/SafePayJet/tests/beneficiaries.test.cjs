const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
class ApiError extends Error { constructor(status, message = 'Safe error') { super(message); this.status = status; } }
const row = { accountId:1,beneficiaryId: 7, beneficiaryName: 'Recipient', bankAccountNumber: '0012345678', ifsc: 'HDFC0001234', status: 'ACTIVE', createdAt: new Date().toISOString() };
function load(file, imports, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/', file + '.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const module = { exports: {} };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', globals)(p => imports[p], module, module.exports);
  return module.exports;
}
const validation = load('services/beneficiaryService', { './apiClient': {}, './apiError': { ApiError } }).validateBeneficiary;
function fixture(overrides = {}) {
  const calls = [], redirects = [];
  const defaults = { list: async () => [row], create: async () => row, get: async () => row, deactivate: async () => undefined };
  const service = Object.fromEntries(Object.keys(defaults).map(key => [key, (...args) => { calls.push([key, ...args]); return (overrides[key] || defaults[key])(...args); }]));
  const Model = load('viewModels/beneficiaries', { knockout: ko, '../services/accountService': {accountService:{list:async()=>[{accountId:1,status:'ACTIVE'}]}}, '../services/apiError': { ApiError }, '../services/beneficiaryService': { beneficiaryService: service, validateBeneficiary: validation } }, { document: { getElementById: () => ({ focus() {} }) }, window: { location: { replace: url => redirects.push(url) } } });
  const model=new Model(); model.selectedAccountId(1);return { model, calls, redirects };
}
function fill(model) { model.beneficiaryName('Recipient'); model.bankAccountNumber('0012345678'); model.ifsc('HDFC0001234'); }
test('list loads only session-owned API data', async () => { const f = fixture(); await f.model.load(); assert.equal(f.model.beneficiaries()[0], row); assert.deepEqual(f.calls, [['list',1,false]]); });
test('empty list is distinct from error', async () => { const f = fixture({ list: async () => [] }); await f.model.load(); assert.equal(f.model.beneficiaries().length, 0); assert.equal(f.model.error(), ''); });
test('valid add returns to list, preserves success and refreshes exactly once', async () => {
  const f = fixture(); f.model.openAdd(); fill(f.model); await f.model.save();
  assert.equal(f.model.mode(), 'list'); assert.equal(f.model.success(), 'Beneficiary added successfully.');
  assert.deepEqual(f.calls.map(c => c[0]), ['create', 'list']); assert.equal(f.model.bankAccountNumber(), '');
});
test('unverified recipient requires a visible confirmation before an external save', async () => {
  const f = fixture({ create: async () => { throw new ApiError(409, 'This recipient is not verified as a SafePay user. Confirm to add them as an external beneficiary.'); } });
  f.model.openAdd(); fill(f.model); await f.model.save();
  assert.equal(f.model.confirmExternal(), true); assert.equal(f.model.error(), '');
});
test('invalid local form makes no request', async () => {
  const f = fixture(); await f.model.save(); assert.equal(Object.keys(f.model.fieldErrors()).length, 3); assert.equal(f.calls.length, 0);
  fill(f.model); f.model.ifsc('TOO-LONG-INVALID'); await f.model.save(); assert.equal(f.calls.length, 0);
});
test('validation matches lengths, digits and IFSC; preserves leading zeros', () => {
  assert.equal(Object.keys(validation({ beneficiaryName: 'A', bankAccountNumber: '001', ifsc: 'hdfc0001234' })).length, 0);
  assert.equal(Object.keys(validation({ beneficiaryName: 'x'.repeat(101), bankAccountNumber: '12x', ifsc: 'HDFC1001234' })).length, 3);
});
for (const status of [400, 403, 409, 500]) test(`add handles ${status} without refreshing`, async () => {
  const f = fixture({ create: async () => { throw new ApiError(status); } }); f.model.openAdd(); fill(f.model); await f.model.save();
  assert.equal(f.model.mode(), 'add'); assert.equal(f.calls.length, 1); assert.ok(f.model.error()); assert.equal(f.model.saving(), false);
  if (status === 409) assert.equal(f.model.error(), 'Safe error');
});
test('401 clears beneficiary data and redirects to login', async () => {
  const f = fixture({ list: async () => { throw new ApiError(401); } }); f.model.beneficiaries([row]); await f.model.load();
  assert.equal(f.model.beneficiaries().length, 0); assert.deepEqual(f.redirects, ['/login?reason=session-expired']);
});
test('loading and double-submission protection', async () => {
  let finish; const f = fixture({ create: () => new Promise(resolve => { finish = resolve; }) }); fill(f.model);
  const pending = f.model.save(); assert.equal(f.model.saving(), true); await f.model.save(); assert.equal(f.calls.length, 1);
  finish(row); await pending; assert.equal(f.model.saving(), false);
});
test('details refetch through owned endpoint and deactivation requires confirmation', async () => {
  const f = fixture(); await f.model.select(row); assert.equal(f.model.selected(), row);
  await f.model.deactivate(); assert.equal(f.calls.length, 1);
  f.model.confirmDeactivate(true); await f.model.deactivate(); assert.deepEqual(f.calls.map(c => c[0]), ['get', 'deactivate', 'list']);
});
test('late list response after navigation is ignored', async () => {
  let finish; const f = fixture({ list: () => new Promise(resolve => { finish = resolve; }) });
  const pending = f.model.load(); assert.equal(f.model.loading(), true); await new Promise(resolve=>setImmediate(resolve)); f.model.disconnected(); finish([row]); await pending; assert.equal(f.model.beneficiaries().length, 0);
});
test('display masks account and new badge is age-only', () => {
  const { model } = fixture(); assert.equal(model.mask('0012345678'), '•••• 5678');
  assert.equal(model.isNew(new Date().toISOString()), true); assert.equal(model.isNew('2000-01-01'), false);
  const html = fs.readFileSync(path.join(__dirname, '../src/ts/views/beneficiaries.html'), 'utf8');
  assert.doesNotMatch(html, /userId|userEmail|beneficiaryId|localStorage/);
});
