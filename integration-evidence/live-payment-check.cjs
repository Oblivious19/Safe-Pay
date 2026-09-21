// Bounded live demo mutations on the designated second customer; no direct database writes.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {clients,LosslessNumber,log}=require('./live-api-check.cjs');
const N=x=>new LosslessNumber(String(x)),key=()=>crypto.randomUUID();
const cents=x=>{const [whole,fraction='']=x.split('.');return BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0'));};
const records=[];
function retain(test,data){records.push({test,transactionId:data.transactionId,state:data.state,amount:data.amount,terminalReasonCode:data.terminalReasonCode});fs.writeFileSync(path.join(__dirname,'live-payment-observations.json'),JSON.stringify(records,null,2));}
async function run(){
 const users=await clients(),c=users.get('2470');await c.login();
 try{
  const accountId='1628',beneficiaryId='1160';
  const funds=()=>c.call('PAYMENT_TEST_FUNDS','/accounts/'+accountId+'/balance').then(x=>x.data);
  const before=await funds();assert.equal(before.reservedAmount,'0.00');assert.ok(cents(before.availableBalance)>=10000001n);
  const body=(amount,extra={})=>({sourceAccountId:N(accountId),beneficiaryId:N(beneficiaryId),amount:N(amount),customerReference:'SP-INTEGRATION-20260921',purpose:'Approved demo integration verification',...extra});
  const post=(test,payload,statuses=[201],k=key())=>c.call(test,'/transactions',{method:'POST',body:payload,key:k,statuses});
  const cancel=async(test,id,k=key())=>{const x=(await c.call(test,'/transactions/'+id+'/cancel',{method:'POST',key:k})).data;assert.equal(x.state,'CANCELLED');retain(test,x);return x;};
  await post('AMOUNT_PRECISION_REJECT',body('1.001'),[400]);
  await post('CATEGORY_REQUIRED_REJECT',body('100000.01'),[400]);
  await post('CATEGORY_LOW_BOUNDARY_REJECT',body('100000.00',{category:'MEDICAL'}),[400]);
  await post('OTHERS_PURPOSE_REJECT',body('100000.01',{category:'OTHERS',purpose:''}),[400]);
  const createKey=key(),payload=body('5000.01');
  const created=(await post('MEDIUM_CREATE',payload,[201],createKey)).data;retain('MEDIUM_CREATE',created);assert.equal(created.state,'CREATED');assert.equal(created.reservedAmount,'0.00');
  const replay=(await post('CREATE_SAME_KEY_REPLAY',payload,[201],createKey)).data;assert.equal(replay.transactionId,created.transactionId);
  await post('CREATE_KEY_PAYLOAD_CONFLICT',body('5000.02'),[409],createKey);
  await c.call('AUTHORIZE_UNCONFIRMED_REJECT','/transactions/'+created.transactionId+'/authorize',{method:'POST',key:key(),body:{confirmed:false},statuses:[400]});
  const authKey=key();
  const authorized=(await c.call('MEDIUM_AUTHORIZE','/transactions/'+created.transactionId+'/authorize',{method:'POST',key:authKey,body:{confirmed:true}})).data;retain('MEDIUM_AUTHORIZE',authorized);assert.equal(authorized.state,'PROTECTED');assert.equal(authorized.riskTier,'MEDIUM');
  const detail=(await c.call('PROTECTION_HINTS','/transactions/'+created.transactionId)).data;assert.equal(detail.canCancel,true);assert.ok(Number(detail.protectionRemainingMillis)>0);assert.equal(detail.amount,'5000.01');
  const cancelKey=key();await cancel('PROTECTED_CANCEL',created.transactionId,cancelKey);await cancel('CANCEL_SAME_KEY_REPLAY',created.transactionId,cancelKey);
  const cancelled=(await c.call('CANCELLED_DETAIL','/transactions/'+created.transactionId)).data;assert.equal(cancelled.canCancel,false);
  const high=(await post('HIGH_CREATE',body('25000.01'))).data;retain('HIGH_CREATE',high);
  const highAuth=(await c.call('HIGH_AUTHORIZE','/transactions/'+high.transactionId+'/authorize',{method:'POST',key:key(),body:{confirmed:true}})).data;retain('HIGH_AUTHORIZE',highAuth);assert.equal(highAuth.riskTier,'HIGH');assert.equal(highAuth.state,'PROTECTED');await cancel('HIGH_CANCEL',high.transactionId);
  const categorized=(await post('VERY_HIGH_CATEGORY_CREATE',body('100000.01',{category:'MEDICAL'}))).data;retain('VERY_HIGH_CATEGORY_CREATE',categorized);assert.equal(categorized.category,'MEDICAL');await cancel('CREATED_CANCEL',categorized.transactionId);
  const insufficient=(await post('INSUFFICIENT_FUNDS_CREATE',body((cents(before.availableBalance)+1n).toString().replace(/(\d{2})$/,'.$1'),{category:'MEDICAL'}))).data;retain('INSUFFICIENT_FUNDS_CREATE',insufficient);
  const failed=(await c.call('INSUFFICIENT_FUNDS_AUTHORIZE','/transactions/'+insufficient.transactionId+'/authorize',{method:'POST',key:key(),body:{confirmed:true}})).data;retain('INSUFFICIENT_FUNDS_AUTHORIZE',failed);assert.equal(failed.state,'FAILED');assert.match(failed.terminalReasonCode,/INSUFFICIENT/);
  await c.call('TERMINAL_AUTHORIZE_REJECT','/transactions/'+failed.transactionId+'/authorize',{method:'POST',key:key(),body:{confirmed:true},statuses:[409]});
  const after=await funds();assert.equal(after.currentBalance,before.currentBalance);assert.equal(after.reservedAmount,before.reservedAmount);assert.equal(after.availableBalance,before.availableBalance);
  log([new Date().toISOString(),c.id,'FUNDS_RECONCILED','GET balance assertions','200','unchanged','PASS','—',accountId+': current '+before.currentBalance+' -> '+after.currentBalance+'; reserved '+before.reservedAmount+' -> '+after.reservedAmount]);
  console.log(JSON.stringify({phase:'bounded-payments',result:'PASS',accountId,before,after,records},null,2));
 }finally{await c.logout();}
}
run().catch(e=>{console.error(e.message);process.exitCode=1;});

