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
 adminUserService:{searchUsers:async(...args)=>{calls.push(args);return service.users?service.users(...args):[user];},accounts:async()=>service.account?[await service.account()]:[account],
 credit:async(...args)=>{calls.push(args);return service.credit?service.credit():receipt;}}}}, {window:{location:{replace:url=>redirects.push(url)}}}).AdminUsersModel;
 const model=new modelClass();model.searchQuery('113');return {model,calls,redirects};
}
test('users load and account selection uses real service data',async()=>{const f=fixture();await f.model.load();assert.equal(f.model.users()[0],user);await f.model.select(user);assert.equal(f.model.account(),account);assert.equal(f.model.masked(account.accountNumber),'•••• 0001');});
test('empty users and no account states',async()=>{const f=fixture({users:async()=>[],account:async()=>{throw new ApiError(404,'No account')}});await f.model.load();assert.equal(f.model.users().length,0);await f.model.select(user);assert.equal(f.model.noAccount(),true);assert.equal(f.model.account(),null);});
for(const status of [401,403]) test('access failure '+status,async()=>{const f=fixture({users:async()=>{throw new ApiError(status,'Denied')}});await f.model.load();if(status===401)assert.deepEqual(f.redirects,['/admin/login']);else assert.equal(f.model.forbidden(),true);assert.equal(f.model.users().length,0);});
test('full INR formatting retains large exact values',()=>{const f=fixture();assert.equal(f.model.money('9999999999999999.99'),'₹9,99,99,99,99,99,99,999.99');assert.equal(f.model.money('450000.00'),'₹4,50,000.00');});

test('initial state is empty and invalid searches make no request',async()=>{
 const f=fixture();assert.equal(f.model.users().length,0);assert.equal(f.model.searched(),false);assert.equal(f.calls.length,0);
 for(const value of ['', ' ', '0', '-1', '1e3', '1.5', '9007199254740992']) {
  f.model.searchQuery(value);await f.model.load();assert.ok(f.model.error());assert.equal(f.calls.length,0);
 }
});
test('email normalization, successful no-match and clearing selection',async()=>{
 const f=fixture({users:async()=>[]});await f.model.select(user);
 f.model.searchBy('email');f.model.searchQuery(' TEST@EXAMPLE.TEST ');await f.model.load();
 assert.deepEqual(f.calls[0],['email','test@example.test']);assert.equal(f.model.searched(),true);
 assert.equal(f.model.selected(),null);assert.equal(f.model.account(),null);assert.equal(f.model.error(),'');
 f.model.resetSearch();assert.equal(f.model.searched(),false);assert.equal(f.model.searchQuery(),'');
});
test('cleared or disconnected models ignore late search responses',async()=>{
 let resolve;const f=fixture({users:()=>new Promise(r=>{resolve=r;})});
 const first=f.model.load();f.model.resetSearch();resolve([user]);await first;assert.equal(f.model.users().length,0);
 f.model.searchQuery('113');const second=f.model.load();f.model.disconnected();resolve([user]);await second;
 assert.equal(f.model.users().length,0);assert.equal(f.model.loading(),false);
});
test('failed search does not pretend to be no match',async()=>{
 const f=fixture({users:async()=>{throw new ApiError(500,'Down');}});
 await f.model.load();assert.ok(f.model.error());assert.equal(f.model.searched(),false);
});
test('admin route does not fetch the full directory on entry',()=>{
 const source=fs.readFileSync(path.join(__dirname,'../src/ts/viewModels/admin.ts'),'utf8');
 const branch=source.split('this.context.params?.page === "users"')[1].split('this.context.params?.page === "holds"')[0];
 assert.doesNotMatch(branch,/model\.load/);
 const markup=fs.readFileSync(path.join(__dirname,'../src/ts/views/admin.html'),'utf8').split('<!-- ko if: holdsPage -->')[0];
 assert.match(markup,/>User ID</);assert.match(markup,/No match found/);
 for(const field of ['accountId','userId','accountNumber','accountType','status','balance','createdAt']) assert.ok(markup.includes('account().'+field));
 assert.doesNotMatch(markup,/updatedAt/);
});
