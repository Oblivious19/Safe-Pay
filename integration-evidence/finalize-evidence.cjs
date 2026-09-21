// Offline evidence reconciliation only. Reads validated snapshots; opens no DB/API connection.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {parseSnapshot,compare,report}=require('./compare-snapshots.cjs');
const read=name=>parseSnapshot(fs.readFileSync(path.join(__dirname,name),'utf8'));
const before=read('baseline-before-v2.txt'),after=read('baseline-after-v2.txt'),changes=compare(before,after);
const users=new Set(['2468','2470','2498','2499','2500']),payments=new Set(['1554','1555','1556','1557','1558']);
assert.equal(changes.filter(c=>c.kind==='DELETE').length,0,'Unexpected deleted row');
for(const c of changes.filter(c=>c.kind==='UPDATE')){
 assert.ok(c.table==='APP_USER'&&users.has(c.id)&&['LAST_SUCCESSFUL_LOGIN_AT','UPDATED_AT','VERSION_NO'].includes(c.field)||
 c.table==='ACCOUNT'&&['1626','1628'].includes(c.id)&&['AVAILABLE_BALANCE','RESERVED_AMOUNT','UPDATED_AT','VERSION_NO'].includes(c.field),'Unexpected existing-row change: '+c.table+'/'+c.id+'/'+c.field);
}
for(const [id,row] of before.tables.ACCOUNT)assert.equal(after.tables.ACCOUNT.get(id).CURRENT_BALANCE,row.CURRENT_BALANCE,'Current balance changed: '+id);
assert.equal(after.tables.ACCOUNT.get('1626').RESERVED_AMOUNT,'180001');
assert.equal(after.tables.ACCOUNT.get('1626').AVAILABLE_BALANCE,'169999');
assert.equal(after.tables.ACCOUNT.get('1628').RESERVED_AMOUNT,'0');
assert.equal(after.tables.BENEFICIARY.get('1751').STATUS,'DISABLED');
for(const table of ['APP_ROLE','USER_ROLE','LEDGER_ENTRY','LEDGER_POSTING','PAYMENT_OTP_CHALLENGE','PROTECTION_POLICY','RISK_POLICY','RISK_POLICY_BAND','RISK_REVIEW','TRANSACTION_EXCEPTION'])assert.equal(changes.filter(c=>c.table===table).length,0,'Unexpected protected-table difference: '+table);
const added=(table)=>[...after.tables[table]].filter(([id])=>!before.tables[table].has(id));
for(const [,r] of added('AUTH_SESSION')){assert.ok(users.has(r.USER_ID));assert.ok(r.REVOKED_AT,'A test session is still active');}
for(const [,r] of added('PAYMENT_TRANSACTION'))assert.ok(payments.has(r.TRANSACTION_ID)&&['2468','2470'].includes(r.CUSTOMER_USER_ID));
for(const [,r] of added('APP_NOTIFICATION'))assert.ok(payments.has(r.TRANSACTION_ID)&&['2468','2470'].includes(r.RECIPIENT_USER_ID));
for(const [,r] of added('IDEMPOTENCY_RECORD'))assert.ok(payments.has(r.TRANSACTION_ID)&&['2468','2470'].includes(r.USER_ID));
for(const [,r] of added('TRANSACTION_RISK_FACTOR'))assert.ok(payments.has(r.TRANSACTION_ID));
for(const [,r] of added('AUDIT_LOG')){assert.ok(r.ACTOR_USER_ID===null||users.has(r.ACTOR_USER_ID));assert.ok(r.TRANSACTION_ID===null||payments.has(r.TRANSACTION_ID));}
const apiText=fs.readFileSync(path.join(__dirname,'LIVE_API_TESTS.md'),'utf8');
function attribution(c){
 const row=after.tables[c.table].get(c.id);
 if(c.table==='ACCOUNT')return c.id==='1626'?'Browser payment 1558: 1.00 reserved, RELEASED; current balance unchanged':'API protected/cancel tests 1554/1555: version/timestamp changed; final funds unchanged';
 if(c.table==='APP_USER')return 'Approved sign-ins for user '+c.id+'; login timestamp/version only';
 if(c.table==='BENEFICIARY')return 'Browser beneficiary test: created then disabled/reactivated/disabled; final DISABLED';
 if(c.table==='PAYMENT_TRANSACTION')return (c.id==='1558'?'LIVE_BROWSER_TESTS.md':'LIVE_API_TESTS.md')+': payment '+c.id+' / '+row.STATE;
 if(c.table==='APP_NOTIFICATION')return 'Notification from test payment '+row.TRANSACTION_ID+(row.READ_AT?'; marked read in browser':'');
 if(c.table==='AUTH_SESSION')return 'Sign-in/refresh for approved user '+row.USER_ID+'; revoked by rotation/logout';
 if(c.table==='IDEMPOTENCY_RECORD')return row.OPERATION_CODE+' payment '+row.TRANSACTION_ID+'; completed original-key evidence';
 if(c.table==='TRANSACTION_RISK_FACTOR')return 'Authorization of test payment '+row.TRANSACTION_ID+'; '+row.RESULTING_TIER+' amount band';
 if(c.table==='AUDIT_LOG'){
  if(row.ACTION_CODE==='AUTH_REFRESH_FAILED')return 'Anonymous browser restore without a valid cookie; temporal attribution to sign-out/startup checks, not a password failure';
  return row.ACTION_CODE+'; '+(row.TRANSACTION_ID?'test payment '+row.TRANSACTION_ID:'approved actor '+row.ACTOR_USER_ID)+(row.CORRELATION_ID&&apiText.includes(row.CORRELATION_ID)?'; correlation matched LIVE_API_TESTS.md':'; browser/action window evidence');
 }
 throw Error('Unattributed table '+c.table);
}
let detail=report(before,after,changes),i=0;
detail=detail.replaceAll('Unattributed: match to test and correlation evidence',()=>attribution(changes[i++]).replace(/\|/g,'/'));assert.equal(i,changes.length);
const summary=[
'# Database change summary',
'',
'Before: '+before.timestamp+'. After: '+after.timestamp+'. Both consistent read-only snapshots validated all 19 tables.',
'',
'1474 changed non-secret field values across 107 rows: 100 inserted, 7 updated, zero deleted. Inserts include every captured field of each new row; this is not 1474 database operations. No direct SQL DML, DDL, grant or cleanup was executed.',
'',
'All 50 current account balances are unchanged. Priya account 1626: current 350000.00 unchanged; reserved 180000.00 -> 180001.00; available 170000.00 -> 169999.00. The extra 1.00 belongs to browser test payment 1558, which is RELEASED while the settlement processor is disabled. Kavya account 1628 ends at current 200000.00, reserved 0.00 and available 200000.00, exactly its initial funds.',
'',
'| Table | Before rows | After rows | Changed rows | Attribution |',
'|---|---:|---:|---:|---|',
...Object.keys(before.tables).map(table=>{
 const rows=new Set(changes.filter(c=>c.table===table).map(c=>c.id));
 return '| '+[table,before.counts[table],after.counts[table],rows.size,rows.size?'See field-level report and action ledgers':'No included-column differences'].join(' | ')+' |';
}),
'',
'New payments: 1554 (5000.01 MEDIUM) and 1555 (25000.01 HIGH) were protected then cancelled; 1556 (100000.01 MEDICAL) was cancelled before authorization; 1557 (200000.01 MEDICAL) failed with INSUFFICIENT_AVAILABLE_BALANCE; 1558 (1.00 LOW) is RELEASED and reserved. No settlement/OTP/review progression was forced.',
'',
'Beneficiary 1751 is a test-only UPI recipient and ends DISABLED. All pre-existing beneficiaries and payment rows are unchanged. All 20 new session rows are revoked. The five designated users changed only successful-login timestamps, update timestamps and optimistic-lock versions. User roles/security status are unchanged.',
'',
'The 51 new audit rows comprise 10 successful logins, 10 refresh rotations, 10 logouts, 5 anonymous failed restore attempts, 4 intended role-denial checks and 12 payment events. Failed anonymous restore entries line up with browser startup/sign-out, do not identify a user and are a temporal attribution rather than a demonstrated request-correlation match. No unexplained financial or protected-table drift was found in included fields.',
'',
'Raw snapshots exclude secret/hash/token/key/LOB fields. Assertions and differences cannot establish equality of excluded fields. Local snapshots are ignored by Git. No cleanup was attempted.',
'',
'Detailed initial/end values and per-row attribution: DATA_CHANGES.md. Executed API requests: LIVE_API_TESTS.md. Browser actions: LIVE_BROWSER_TESTS.md. Deferred checks: PENDING_USER_TESTS.txt.',
''
].join('\n');
fs.writeFileSync(path.join(__dirname,'DATA_CHANGES.md'),detail);
fs.writeFileSync(path.join(__dirname,'DATA_CHANGE_SUMMARY.md'),summary);
console.log(JSON.stringify({result:'PASS',tables:19,fields:changes.length,rows:new Set(changes.map(c=>c.table+'/'+c.id)).size,allCurrentBalancesUnchanged:true,newSessionRows:added('AUTH_SESSION').length,activeTestSessions:0}));
