const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const ts=require('typescript');
const ko=require('knockout');
class ApiError extends Error {constructor(status){super();this.status=status;}}
function transpile(file){
 return ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
}
function service(profile,admin){
 const module={exports:{}};
 const imports={'./profileService':{profileService:{getCurrent:profile}},'./adminReportService':{adminReportService:{summary:admin}},'./apiError':{ApiError}};
 vm.runInNewContext('(function(require,module,exports){'+transpile('src/ts/services/sessionRouteService.ts')+'\n})')(p=>imports[p],module,module.exports);
 return module.exports.checkSessionRoute;
}
function homeModel(){
 const disconnects=[];
 const protection={exports:{}};
 vm.runInNewContext('(function(require,module,exports){'+transpile('src/ts/utils/protection.ts')+'\n})')(()=>({}),protection,protection.exports);
 const module={exports:{}};
 const fakeWindow={
  location:{search:''}, innerHeight:900, scrollY:0,
  matchMedia:()=>({matches:false}),
  addEventListener(){},
  removeEventListener(type){disconnects.push(type);}
 };
 vm.runInNewContext('(function(require,module,exports){'+transpile('src/ts/viewModels/home.ts')+'\n})',{
  window:fakeWindow, document:{title:'',getElementById:()=>null},
  IntersectionObserver:class{observe(){} disconnect(){}},
  URLSearchParams, setInterval, clearInterval, setTimeout, clearTimeout,
  requestAnimationFrame:(cb)=>{cb();return 1;}, cancelAnimationFrame(){}
 })(p=>p==='knockout'?ko:p==='../utils/protection'?protection.exports:{},module,module.exports);
 return {model:new module.exports(), disconnects};
}
test('401 is guest without probing admin reports',async()=>{
 let calls=0;const check=service(async()=>{throw new ApiError(401)},async()=>{calls++});
 assert.equal((await check()).kind,'guest');assert.equal(calls,0);
});
test('customer identity comes from current server profile',async()=>{
 const profile={name:'Owner',email:'owner@example.test'};
 const result=await service(async()=>profile,async()=>{throw Error()})();
 assert.equal(result.kind,'customer');assert.equal(result.profile,profile);
});
test('admin must be confirmed by protected reports endpoint',async()=>{
 assert.equal((await service(async()=>{throw new ApiError(403)},async()=>({}))()).kind,'admin');
 assert.equal((await service(async()=>{throw new ApiError(403)},async()=>{throw new ApiError(403)})()).kind,'unavailable');
});
test('network error does not grant private access',async()=>{
 assert.equal((await service(async()=>{throw Error()},async()=>({}))()).kind,'unavailable');
});
test('hero offers login and registration without private account controls',()=>{
 const html=fs.readFileSync('src/ts/views/home.html','utf8');
 assert.match(html,/href="\/login"/);assert.match(html,/href="\/register"/);
 assert.match(html,/No real money is moved/);assert.doesNotMatch(html,/Logout|Load account|userId|balance/);
 const shell=fs.readFileSync('src/index.html','utf8');assert.match(shell,/visible: profile\(\)/);
});
test('home roadmap is interactive and stays in customer language',()=>{
 const html=fs.readFileSync('src/ts/views/home.html','utf8');
 const css=fs.readFileSync('src/css/home.css','utf8');
 assert.match(html,/Play the flow/);assert.match(html,/selectPath\('pause'\)/);
 assert.match(html,/home-scene/);assert.match(html,/home-story-scene/);
 assert.match(html,/From confirm to settled/);assert.match(html,/No automatic release/);
 assert.doesNotMatch(html,/\b(LOW|MEDIUM|HIGH|VERY_HIGH|HARD_HOLD|AUTHORIZED)\b/);
 assert.match(css,/home-enter/);assert.match(css,/prefers-reduced-motion:reduce/);
 const {model,disconnects}=homeModel();
 model.connected();
 assert.equal(model.activeStep(),'confirm');
 model.selectPath('hold');
 assert.equal(model.activeStep(),'decide');
 model.selectStep('done');
 assert.match(model.storyBody(),/held for extra review/i);
 model.selectStep('protect');
 assert.equal(model.activeStep(),'protect');
 assert.match(model.storyBody(),/No timer/i);
 assert.match(model.storyBody(),/will not auto-release/i);
 model.disconnected();
 assert.ok(disconnects.includes('scroll'));
});
test('home supports clean route refresh',async()=>{
 const config=await require('../scripts/hooks/before_serve')({});const req={method:'GET',url:'/home'};
 config.preMiddleware[0](req,{},()=>{});assert.equal(req.url,'/index.html');
});
