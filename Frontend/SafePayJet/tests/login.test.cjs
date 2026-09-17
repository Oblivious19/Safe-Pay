const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('typescript');
const ko = require('knockout');
class ApiError extends Error { constructor(status, message) { super(message); this.status = status; } }
const read = file => fs.readFileSync(path.join(__dirname, '../src/', file), 'utf8');
test('server-authenticated ADMIN navigates to admin dashboard',async()=>{
  const f=fixture(async()=>({role:'ADMIN'}));await f.model.login();assert.deepEqual(f.redirects,['/admin/dashboard']);
});
test('phone password login preserves exact password and navigates', async () => {
  const f = fixture(); f.model.mode('phone'); f.model.phone('9876543210');
  await f.model.login(); assert.equal(f.calls[0].password, ' Test password ');
  assert.deepEqual(Object.keys(f.calls[0]).sort(), ['password','phone']); assert.deepEqual(f.redirects, ['/dashboard']);
});
function fixture(call = () => Promise.resolve({}), search = '') {
  const calls = [], redirects = [], module = { exports: {} };
  const code = ts.transpileModule(read('ts/viewModels/login.ts'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  const imports = { knockout: ko, '../services/apiError': { ApiError }, '../services/authService': { authService: { login: data => { calls.push(data); return call(data); } } }, 'ojs/ojbutton': {}, 'ojs/ojinputtext': {} };
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    document: { title: '', getElementById: () => ({ focus() {} }) },
    URLSearchParams, window: { location: { search, assign: url => redirects.push(url) } }
  })(p => imports[p], module, module.exports);
  const model = new module.exports(); model.mode('email'); model.email('customer@example.test'); model.password(' Test password ');
  return { model, calls, redirects };
}
for (const [field, value, message] of [
  ['email', '', 'Email is required'], ['email', 'invalid', 'Enter a valid email address'],
  ['password', '', 'Password is required'], ['password', '   ', 'Password is required']
]) test(`login local validation: ${field} ${JSON.stringify(value)}`, async () => {
  const f = fixture(); f.model[field](value); await f.model.login();
  assert.equal(f.model[field + 'Error'](), message); assert.equal(f.calls.length, 0);
});
test('successful login sends exact raw password and navigates to dashboard', async () => {
  const f = fixture(); f.model.email(' customer@example.test '); await f.model.login();
  assert.equal(f.calls[0].email, 'customer@example.test'); assert.equal(f.calls[0].password, ' Test password ');
  assert.deepEqual(f.redirects, ['/dashboard']); assert.equal(f.model.password(), '');
});
test('login 401 uses generic credentials message', async () => {
  const f = fixture(() => Promise.reject(new ApiError(401, 'Ignored'))); await f.model.login();
  assert.equal(f.model.error(), 'Invalid email or password'); assert.equal(f.redirects.length, 0);
  assert.equal(f.model.password(), ''); assert.equal(f.model.submitting(), false);
});
for (const status of ['locked', 'suspended', 'inactive']) test(`login 403 shows safe ${status} message`, async () => {
  const message = 'Your account is ' + status;
  const f = fixture(() => Promise.reject(new ApiError(403, message))); await f.model.login();
  assert.equal(f.model.error(), message); assert.equal(f.redirects.length, 0);
});
for (const failure of [new Error('Network details'), new ApiError(500, 'Internal details')])
  test(`login friendly failure: ${failure.constructor.name}`, async () => {
    const f = fixture(() => Promise.reject(failure)); await f.model.login();
    assert.equal(f.model.error(), 'We couldn’t sign you in right now. Please try again shortly.');
    assert.equal(f.model.submitting(), false);
  });
test('pending login disables button and prevents duplicate calls', async () => {
  let finish; const f = fixture(() => new Promise(resolve => { finish = resolve; }));
  const pending = f.model.login(); assert.equal(f.model.submitting(), true);
  await f.model.login(); assert.equal(f.calls.length, 1);
  assert.match(read('ts/views/login.html'), /class="login-submit"[^>]+data-bind="disable: submitting"/);
  finish({}); await pending;
});
test('disconnect clears password and ignores late login result', async () => {
  let finish; const f = fixture(() => new Promise(resolve => { finish = resolve; }));
  f.model.togglePassword(); assert.equal(f.model.showPassword(), true);
  const pending = f.model.login(); f.model.disconnected(); finish({}); await pending;
  assert.equal(f.model.password(), ''); assert.equal(f.model.showPassword(), false); assert.equal(f.redirects.length, 0);
});
test('login markup is labeled, password masked and never stored', () => {
  const html = read('ts/views/login.html'); assert.match(html, /type="password"/);
  assert.match(html, /for="login-email"/); assert.match(html, /role="alert"/);
  assert.doesNotMatch(read('ts/viewModels/login.ts'), /localStorage|sessionStorage/);
});
test('login explains the session-expired redirect without exposing query text', () => {
  const f = fixture(undefined, '?reason=session-expired'); f.model.connected();
  assert.match(f.model.error(), /Please log in to continue/);
  const other = fixture(undefined, '?reason=internal-secret'); other.model.connected();
  assert.equal(other.model.error(), '');
});
