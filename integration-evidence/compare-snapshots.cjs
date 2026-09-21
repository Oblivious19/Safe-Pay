// Compares user-run, read-only SQL snapshots. No database connection is made.
const fs=require('node:fs'),path=require('node:path');
const keys={APP_ROLE:['ROLE_ID'],APP_USER:['USER_ID'],USER_ROLE:['USER_ID','ROLE_ID'],AUTH_SESSION:['SESSION_ID'],ACCOUNT:['ACCOUNT_ID'],BENEFICIARY:['BENEFICIARY_ID'],RISK_POLICY:['RISK_POLICY_ID'],PROTECTION_POLICY:['PROTECTION_POLICY_ID'],RISK_POLICY_BAND:['RISK_POLICY_BAND_ID'],PAYMENT_TRANSACTION:['TRANSACTION_ID'],TRANSACTION_RISK_FACTOR:['TRANSACTION_RISK_FACTOR_ID'],IDEMPOTENCY_RECORD:['IDEMPOTENCY_RECORD_ID'],PAYMENT_OTP_CHALLENGE:['OTP_CHALLENGE_ID'],RISK_REVIEW:['APPROVAL_ID'],LEDGER_POSTING:['POSTING_ID'],LEDGER_ENTRY:['LEDGER_ENTRY_ID'],TRANSACTION_EXCEPTION:['TRANSACTION_EXCEPTION_ID'],AUDIT_LOG:['AUDIT_LOG_ID'],APP_NOTIFICATION:['NOTIFICATION_ID']};
function parseSnapshot(text){
 if(/\bORA-\d{5}|\bPLS-\d{5}/.test(text))throw Error('Oracle reported an error; this is not a complete baseline.');
 const tables={},counts={},excluded=[];let timestamp='',ended=false,hexTable='',hexParts=[];
 function addRow(table,json){let row;try{row=JSON.parse(json);}catch{throw Error('Incomplete JSON row in '+table+'. Save full Script Output without wrapping or truncation.');}
 const id=keys[table].map(k=>{if(row[k]===undefined||row[k]===null)throw Error('Missing primary key '+table+'.'+k);return String(row[k]);}).join('/');
 tables[table]||=new Map();if(tables[table].has(id))throw Error('Duplicate snapshot identity '+table+'/'+id);tables[table].set(id,row);}
 for(const raw of text.replace(/^\uFEFF/,'').split(/\r?\n/)){const line=raw.trim();
  if(/^SAFEPAY_SNAPSHOT_V[12]\|/.test(line)){if(timestamp)throw Error('More than one snapshot was supplied.');timestamp=line.slice(20);}
  else if(line==='SAFEPAY_SNAPSHOT_END')ended=true;
  else if(line.startsWith('EXCLUDED|'))excluded.push(line.slice(9));
  else if(line.startsWith('COUNT|')){const [,table,count]=line.split('|');if(!keys[table]||counts[table]!==undefined||!/^\d+$/.test(count))throw Error('Invalid or duplicate table count.');counts[table]=Number(count);}
  else if(line.startsWith('ROWHEX|')){
   const [,table,part,hex]=line.split('|');
   if(!keys[table]||(hexTable&&hexTable!==table)||Number(part)!==hexParts.length+1||!/^([0-9A-F]{2})+$/.test(hex))throw Error('Invalid or missing hexadecimal row chunk.');
   hexTable=table;hexParts.push(hex);
  }
  else if(line.startsWith('ROWEND|')){
   const [,table,parts,bytes]=line.split('|');const value=Buffer.from(hexParts.join(''),'hex');
   if(table!==hexTable||Number(parts)!==hexParts.length||Number(bytes)!==value.length)throw Error('Incomplete hexadecimal row.');
   addRow(table,new TextDecoder('utf-8',{fatal:true}).decode(value));hexTable='';hexParts=[];
  }
  else if(line.startsWith('ROW|')){
   const index=line.indexOf('|',4),table=line.slice(4,index);if(!keys[table])throw Error('Unexpected snapshot table '+table);
   addRow(table,line.slice(index+1));
  }
 }
 if(hexParts.length)throw Error('Incomplete hexadecimal row.');
 if(!timestamp||!ended)throw Error('Missing snapshot start/end marker.');
 for(const table of Object.keys(keys)){tables[table]||=new Map();if(counts[table]===undefined||counts[table]!==tables[table].size)throw Error('Missing/incomplete table '+table);}
 return {timestamp,tables,counts,excluded};
}
function compare(before,after){
 const changes=[];
 for(const table of Object.keys(keys)){const a=before.tables[table],b=after.tables[table];for(const id of [...new Set([...a.keys(),...b.keys()])].sort()){
  const left=a.get(id),right=b.get(id);const kind=!left?'INSERT':!right?'DELETE':'UPDATE';
  for(const field of [...new Set([...Object.keys(left||{}),...Object.keys(right||{})])].sort()){const old=left?.[field],value=right?.[field];if(JSON.stringify(old)!==JSON.stringify(value))changes.push({table,id,kind,field,before:old===undefined?'[row absent]':old,after:value===undefined?'[row absent]':value});}
 }}
 return changes;
}
function report(before,after,changes){
 const cell=v=>String(v===null?'NULL':typeof v==='object'?JSON.stringify(v):v).replace(/\|/g,'\\|').replace(/\r?\n/g,'<br>');
 const rows=changes.map(c=>'| '+[c.table,c.id,c.kind,c.field,cell(c.before),cell(c.after),'Unattributed: match to test and correlation evidence'].join(' | ')+' |');
 return '# Database before/after comparison\n\nBefore: '+before.timestamp+'\n\nAfter: '+after.timestamp+'\n\n'+changes.length+' changed field values across '+new Set(changes.map(c=>c.table+'/'+c.id)).size+' rows. All 19 canonical table counts and primary keys were checked. RISK_REVIEW uses retained APPROVAL_ID.\n\nThese are observed differences, not automatic attribution to frontend tests. Schedulers, existing sessions and other users may also change rows. Secret/hash/token/key/LOB fields were excluded by the SQL and cannot be compared. This is not a rollback script.\n\n| Table | Row key | Change | Field | Before | After | Attribution |\n|---|---|---|---|---|---|---|\n'+(rows.join('\n')||'| — | — | No differences | — | — | — | — |')+'\n\nExcluded columns:\n\n'+[...new Set([...before.excluded,...after.excluded])].sort().map(x=>'- '+x).join('\n')+'\n';
}
if(require.main===module){
 const args=process.argv.slice(2);
 try{
  if(args[0]==='--validate'){const value=parseSnapshot(fs.readFileSync(args[1],'utf8'));console.log(JSON.stringify({complete:true,tables:Object.keys(value.tables).length,counts:value.counts,timestamp:value.timestamp},null,2));}
  else{
   if(args.length!==3)throw Error('Usage: node compare-snapshots.cjs before.txt after.txt DATA_CHANGES.md (output must be in this evidence directory).');
   const output=path.resolve(args[2]),root=path.resolve(__dirname);if(!output.startsWith(root+path.sep))throw Error('Output must stay in integration-evidence.');
   const before=parseSnapshot(fs.readFileSync(args[0],'utf8')),after=parseSnapshot(fs.readFileSync(args[1],'utf8')),changes=compare(before,after);
   fs.writeFileSync(output,report(before,after,changes));console.log('Wrote '+changes.length+' field changes to '+output);
  }
 }catch(error){console.error(error.message);process.exitCode=1;}
}
module.exports={keys,parseSnapshot,compare,report};
