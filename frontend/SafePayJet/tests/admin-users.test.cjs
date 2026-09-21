const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),ko=require('knockout');
class ApiError extends Error {constructor(status,message){super(message);this.status=status;}}
function load(file,imports,extra={}) {
 const m={exports:{}};const code=ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts',file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
 vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{BigInt,crypto:{randomUUID:()=> 'unique-request-00001'},...extra})(key=>imports[key],m,m.exports);return m.exports;
}
const user={userId:113,name:'Test',email:'test@example.test',role:'CUSTOMER',status:'ACTIVE'};
const account={accountId:77,accountNumber:'500000000001',accountType:'SAVINGS',status:'ACTIVE',balance:'5000.00'};
function fixture(service={}) {
 const calls=[],redirects=[];
 const modelClass=load('viewModels/adminUsers.ts',{'knockout':ko,'../services/apiError':{ApiError},'../services/adminUserService':{
 adminUserService:{users:async()=>service.users?service.users():[user],accounts:async()=>service.account?[await service.account()]:[account],
 credit:async(...args)=>{calls.push(args);return service.credit?service.credit():receipt;}}}}, {window:{location:{replace:url=>redirects.push(url)}}}).AdminUsersModel;
 return {model:new modelClass(),calls,redirects};
}
test('users load and account selection uses real service data',async()=>{const f=fixture();await f.model.load();assert.equal(f.model.users()[0],user);await f.model.select(user);assert.equal(f.model.account(),account);assert.equal(f.model.masked(account.accountNumber),'•••• 0001');});
test('empty users and no account states',async()=>{const f=fixture({users:async()=>[],account:async()=>{throw new ApiError(404,'No account')}});await f.model.load();assert.equal(f.model.users().length,0);await f.model.select(user);assert.equal(f.model.noAccount(),true);assert.equal(f.model.account(),null);});
for(const status of [401,403]) test('access failure '+status,async()=>{const f=fixture({users:async()=>{throw new ApiError(status,'Denied')}});await f.model.load();if(status===401)assert.deepEqual(f.redirects,['/admin/login']);else assert.equal(f.model.forbidden(),true);assert.equal(f.model.users().length,0);});
test('full INR formatting retains large exact values',()=>{const f=fixture();assert.equal(f.model.money('9999999999999999.99'),'₹9,99,99,99,99,99,99,999.99');assert.equal(f.model.money('450000.00'),'₹4,50,000.00');});
