const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
function shell(logout = () => Promise.resolve(), getCurrent = () => Promise.resolve({ name: 'Customer', email: 'customer@example.test' }), pathname = '/login', checkSessionRoute = () => Promise.resolve({kind:'guest'})) {
  const redirects = [], module = { exports: {} };
  let routerConfig, routerInstance, syncs = 0;
  class Router {
    constructor(routes, options) {
      routerConfig = { routes, options };
      routerInstance = this;
      this.beforeStateChange = ko.observable();
      this.currentState = ko.observable();
    }
    sync() { syncs++; this.beforeStateChange({}); this.currentState({}); return Promise.resolve(); }
  }
  class Adapter { constructor(base) { this.base = base; this.path = () => 'login'; } }
  const imports = { knockout: ko, 'ojs/ojcorerouter': Router,
    'ojs/ojmodulerouter-adapter': Adapter, 'ojs/ojknockoutrouteradapter': Adapter,
    'ojs/ojurlpathparamadapter': Adapter, './services/authService': { authService: { logout } },
    './services/profileService': { profileService: { getCurrent } },
    './services/sessionRouteService': { checkSessionRoute },
    './utils/chime': { armAudio() {} },
    'ojs/ojcontext': { getPageContext: () => ({ getBusyContext: () => ({ applicationBootstrapComplete() {} }) }) } };
  const source = fs.readFileSync(path.join(__dirname, '../src/ts/appController.ts'), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
  vm.runInNewContext('(function(require,module,exports){' + code + '\n})', {
    URLSearchParams, document: { getElementById: () => ({ addEventListener() {} }) },
    window: { location: { pathname, search: '', replace: url => redirects.push(url) }, addEventListener() {} }
  })(p => imports[p] || {}, module, module.exports);
  return { model: module.exports.default, redirects, routerConfig, syncs: () => syncs, router: () => routerInstance };
}
test('guest sees public home, not a customer module', async () => {
  const f = shell(undefined, undefined, '/dashboard'); await Promise.resolve();
  assert.deepEqual(f.redirects, ['/home']); assert.equal(f.syncs(), 0);
  const home = shell(undefined, undefined, '/'); await Promise.resolve();
  assert.equal(home.syncs(), 1); assert.equal(home.routerConfig.routes[0].redirect, 'home');
});
test('route loader shows until the router settles on a state', async () => {
  const f = shell(undefined, undefined, '/'); assert.equal(f.model.routeBusy(), true);
  await Promise.resolve(); assert.equal(f.model.routeBusy(), false);
  f.router().beforeStateChange({}); assert.equal(f.model.routeBusy(), true);
  f.router().currentState({}); assert.equal(f.model.routeBusy(), false);
});
test('private route waits for server session; customer then loads', async () => {
  let finish; const f = shell(undefined, undefined, '/dashboard', () => new Promise(r => finish=r));
  assert.equal(f.syncs(), 0); finish({kind:'customer',profile:{name:'Owner',email:'owner@example.test'}});
  await Promise.resolve(); assert.equal(f.syncs(),1); assert.equal(f.model.profile().name,'Owner');
});
test('confirmed admin is redirected away from customer dashboard', async () => {
  const f = shell(undefined, undefined, '/dashboard', async () => ({kind:'admin'})); await Promise.resolve();
  assert.deepEqual(f.redirects,['/admin/dashboard']); assert.equal(f.syncs(),0);
});
test('unavailable session never renders customer data', async () => {
  const f = shell(undefined, undefined, '/beneficiaries', async () => ({kind:'unavailable'})); await Promise.resolve();
  assert.deepEqual(f.redirects,['/home?reason=unavailable']); assert.equal(f.syncs(),0);
});
test('signed-in customer opening home goes to dashboard', async () => {
  const f = shell(undefined, undefined, '/', async () => ({kind:'customer',profile:{name:'Owner'}})); await Promise.resolve();
  assert.deepEqual(f.redirects,['/dashboard']); assert.equal(f.syncs(),0);
});
test('clean dashboard route uses root path adapter', () => {
  const f = shell(); assert.ok(f.routerConfig.routes.some(r => r.path === 'dashboard'));
  assert.equal(f.routerConfig.options.urlAdapter.base, '/');
  assert.ok(f.routerConfig.routes.some(r => r.path === 'send-money/{step}'));
  assert.ok(f.routerConfig.routes.some(r => r.path === 'profile'));
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
  for (const [url, expected] of [['/dashboard', '/index.html'], ['/login?reason=session-expired', '/index.html'], ['/api/accounts/current', '/api/accounts/current'], ['/missing.js', '/missing.js']]) {
    const req = { method: 'GET', url }; let next = false;
    middleware(req, {}, () => { next = true; }); assert.equal(req.url, expected); assert.equal(next, true);
  }
});
