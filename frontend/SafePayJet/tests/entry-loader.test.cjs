const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {JSDOM}=require('jsdom');
const bootstrap=fs.readFileSync(path.join(__dirname,'../src/index.html'),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
function enter(route,storage=new Map(),blocked=false){
 const dom=new JSDOM('<!doctype html><html></html>');
 const sessionStorage={getItem:key=>{if(blocked)throw Error('Storage blocked');return storage.get(key)||null;},removeItem:key=>storage.delete(key)};
 vm.runInNewContext(bootstrap,{window:{location:{pathname:route},sessionStorage,setTimeout:()=>0},document:dom.window.document,Date});
 const animated=dom.window.document.documentElement.hasAttribute('data-safepay-entry-loader');
 const cloaked=dom.window.document.documentElement.hasAttribute('data-safepay-booting');
 dom.window.close();return {animated,cloaked};
}
test('homepage arrivals show the entry animation',()=>{
 for(const route of ['/','/home','/home/'])assert.equal(enter(route).animated,true);
});
test('ordinary refresh and navbar destinations never request the entry delay',()=>{
 for(const route of ['/login','/register/mobile','/dashboard','/transactions','/beneficiaries','/send-money/beneficiary','/notifications','/profile','/admin/dashboard','/risk/reviews','/audit/logs']){
  assert.deepEqual(enter(route),{animated:false,cloaked:true});
 }
});
test('sign-in handoff animates once for every role and is consumed before refresh',()=>{
 for(const route of ['/dashboard','/admin/dashboard','/risk/reviews','/audit/logs']){
  const storage=new Map([['safepay-entry-transition',route]]);
  assert.equal(enter(route,storage).animated,true);
  assert.equal(enter(route,storage).animated,false);
 }
});
test('a stale sign-in marker does not animate an unrelated page',()=>{
 const storage=new Map([['safepay-entry-transition','/dashboard']]);
 assert.equal(enter('/transactions',storage).animated,false);
 assert.equal(storage.size,0);
});
test('blocked browser storage does not prevent homepage or ordinary entry',()=>{
 assert.equal(enter('/home',new Map(),true).animated,true);
 assert.equal(enter('/transactions',new Map(),true).animated,false);
});
