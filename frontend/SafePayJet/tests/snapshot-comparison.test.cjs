const test=require('node:test'),assert=require('node:assert/strict');
const {keys,parseSnapshot,compare,report}=require('../../../integration-evidence/compare-snapshots.cjs');
function snapshot(rows={}){return ['SAFEPAY_SNAPSHOT_V1|2026-09-21T08:00:00Z',...Object.keys(keys).flatMap(t=>[...(rows[t]||[]).map(r=>'ROW|'+t+'|'+JSON.stringify(r)),'COUNT|'+t+'|'+(rows[t]||[]).length]),'SAFEPAY_SNAPSHOT_END'].join('\n');}
test('snapshot diff preserves exact long identifiers and amounts',()=>{const before=parseSnapshot(snapshot({ACCOUNT:[{ACCOUNT_ID:'9007199254740993',CURRENT_BALANCE:'9999999999999999.99'}]})),after=parseSnapshot(snapshot({ACCOUNT:[{ACCOUNT_ID:'9007199254740993',CURRENT_BALANCE:'9999999999999998.98'}]}));const changes=compare(before,after);assert.equal(changes.length,1);assert.equal(changes[0].id,'9007199254740993');assert.equal(changes[0].before,'9999999999999999.99');assert.equal(changes[0].after,'9999999999999998.98');assert.match(report(before,after,changes),/Unattributed/);});
test('snapshot parser rejects partial output, Oracle errors and mismatched counts',()=>{assert.throws(()=>parseSnapshot(snapshot().replace('SAFEPAY_SNAPSHOT_END','')),/marker/);assert.throws(()=>parseSnapshot(snapshot()+'\nORA-00942'),/Oracle/);assert.throws(()=>parseSnapshot(snapshot().replace('COUNT|ACCOUNT|0','COUNT|ACCOUNT|2')),/ACCOUNT/);});
test('snapshot diff uses retained review ID and composite role identity',()=>{const before=parseSnapshot(snapshot()),after=parseSnapshot(snapshot({RISK_REVIEW:[{APPROVAL_ID:'51',STATUS:'PENDING'}],USER_ROLE:[{USER_ID:'2498',ROLE_ID:'3'}]}));const changes=compare(before,after);assert.ok(changes.some(c=>c.table==='RISK_REVIEW'&&c.id==='51'));assert.ok(changes.some(c=>c.table==='USER_ROLE'&&c.id==='2498/3'));assert.ok(changes.every(c=>c.kind==='INSERT'));});
test('short hexadecimal snapshot chunks preserve Unicode and whitespace and reject missing parts',()=>{
 const row={ACCOUNT_ID:'9007199254740993',LABEL:'LOW band.\nUnicode ₹ with spaces',CURRENT_BALANCE:'9999999999999999.99'};
 const bytes=Buffer.from(JSON.stringify(row),'utf8'),chunks=[];for(let i=0;i<bytes.length;i+=24)chunks.push(bytes.subarray(i,i+24).toString('hex').toUpperCase());
 const encoded=chunks.map((v,i)=>'ROWHEX|ACCOUNT|'+(i+1)+'|'+v).join('\n')+'\nROWEND|ACCOUNT|'+chunks.length+'|'+bytes.length;
 const input=snapshot().replace('SAFEPAY_SNAPSHOT_V1|','SAFEPAY_SNAPSHOT_V2|').replace('COUNT|ACCOUNT|0',encoded+'\nCOUNT|ACCOUNT|1');
 assert.deepEqual(parseSnapshot(input).tables.ACCOUNT.get(row.ACCOUNT_ID),row);
 assert.throws(()=>parseSnapshot(input.replace(chunks[1],'')),/chunk/);
});
