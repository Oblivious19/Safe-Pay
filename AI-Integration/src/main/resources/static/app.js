import {money,safeAdminUrl} from './format.mjs';
const $=id=>document.getElementById(id);
let csrf='',active=false,selected=null,report=null,generation=0,timer=null,busy=false,requestRevision=0,queueRevision=0;
function message(text=''){$('message').textContent=text;$('message').hidden=!text;}
function busyState(value){busy=value;$('analyze').disabled=value;$('refresh').disabled=value;$('download').disabled=value;$('pending').querySelectorAll('button').forEach(b=>b.disabled=value);}
function clearReview(){report=null;selected=null;requestRevision++;$('reviewPanel').hidden=true;$('empty').hidden=false;$('facts').replaceChildren();$('focus').replaceChildren();$('history').replaceChildren();$('paymentDetails').replaceChildren();$('checks').replaceChildren();}
function clearSession(){active=false;generation++;queueRevision++;clearTimeout(timer);timer=null;busyState(false);clearReview();$('pending').replaceChildren();$('signedIn').textContent='';$('workspace').hidden=true;$('loginPanel').hidden=false;$('logout').hidden=true;$('password').value='';}
async function api(path,options={}){
  const headers={'Accept':'application/json',...options.headers};if(options.method==='POST')headers['X-CSRF-TOKEN']=csrf;
  if(options.body!==undefined){headers['Content-Type']='application/json';options.body=JSON.stringify(options.body);}
  let response;try{response=await fetch('api/'+path,{credentials:'same-origin',cache:'no-store',...options,headers});}catch{throw new Error('Connection to the assistant was lost. Refresh and try again.');}
  let body;try{body=await response.json();}catch{throw new Error('The assistant returned an unexpected response.');}
  if(!response.ok){if((response.status===401 || response.status===403) && path!=='login'){clearSession();void loadSession();}const error=new Error(body.message||'Request failed');error.status=response.status;throw error;}
  return body;
}
async function loadSession(){
  try{const session=await api('session');csrf=session.csrf;
    $('adminLink').href=safeAdminUrl(session.safePayAdminUrl);$('approvalLink').href=safeAdminUrl(session.safePayAdminUrl);
    if(session.signedIn){active=true;$('loginPanel').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;$('signedIn').textContent='Signed in as '+session.identity.name;void modelStatus();await refresh();}
  }catch(error){message(error.message);}
}
async function modelStatus(){const revision=generation;try{const status=await api('model');if(active && revision===generation)$('modelStatus').textContent=status.model+' · '+status.message;}catch(error){if(revision===generation)$('modelStatus').textContent=error.message;}}
async function refresh(){
  if(!active || busy)return;clearTimeout(timer);const version=generation,queue=++queueRevision;
  $('refresh').disabled=true;
  try{const rows=await api('pending');if(!active || version!==generation || queue!==queueRevision)return;
    $('pending').replaceChildren();$('count').textContent=String(rows.length);
    if(selected!==null && !rows.some(row=>row.transactionId===selected)){clearReview();message('The selected payment is no longer awaiting approval.');}
    if(!rows.length){const p=document.createElement('p');p.textContent='No payments awaiting approval.';$('pending').append(p);}
    rows.forEach(row=>{const b=document.createElement('button');b.className='pending-item'+(row.transactionId===selected?' selected':'');b.disabled=busy;b.setAttribute('aria-pressed',String(row.transactionId===selected));
      for(const [tag,text] of [['strong',money(row.amount)],['span',row.transactionRef],['span',row.customerName+' → '+row.beneficiaryName]]){const element=document.createElement(tag);element.textContent=text;b.append(element);}
      b.addEventListener('click',()=>select(row.transactionId));$('pending').append(b);
    });
  }catch(error){if(version===generation)message(error.message);}
  finally{if(active && version===generation && queue===queueRevision){$('refresh').disabled=busy;timer=setTimeout(refresh,30000);}}
}
async function select(id){if(busy)return;clearReview();selected=id;await getReport(false);}
function renderFacts(container,facts){container.replaceChildren();facts.forEach(fact=>{const div=document.createElement('div');div.className='fact';const label=document.createElement('strong');label.textContent=fact.label;const text=document.createElement('p');text.textContent=fact.text;div.append(label,text);container.append(div);});}
function show(result){report=result;const evidence=result.evidence;$('empty').hidden=true;$('reviewPanel').hidden=false;
  $('reference').textContent=evidence.reference;$('amount').textContent=money(evidence.amount);$('paymentDetails').replaceChildren();
  [['Beneficiary',evidence.beneficiaryName+' · '+evidence.maskedBeneficiary],['Source account ID',evidence.accountId],['Purpose',evidence.purpose||'Not supplied'],['Payment created',evidence.paymentCreatedAt+' (database local time)'],['Saved risk tier',evidence.riskTier],['Saved risk reason',evidence.riskReason]].forEach(([label,value])=>{const dt=document.createElement('dt');dt.textContent=label;const dd=document.createElement('dd');dd.textContent=String(value);$('paymentDetails').append(dt,dd);});
  $('analysisMessage').textContent=result.message;$('scope').textContent=evidence.historyScope;renderFacts($('facts'),evidence.facts);
  $('aiPanel').hidden=result.mode!=='AI_ASSISTED';renderFacts($('focus'),result.focus);$('checks').replaceChildren();result.suggestedChecks.forEach(text=>{const li=document.createElement('li');li.textContent=text;$('checks').append(li);});
  $('history').replaceChildren();evidence.recentSettledPayments.forEach(row=>{const tr=document.createElement('tr');for(const text of [row.reference,money(row.amount),row.settledAt]){const td=document.createElement('td');td.textContent=text;tr.append(td);}$('history').append(tr);});
  $('noHistory').hidden=!!evidence.recentSettledPayments.length;$('reportMeta').textContent='Report '+result.reportId+' · Evidence checked '+new Date(evidence.observedAt).toLocaleString()+' · Snapshot only; confirm current state in SafePay.';
}
async function getReport(useAi){
  if(selected===null || busy || !active)return;const id=selected,version=generation,revision=++requestRevision;clearTimeout(timer);queueRevision++;busyState(true);message();
  if(useAi)$('analysisMessage').textContent='Local AI is reviewing verified evidence. This may take up to 90 seconds…';
  try{const result=await api('payments/'+id+(useAi?'/review':'/evidence'),useAi?{method:'POST'}:{});if(active && version===generation && revision===requestRevision && selected===id)show(result);}
  catch(error){if(version===generation){message(error.message);if(error.status===409)clearReview();}}
  finally{if(active && version===generation){busyState(false);await refresh();}}
}
$('loginForm').addEventListener('submit',async event=>{event.preventDefault();const value=$('identity').value.trim();const password=$('password').value;$('password').value='';$('loginButton').disabled=true;message();
  try{await api('login',{method:'POST',body:{...(/^[0-9]{10}$/.test(value)?{phone:value}:{email:value}),password}});generation++;await loadSession();}
  catch(error){message(error.message);await loadSession();}finally{$('loginButton').disabled=false;}
});
$('logout').addEventListener('click',async()=>{clearSession();try{await api('logout',{method:'POST'});}catch(error){message(error.message);}await loadSession();});
$('refresh').addEventListener('click',()=>{void modelStatus();void refresh();});$('analyze').addEventListener('click',()=>getReport(true));
$('download').addEventListener('click',()=>{if(!report || !active || busy)return;const blob=new Blob([JSON.stringify(report,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download='safepay-review-'+report.evidence.transactionId+'.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
window.addEventListener('pagehide',()=>clearSession());document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(timer);}else if(active && !busy){void refresh();}});
void loadSession();

window.addEventListener('pageshow',event=>{if(event.persisted)void loadSession();});
