const test = require('node:test');
const assert = require('node:assert/strict');
const money = require('../.test-build/contracts/utils/money.js');
const wire = require('../.test-build/contracts/services/wireCodec.js');
const api = require('../.test-build/contracts/services/apiClient.js');
const {accounts} = require('../.test-build/contracts/services/accountService.js');
const {beneficiaries} = require('../.test-build/contracts/services/beneficiaryService.js');
const state = require('../.test-build/contracts/utils/paymentState.js');

test('phase 1: long identifiers and decimal lexemes survive decoding', () => {
  const value = wire.decode('{"id":9007199254740993,"amount":9999999999999999.99,"page":0,"totalPages":2,"totalElements":9007199254740993}');
  assert.equal(value.id,'9007199254740993'); assert.equal(value.amount,'9999999999999999.99'); assert.equal(value.totalElements,'9007199254740993'); assert.equal(value.page,0); assert.equal(value.totalPages,2);
});
test('phase 1: outbound ID and money are numeric JSON without precision loss', () => {
  assert.equal(wire.encode({sourceAccountId:wire.wireId('9007199254740993'),amount:wire.wireAmount('9999999999999999.99')}), '{"sourceAccountId":9007199254740993,"amount":9999999999999999.99}');
});
test('phase 1: malformed and overflowing IDs are rejected', () => { ['0','01','1e3','9223372036854775808'].forEach(x=>assert.throws(()=>money.validId(x))); });
test('phase 3: exact category boundary uses cents', () => { assert.equal(money.minor('100000.00'),10000000n); assert.equal(money.minor('100000.01'),10000001n); });
test('phase 3: invalid money is rejected without rounding', () => { ['0.99','-1','1e3','5,000','5000.001','10000000000000000'].forEach(x=>assert.throws(()=>money.paymentAmount(x))); });
test('phase 2: formatting preserves cents and unavailable is not zero', () => { assert.equal(money.rupees('0.10'),'₹0.10'); assert.equal(money.rupees(null),'Unavailable'); assert.equal(money.paymentAmount('1'),'1.00'); });

async function withFetch(action, handler) {
  const original = global.fetch; global.fetch = handler;
  api.configureSession(async()=> 'first-token', async()=> 'renewed-token', ()=> '7');
  try { await action(); } finally { global.fetch = original; }
}
test('phase 1: one 401 retry preserves mutation key and exact body', async () => {
  const calls=[];
  await withFetch(async()=> { const result=await api.request('/transactions',{method:'POST',key:'same-logical-key',body:{amount:wire.wireAmount('5000.01')}}); assert.equal(result.transactionId,'101'); }, async(url,options)=> { calls.push(options); return calls.length===1 ? new Response('{"detail":"Expired"}',{status:401}) : new Response('{"transactionId":"101"}'); });
  assert.equal(calls.length,2); assert.equal(calls[0].body,calls[1].body); assert.equal(calls[1].headers['Idempotency-Key'],'same-logical-key'); assert.equal(calls[1].headers.Authorization,'Bearer renewed-token'); assert.notEqual(calls[0].headers['X-Correlation-ID'],calls[1].headers['X-Correlation-ID']);
});
test('phase 3: lost mutation response is uncertain and not automatically retried', async()=> { let calls=0; await withFetch(async()=> assert.rejects(api.request('/transactions',{method:'POST',key:'one',body:{}}), error=>error.uncertain===true),async()=> { calls++; throw new TypeError('Disconnected'); }); assert.equal(calls,1); });
test('phase 1: 403 remains a permission error rather than a refresh loop',async()=> { let calls=0; await withFetch(async()=> assert.rejects(api.request('/accounts'),error=>error.status===403),async()=> { calls++; return new Response('{"detail":"Forbidden","errorCode":"ACCESS_DENIED"}',{status:403}); }); assert.equal(calls,1); });
test('phase 2: balance retrieval uses its own endpoint and rejects wrong account',async()=> { await withFetch(async()=>assert.rejects(accounts.balance('70'),/Unexpected account/),async(url)=> { assert.equal(url,'http://localhost:8080/api/v1/accounts/70/balance'); return new Response('{"accountId":"71","currency":"INR","currentBalance":"100.00","reservedAmount":"0.00","availableBalance":"100.00"}'); }); });
test('phase 2: beneficiary status uses PATCH and the canonical status field',async()=> { await withFetch(async()=>beneficiaries.status('700','DISABLED'),async(url,options)=> { assert.equal(url,'http://localhost:8080/api/v1/beneficiaries/700/status'); assert.equal(options.method,'PATCH'); assert.equal(options.body,'{"status":"DISABLED"}'); return new Response('{"beneficiaryId":"700","status":"DISABLED"}'); }); });
test('phase 3: every canonical state has a distinct description; unknown fails closed',()=> { assert.equal(state.states.length,10); for(const value of state.states) assert.ok(state.descriptions[value]); assert.equal(state.knownState('REJECTED'),false); assert.match(state.description('FUTURE'),/disabled/); assert.match(state.description('CREATED'),/No funds/); assert.match(state.description('RELEASED'),/pending/); });
