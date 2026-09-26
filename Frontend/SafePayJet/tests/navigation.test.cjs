const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const ko = require('knockout');
function shell(logout = () => Promise.resolve(), getCurrent = () => Promise.resolve({ name: 'Customer', email: 'customer@example.test' }), pathname = '/login', checkSessionRoute = () => Promise.resolve({kind:'guest'}), timer = fn => {fn();return 1;}) {
  const redirects = [], historyChanges = [], removedAttributes = [], module = { exports: {} };
  let routerConfig, routerInstance, syncs = 0;
  class Router {
    constructor(routes, options) {
      routerConfig = { routes, options };
      routerInstance = this;
      this.beforeStateChange = ko.observable();
      this.currentState = ko.observable();
      const subscribe = this.currentState.subscribe.bind(this.currentState);
      this.currentState.subscribe = callback => {
        const subscription = subscribe(callback);
        callback({complete() {}});
        return subscription;
      };
    }
    sync() { syncs++; this.beforeStateChange({}); this.currentState({state:{path:'test'}}); return Promise.resolve(); }
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
    URLSearchParams, setTimeout: timer, document: { documentElement: {removeAttribute: name=>removedAttributes.push(name)}, getElementById: () => ({ addEventListener() {} }) },
    window: { location: { pathname, search: '', replace: url => redirects.push(url) }, history: {replaceState: (_state,_title,url)=>historyChanges.push(url)}, addEventListener() {} }
  })(p => imports[p] || {}, module, module.exports);
  return { model: module.exports.default, redirects, historyChanges, removedAttributes, routerConfig, syncs: () => syncs, router: () => routerInstance };
}
test('guest sees public home, not a customer module', async () => {
  const f = shell(undefined, undefined, '/dashboard'); await Promise.resolve();
  assert.deepEqual(f.redirects, ['/home']); assert.equal(f.syncs(), 0);
  const home = shell(undefined, undefined, '/'); await Promise.resolve();
  assert.equal(home.syncs(), 1); assert.equal(home.routerConfig.routes[0].redirect, 'home');
});
test('route bar settles after public routing and tracks ordinary navigation', async () => {
  const f = shell(undefined, undefined, '/'); await Promise.resolve(); assert.equal(f.model.routeBusy(), false);
  assert.equal(f.model.entryLoaderBusy(), false);
  f.router().beforeStateChange({}); assert.equal(f.model.routeBusy(), true);
  assert.equal(f.model.entryLoaderBusy(), false);
  f.router().currentState({state:{path:'test'}}); assert.equal(f.model.routeBusy(), false);
});
test('full entry loader is never enabled by a normal feature route', async () => {
  const login = shell(undefined, undefined, '/login');
  assert.equal(login.model.entryLoaderBusy(), false);
  login.router().beforeStateChange({}); assert.equal(login.model.entryLoaderBusy(), false);
  const protectedPage = shell(undefined, undefined, '/transactions');
  assert.equal(protectedPage.model.entryLoaderBusy(), false);
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
test('signed-in customer opens dashboard with one bootstrap and no full-page reload', async () => {
  let finish; const timers=[];
  const f = shell(undefined, undefined, '/', () => new Promise(r=>finish=r), (fn,ms)=>{timers.push({fn,ms});return timers.length;});
  assert.equal(f.syncs(),0);assert.equal(f.model.entryLoaderBusy(),true);
  finish({kind:'customer',profile:{name:'Owner'}});await new Promise(setImmediate);
  assert.deepEqual(f.redirects,[]);assert.deepEqual(f.historyChanges,['/dashboard']);assert.equal(f.syncs(),1);
  assert.equal(timers.length,1);timers[0].fn();assert.equal(f.model.entryLoaderBusy(),false);
});

test('admin home handoff stays in the same document and unavailable sessions still show home',async()=>{
  const admin=shell(undefined,undefined,'/',async()=>({kind:'admin'}));await new Promise(setImmediate);
  assert.deepEqual(admin.redirects,[]);assert.deepEqual(admin.historyChanges,['/admin/dashboard']);assert.equal(admin.syncs(),1);
  const offline=shell(undefined,undefined,'/',async()=>{throw Error('offline');});await new Promise(setImmediate);
  assert.equal(offline.syncs(),1);assert.deepEqual(offline.historyChanges,[]);assert.equal(offline.model.entryLoaderBusy(),false);
});

test('loader rotates a full turn without competing transform animations and respects reduced motion',()=>{
  const css=fs.readFileSync(path.join(__dirname,'../src/css/loading-overlay.css'),'utf8');
  assert.match(css,/safepay-loader-flip 2s linear infinite/);assert.match(css,/perspective\(900px\) rotateY\(360deg\)/);
  assert.doesNotMatch(css,/safepay-loader-spin|rotate\(360deg\)/);
  assert.doesNotMatch(css,/safepay-loader-logo-reveal|safepay-loader-float/);
  assert.match(css,/prefers-reduced-motion: reduce/);assert.match(css,/animation-play-state: paused/);
});

test('initial JET subscription cannot hide the bootstrap logo before the first real route',async()=>{
  let finish;const timers=[];
  const f=shell(undefined,undefined,'/',()=>new Promise(resolve=>finish=resolve),(fn,ms)=>{timers.push({fn,ms});return timers.length;});
  assert.equal(f.model.entryLoaderBusy(),true);assert.deepEqual(f.removedAttributes,[]);assert.equal(timers.length,0);
  f.router().currentState({complete(){}});
  assert.equal(f.model.routeBusy(),true);assert.deepEqual(f.removedAttributes,[]);assert.equal(timers.length,0);
  finish({kind:'guest'});await new Promise(setImmediate);
  assert.equal(timers.length,1);assert.deepEqual(f.removedAttributes,[]);
  timers[0].fn();assert.equal(f.model.entryLoaderBusy(),false);assert.deepEqual(f.removedAttributes,['data-safepay-entry-loader']);
});

test('dashboard entry holds the logo for 2.5 seconds and does not replay on feature changes',async()=>{
  const timers=[];const f=shell(undefined,undefined,'/dashboard',async()=>({kind:'customer',profile:{name:'Owner'}}),(fn,ms)=>{timers.push({fn,ms});return timers.length;});
  assert.equal(f.model.entryLoaderBusy(),true);await new Promise(setImmediate);
  assert.equal(timers.length,1);assert.ok(timers[0].ms>2400 && timers[0].ms<=2500);
  assert.equal(f.model.entryLoaderBusy(),true);timers[0].fn();assert.equal(f.model.entryLoaderBusy(),false);
  f.router().beforeStateChange({});f.router().currentState({});assert.equal(f.model.entryLoaderBusy(),false);assert.equal(timers.length,1);
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
