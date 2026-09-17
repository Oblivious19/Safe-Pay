const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const ts = require('typescript'), ko = require('knockout');
const read = file => fs.readFileSync(path.join(__dirname, '../src/', file), 'utf8');
class ApiError extends Error { constructor(status,message){super(message);this.status=status;} }
function load(file, imports={}) {
  const m={exports:{}};
  const code=ts.transpileModule(read(file),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{Date,TextEncoder,document:{getElementById:()=>({focus(){}})}})(key=>imports[key],m,m.exports);
  return m.exports;
}
const providers=load('ts/services/demoOnboarding.ts');
function fixture(call=async()=>({})) {
  const calls=[];
  const Model=load('ts/viewModels/register.ts',{'knockout':ko,'../services/apiError':{ApiError},'../services/demoOnboarding':providers,
    '../services/authService':{authService:{register:async data=>{calls.push(data);return call(data);}}},'ojs/ojbutton':{},'ojs/ojinputtext':{}});
  return {model:new Model(),calls};
}
async function details(f) {
  const m=f.model; m.phone('9876543210'); await m.next();
  m.name('Demo Customer'); m.dob('2000-01-01'); m.email('demo@example.test');m.address('Fictional demo address');
}
async function password(f) {
  await details(f); await f.model.next(); f.model.pan('ABCDE1234F'); await f.model.next();
  f.model.password('DemoPass123'); f.model.confirmPassword('DemoPass123');f.model.pin('135790');f.model.confirmPin('135790');
}
test('invalid mobile has no API call',async()=>{const f=fixture(); f.model.phone('123');await f.model.next();assert.match(f.model.error(),/mobile/);assert.equal(f.calls.length,0);});
test('mobile proceeds to details without OTP or API calls',async()=>{const f=fixture();f.model.phone('9876543210');await f.model.next();assert.equal(f.model.step(),'details');assert.equal(f.calls.length,0);assert.doesNotMatch(read('ts/views/register.html'),/reg-otp|Resend OTP|one-time-code/);});
test('legacy verify route restarts safely without claiming verification',()=>{const f=fixture();f.model.parametersChanged({step:'verify'});assert.equal(f.model.step(),'mobile');});
test('OTP expiration, resend cooldown, attempt limit and send limit',()=>{
  let now=100; const p=new providers.DemoOtpProvider(()=>now);
  p.send();assert.throws(()=>p.send(),/wait/);
  for(let i=0;i<5;i++) assert.throws(()=>p.verify('000000'),/Invalid/);
  assert.throws(()=>p.verify(providers.demoOtpConfig.code),/Too many/);
  now+=180000;assert.throws(()=>p.verify(providers.demoOtpConfig.code),/expired/);
  p.send();p.verify(providers.demoOtpConfig.code);assert.throws(()=>p.verify(providers.demoOtpConfig.code),/expired/);
  now+=30000;p.send();now+=30000;assert.throws(()=>p.send(),/limit/);
});
for(const [field,value] of [['name',''],['name','123'],['email','bad'],['dob','2020-02-31'],['address','x']])
test('invalid personal '+field,async()=>{const f=fixture();await details(f);f.model[field](value);await f.model.next();assert.equal(f.model.step(),'details');assert.ok(f.model.error());assert.equal(f.calls.length,0);});
test('PAN format produces explicitly simulated result',()=>{const p=new providers.DemoKycProvider();assert.throws(()=>p.verify('INVALID'));assert.equal(p.verify('ABCDE1234F'),'KYC Verified (Simulated)');});
test('invalid PAN cannot reach password or POST',async()=>{const f=fixture();await details(f);await f.model.next();f.model.pan('INVALID');await f.model.next();assert.equal(f.model.step(),'kyc');assert.equal(f.calls.length,0);});
for(const [field,value] of [['password','short'],['confirmPassword','different'],['pin','123'],['confirmPin','000000']])
test('invalid secret '+field+' does not POST',async()=>{const f=fixture();await password(f);f.model[field](value);await f.model.next();assert.ok(f.model.error());assert.equal(f.calls.length,0);});
test('registration submits only supported fields and clears all temporary personal data',async()=>{
  const f=fixture();await password(f);await f.model.next();assert.equal(f.calls.length,1);
  assert.deepEqual(Object.keys(f.calls[0]).sort(),['email','name','password','phone','pin']);assert.equal(f.model.step(),'success');
  for(const field of ['password','confirmPassword','pin','confirmPin','pan','dob','address','name','email','phone']) assert.equal(f.model[field](),'');
});
for(const message of ['Email is already registered','Phone number is already registered'])
test('409 '+message,async()=>{const f=fixture(async()=>{throw new ApiError(409,message)});await password(f);await f.model.next();assert.equal(f.model.error(),message);assert.equal(f.model.password(),'');assert.equal(f.model.pin(),'');});
test('500 has generic message',async()=>{const f=fixture(async()=>{throw new ApiError(500,'Oracle secret')});await password(f);await f.model.next();assert.match(f.model.error(),/couldn’t create/);assert.doesNotMatch(f.model.error(),/Oracle/);});
test('duplicate submission blocked and late response ignored after disconnect',async()=>{
  let finish;const f=fixture(()=>new Promise(r=>finish=r));await password(f);const pending=f.model.next();await f.model.next();assert.equal(f.calls.length,1);assert.equal(f.model.busy(),true);f.model.disconnected();finish({});await pending;assert.notEqual(f.model.step(),'success');assert.equal(f.model.pin(),'');
});
test('refresh of later route restarts onboarding',()=>{const f=fixture();f.model.parametersChanged({step:'password'});assert.equal(f.model.step(),'mobile');});
test('payment PIN is created with the account and cannot be skipped',async()=>{
  const f=fixture();await password(f);f.model.pin('');f.model.confirmPin('');await f.model.next();
  assert.equal(f.model.step(),'password');assert.match(f.model.error(),/6-digit payment PIN/);assert.equal(f.calls.length,0);
  f.model.pin('135790');f.model.confirmPin('135790');await f.model.next();
  assert.equal(f.model.step(),'success');assert.equal(f.calls[0].pin,'135790');
});
test('simulated wording visible, no browser persistence or document upload',()=>{
  const html=read('ts/views/register.html');assert.match(html,/KYC Verified \(Simulated\)/);assert.match(html,/no government KYC service was contacted/);
  assert.doesNotMatch(html,/type="file"/);assert.doesNotMatch(read('ts/viewModels/register.ts'),/localStorage|sessionStorage/);
  assert.doesNotMatch(html,new RegExp(providers.demoOtpConfig.code));
});
