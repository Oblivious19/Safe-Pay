const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
function shell(logout = () => Promise.resolve(), getCurrent = () => Promise.resolve({ name: 'Customer', email: 'customer@example.test' }), currentPath = 'login', restore) {
  const redirects = [], module = { exports: {} };
  let routerConfig;
  class Router { constructor(routes, options) { routerConfig = { routes, options }; } sync() { return Promise.resolve(); } }
  class Adapter { constructor(base) { this.base = base; this.path = () => currentPath; } }
  const imports = { knockout: ko, 'ojs/ojcorerouter': Router,
    'ojs/ojmodulerouter-adapter': Adapter, 'ojs/ojknockoutrouteradapter': Adapter,
    'ojs/ojurlpathadapter': Adapter, './services/authService': { authService: { logout } },
    './services/profileService': { profileService: { getCurrent } },
    './services/sessionService': { sessionService: { restore: restore || (async () => ({ role: 'CUSTOMER', profile: await getCurrent() })) } },
    './services/apiError': { ApiError: class ApiError extends Error {} },
    'ojs/ojcontext': { getPageContext: () => ({ getBusyContext: () => ({ applicationBootstrapComplete() {} }) }) } };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/appController.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    URLSearchParams, document: { getElementById: () => ({ addEventListener() {} }) },
    window: { location: { search: '', replace: url => redirects.push(url) } }
  })(p => imports[p] || {}, module, module.exports);
  return { model: module.exports.default, redirects, routerConfig };
}
test('clean dashboard route uses root path adapter', () => {
  const f = shell(); assert.ok(f.routerConfig.routes.some(r => r.path === 'dashboard'));
  assert.equal(f.routerConfig.options.urlAdapter.base, '/');
});
test('register, admin and profile routes are registered', () => {
  const f = shell();
  for (const route of ['register', 'admin', 'profile']) assert.ok(f.routerConfig.routes.some(r => r.path === route));
});
test('verified administrator session redirects a customer route to administration', async () => {
  const f = shell(undefined, undefined, 'dashboard', async () => ({ role: 'ADMIN', profile: null }));
  await f.model.loadProfile();
  assert.equal(f.model.sessionRole(), 'ADMIN');
  assert.equal(f.model.navItems()[0].path, 'admin');
  assert.ok(f.redirects.includes('/admin'));
});
test('verified customer cannot stay on administration route', async () => {
  const f = shell(undefined, undefined, 'admin'); await f.model.loadProfile();
  assert.ok(f.redirects.includes('/dashboard'));
  assert.equal(f.model.sessionRole(), 'CUSTOMER');
});
test('logout waits for service then navigates to login; duplicate clicks ignored', async () => {
  let finish, calls = 0;
  const f = shell(() => { calls++; return new Promise(resolve => { finish = resolve; }); });
  const pending = f.model.logout(); await f.model.logout();
  assert.equal(calls, 1); assert.equal(f.model.loggingOut(), true); assert.equal(f.redirects.length, 0);
  finish(); await pending; assert.deepEqual(f.redirects, ['/login']);
});
test('logout failure stays on page and offers retry', async () => {
  const f = shell(() => Promise.reject(new Error('Server detail'))); await f.model.logout();
  assert.equal(f.redirects.length, 0); assert.equal(f.model.loggingOut(), false);
  assert.equal(f.model.logoutError(), 'We couldn’t sign you out. Please try again.');
});
test('account menu loads current customer identity and clears it on logout', async () => {
  const f = shell(); await f.model.loadProfile();
  assert.equal(f.model.profile().name, 'Customer');
  assert.equal(f.model.profile().email, 'customer@example.test');
  await f.model.logout(); assert.equal(f.model.profile(), null);
});
test('profile failure never retains a stale identity', async () => {
  const f = shell(undefined, () => Promise.reject(new Error('Unavailable')));
  f.model.profile({ name: 'Old customer', email: 'old@example.test' });
  await f.model.loadProfile(); assert.equal(f.model.profile(), null);
  assert.equal(f.model.profileLoading(), false);
});
test('late profile response cannot restore identity after logout', async () => {
  let finish; const f = shell(undefined, () => new Promise(resolve => { finish = resolve; }));
  const pending = f.model.loadProfile(); await f.model.logout();
  finish({ name: 'Old customer', email: 'old@example.test' }); await pending;
  assert.equal(f.model.profile(), null);
});
test('development fallback serves clean routes, not API or missing assets', async () => {
  const config = await require('../scripts/hooks/before_serve')({});
  const middleware = config.preMiddleware[0];
  for (const [url, expected] of [['/dashboard', '/index.html'], ['/register', '/index.html'], ['/admin', '/index.html'], ['/profile', '/index.html'], ['/login?reason=session-expired', '/index.html'], ['/api/accounts/current', '/api/accounts/current'], ['/missing.js', '/missing.js']]) {
    const req = { method: 'GET', url }; let next = false;
    middleware(req, {}, () => { next = true; }); assert.equal(req.url, expected); assert.equal(next, true);
  }
});
