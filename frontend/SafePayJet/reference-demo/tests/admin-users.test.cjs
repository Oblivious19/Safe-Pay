const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),ko=require('knockout');
class ApiError extends Error {constructor(status,message){super(message);this.status=status;}}
function load(file,imports,extra={}) {
 const m={exports:{}};const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts',file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
 vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{BigInt,crypto:{randomUUID:()=> 'unique-request-00001'},...extra})(key=>imports[key],m,m.exports);return m.exports;
}
const user={userId:113,name:'Test',email:'test@example.test',role:'CUSTOMER',status:'ACTIVE'};
const account={accountId:77,accountNumber:'500000000001',accountType:'SAVINGS',status:'ACTIVE',balance:'5000.00'};
const receipt={accountId:77,amount:'100.25',balanceBefore:'5000.00',balanceAfter:'5100.25',createdAt:'2026-09-15T12:00:00',description:'Simulated bank interest'};
// Real-backend behaviour: the demo ledger is inactive, so every call must use the transport.
const live={'./demoSession':{isDemoActive:()=>false,demoUsers:()=>[],demoUserAccount:()=>({}),demoCreditAccount:()=>({})}};
const validation=load('services/adminUserService.ts',{'./apiClient':{apiClient:{}},'./apiError':{ApiError},...live});
function fixture(service={}) {
 const calls=[],redirects=[];
 const modelClass=load('viewModels/adminUsers.ts',{'knockout':ko,'../services/apiError':{ApiError},'../services/adminUserService':{
 validCredit:validation.validCredit,adminUserService:{users:async()=>service.users?service.users():[user],account:async()=>service.account?service.account():account,
 credit:async(...args)=>{calls.push(args);return service.credit?service.credit():receipt;}}}}, {window:{location:{replace:url=>redirects.push(url)}}}).AdminUsersModel;
 return {model:new modelClass(),calls,redirects};
}
test('users load and account selection uses real service data',async()=>{const f=fixture();await f.model.load();assert.equal(f.model.users()[0],user);await f.model.select(user);assert.equal(f.model.account(),account);assert.equal(f.model.masked(account.accountNumber),'•••• 0001');});
test('empty users and no account states',async()=>{const f=fixture({users:async()=>[],account:async()=>{throw new ApiError(404,'No account')}});await f.model.load();assert.equal(f.model.users().length,0);await f.model.select(user);assert.equal(f.model.noAccount(),true);assert.equal(f.model.account(),null);});
for(const amount of ['0','-1','1.234','1e3','10000000000000000','']) test('local invalid credit '+amount,async()=>{const f=fixture();await f.model.select(user);f.model.amount(amount);f.model.confirmed(true);await f.model.submit();assert.equal(f.calls.length,0);});
test('confirmation required then success updates balance',async()=>{const f=fixture();await f.model.select(user);f.model.amount('100.25');await f.model.submit();assert.equal(f.calls.length,0);f.model.confirmed(true);await f.model.submit();assert.equal(f.calls.length,1);assert.equal(f.model.account().balance,'5100.25');assert.equal(f.model.receipt(),receipt);assert.equal(f.model.pending(),false);});
test('unknown response blocks resubmission until manually checked',async()=>{const f=fixture({credit:async()=>{throw new ApiError(0,'Network');}});await f.model.select(user);f.model.amount('100.25');f.model.confirmed(true);await f.model.submit();assert.equal(f.model.pending(),true);f.model.amount('999');await f.model.select({...user,userId:999});await f.model.submit();assert.equal(f.calls.length,1);assert.match(f.model.error(),/audit/);assert.equal(f.model.selected().userId,113);});
test('busy submission does not double POST',async()=>{let finish;const f=fixture({credit:()=>new Promise(r=>finish=r)});await f.model.select(user);f.model.amount('100.25');f.model.confirmed(true);const pending=f.model.submit();await f.model.submit();assert.equal(f.calls.length,1);finish(receipt);await pending;});
for(const status of [401,403]) test('access failure '+status,async()=>{const f=fixture({users:async()=>{throw new ApiError(status,'Denied')}});await f.model.load();if(status===401)assert.deepEqual(f.redirects,['/admin/login']);else assert.equal(f.model.forbidden(),true);assert.equal(f.model.users().length,0);});
test('full INR formatting retains large exact values',()=>{const f=fixture();assert.equal(f.model.money('9999999999999999.99'),'₹9,99,99,99,99,99,99,999.99');assert.equal(f.model.money('450000.00'),'₹4,50,000.00');});
test('credit service uses existing transport with JSON and CSRF only',async()=>{const calls=[];const service=load('services/adminUserService.ts',{'./apiError':{ApiError},'./apiClient':{apiClient:{request:async(...args)=>{calls.push(args);return receipt;}}},...live}).adminUserService;await service.credit(77,'100.25');assert.equal(calls[0][0],'/api/admin/accounts/77/interest-credits');assert.equal(calls[0][1].csrf,true);assert.equal(calls[0][1].idempotencyKey,undefined);assert.equal(calls[0][1].body.amount,'100.25');});
