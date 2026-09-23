import * as ko from 'knockout';
import {PageModel,confirmAction} from '../pageModel';
import {Evidence,staff,decide,reviewRecovery,reviewPending,canReplayReview,clearReviewRecovery} from '../services/staffService';
import {StaffTab,staffTabs,roles} from '../services/staffConfig';
import {Page} from '../services/types';
import {ApiError,errorText} from '../services/apiError';
import {decimal,validId,rupees} from '../utils/money';
import {label} from '../utils/paymentState';
import {session} from '../services/authService';
import {refreshLoop} from '../services/refreshLoop';
import {realtimeStatus} from '../services/realtimeService';
import {adminSafePayPinResets,PinResetRequest} from '../services/adminSafePayPinResetService';
type Field={label:string;value:string};
type Group={title:string;fields:Field[]};
export class StaffPage extends PageModel {
 area:string;tabs:StaffTab[];tab=ko.observable<StaffTab>();filters=ko.observableArray<any>([]);rows=ko.observableArray<Evidence>([]);result=ko.observable<Page<Evidence>|null>(null);page=ko.observable(0);loading=ko.observable(false);stale=ko.observable(false);detail=ko.observable<Evidence|null>(null);selectedId=ko.observable('');lookupId=ko.observable('');groups=ko.observableArray<Group>([]);statistics=ko.observableArray<Group>([]);timeline=ko.observableArray<Group>([]);timelinePage=ko.observable(0);timelineResult=ko.observable<Page<Evidence>|null>(null);timelineBusy=ko.observable(false);
 statusValue=ko.observable('ACTIVE');roleValue=ko.observable('CUSTOMER');roles=roles;reason=ko.observable('');note=ko.observable('');reviewPending=reviewPending;replayAvailable=ko.observable(false);pendingDescription=ko.observable('');adminUncertain=ko.observable(false);liveStatus=realtimeStatus;dashboard=ko.observable<Evidence|null>(null);
 pinResetRequests=ko.observableArray<PinResetRequest>([]);
 private epoch=0;private detailEpoch=0;private timelineEpoch=0;private stop?:()=>void;private activeFilters:Record<string,string|number>={};
 title=ko.pureComputed(()=>this.area==='admin'?'Administration':this.area==='risk'?'Risk review':'Audit & evidence');
 subtitle=ko.pureComputed(()=>this.area==='admin'?'Access, accounts and operational health.':this.area==='risk'?'Review the evidence. Make a considered decision.':'Trace every step, from payment instruction to ledger.');
 currentTitle=ko.pureComputed(()=>this.tab()?.label||'');columns=ko.pureComputed(()=>this.tab()?.columns||[]);
 adminStates=ko.pureComputed(()=>{const states=this.dashboard()?.paymentsByState||{};const total=Object.values(states).reduce((sum:number,value:any)=>sum+Number(value||0),0);return Object.entries(states).map(([name,value])=>({name:this.label(name),value:Number(value||0),width:total?Math.max(4,Math.round(Number(value||0)*100/total)):0})).filter(x=>x.value>0);});
 riskBars=ko.pureComputed(()=>{const data=this.dashboard()?.paymentsByRiskTier||{};const total=Object.values(data).reduce((sum:number,value:any)=>sum+Number(value||0),0);return Object.entries(data).map(([name,value])=>({name:this.label(name),value:Number(value||0),width:total?Math.max(4,Math.round(Number(value||0)*100/total)):0})).filter(x=>x.value>0);});
 reviewBars=ko.pureComputed(()=>{const data=this.dashboard()?.reviewsByStatus||{};const total=Object.values(data).reduce((sum:number,value:any)=>sum+Number(value||0),0);return Object.entries(data).map(([name,value])=>({name:this.label(name),value:Number(value||0),width:total?Math.max(4,Math.round(Number(value||0)*100/total)):0})).filter(x=>x.value>0);});
 auditBars=(key:string)=>ko.pureComputed(()=>{const data=this.dashboard()?.[key]||{};const total=Object.values(data).reduce((sum:number,value:any)=>sum+Number(value||0),0);return Object.entries(data).map(([name,value])=>({name:this.label(name),value:Number(value||0),width:total?Math.max(4,Math.round(Number(value||0)*100/total)):0})).filter(x=>x.value>0);});
 ledgerBars=this.auditBars('ledgerReconciliation');reservationBars=this.auditBars('reservationReconciliation');exceptionBars=this.auditBars('exceptionsByStatus');
 adminMetric=(key:string)=>String(this.dashboard()?.[key] ?? 0);
 dashboardUpdated=ko.pureComputed(()=>{const value=this.dashboard()?.observedAt;return value?'Last updated '+this.date(String(value)):'Loading current data…';});
 canReview=ko.pureComputed(()=>this.area==='risk'&&this.detail()?.review?.status==='PENDING'&&this.detail()?.review?.transactionState==='PENDING_RISK_REVIEW'&&!this.busy()&&!this.stale()&&!this.reviewPending());
 canAdmin=ko.pureComputed(()=>this.area==='admin'&&this.tab()?.key==='users'&&!!this.detail()&&!this.busy()&&!this.stale()&&!this.adminUncertain());
 transactionId=ko.pureComputed(()=>{const d=this.detail();return String(d?.review?.transactionId||d?.transaction?.transactionId||d?.posting?.transactionId||d?.transactionId||'');});
 constructor(area:string,private context:any={}){super();this.area=area;this.tabs=staffTabs[area];this.setTab(context.params?.page||this.tabs[0].key);}
 label=(value:string)=>({q:'Search',recordId:'Record ID',userId:'User ID',ownerId:'Customer ID',ifscCode:'IFSC code',currencyCode:'Currency',canCancel:'Cancellation currently available'}[value]||value.replace(/([a-z0-9])([A-Z])/g,'$1 $2').replace(/^./,c=>c.toUpperCase()));
 display=(row:Evidence,key:string):string=>{
  const value=row[key];if(value===null||value===undefined||value==='')return key==='category'?'Not specified':'—';
  if(Array.isArray(value))return value.map(v=>typeof v==='string'?label(v):String(v)).join(', ');
  if(typeof value==='boolean')return value?'Yes':'No';
  if(/amount|balance|debitTotal|creditTotal|reservationDifference/i.test(key)&&typeof value==='string'){
   if(value.startsWith('-'))return '−'+rupees(value.slice(1));return rupees(value);
  }
  if(/At$|Until$|From$|To$|serverTime/.test(key)&&typeof value==='string'&&/^\d{4}-/.test(value))return this.date(value);
  return typeof value==='object'?'See detail':String(value).includes('_')?label(String(value)):String(value);
 };
 private evidence(value:Evidence,title='Details'):Group[]{
  const groups:Group[]=[];const fields:Field[]=[];
  Object.entries(value||{}).forEach(([key,v])=>{
   if(v&&typeof v==='object'&&!Array.isArray(v))groups.push(...this.evidence(v,this.label(key)));
   else if(Array.isArray(v)&&v.some(x=>x&&typeof x==='object'))v.forEach((x,i)=>groups.push(...this.evidence(x,this.label(key)+' '+(i+1))));
   else fields.push({label:this.label(key),value:this.display(value,key)});
  });
  return [{title,fields},...groups].filter(g=>g.fields.length);
 }
 private setTab(key:string){const tab=this.tabs.find(t=>t.key===key)||this.tabs[0];this.loading(false);this.busy(false);this.tab(tab);this.filters(tab.filters.map(f=>({...f,value:ko.observable(f.key==='sort'?'PRIORITY':this.area==='audit'&&tab.key==='reviews'&&f.key==='status'?'PENDING':'')})));this.activeFilters={};this.page(0);this.result(null);this.rows([]);this.statistics([]);this.dashboard(null);this.closeDetail();this.adminUncertain(false);}
 parametersChanged(params:any){if(params.page!==this.tab()?.key){this.epoch++;this.setTab(params.page);void this.search();}}
 switchTab=(tab:StaffTab)=>{if(!this.busy())this.navigate(this.area+'/'+tab.key);};
 private queryFilters():Record<string,string|number>{
  const values:Record<string,string|number>={};
  for(const f of this.filters()){const v=String(f.value() ?? '').trim();if(!v)continue;
   if(f.key.endsWith('Id'))validId(v);
   if(f.key==='minCurrentBalance')decimal(v);
   values[f.key]=f.type==='datetime-local'?new Date(v).toISOString():v;
  }
  if(values.from&&values.to&&String(values.from)>=String(values.to))throw Error('The end of the range must follow its start.');
  return values;
 }
 search=async()=>{if(this.busy())return;try{this.activeFilters=this.queryFilters();this.page(0);await this.load();}catch(e){this.error(errorText(e));}};
 load=async()=>{
  if(this.loading()||this.busy())return;
  const n=++this.epoch,tab=this.tab()!;this.loading(true);this.error('');
  try{if(this.area==='admin'){this.pinResetRequests(await adminSafePayPinResets.list());}if(tab.stats){const data=await staff.get(tab.path);if(this.alive&&n===this.epoch){this.dashboard(data);this.statistics(this.evidence(data,'At a glance'));}}
   else{const data=await staff.list(tab.path,{...this.activeFilters,page:this.page(),size:20});if(this.alive&&n===this.epoch){this.result(data);this.rows(data.items);}}}
  catch(e){if(this.alive&&n===this.epoch)this.error(errorText(e));}
  finally{if(this.alive&&n===this.epoch)this.loading(false);}
 };
 previous=()=>{if(!this.loading()&&this.page()>0){this.page(this.page()-1);void this.load();}};
 next=()=>{if(!this.loading()&&this.result()&&!this.result()!.last){this.page(this.page()+1);void this.load();}};
 open=(row:Evidence)=>void this.select(row);
 private async select(row:Evidence){
  if(this.busy())return;const tab=this.tab()!,id=String(row[tab.id]),n=++this.detailEpoch;this.selectedId(id);this.detail(null);this.groups([]);this.timeline([]);this.timelineResult(null);this.reason('');this.note('');this.stale(true);this.busy(true);this.error('');
  try{
   const data=tab.detail?await staff.get(tab.path+'/'+(tab.id==='policyVersion'?encodeURIComponent(id):validId(id))):row;
   if(!this.alive||n!==this.detailEpoch)return;
   this.detail(data);this.groups(this.evidence(data,tab.label));this.statusValue(data.status||'ACTIVE');this.stale(false);
   if(this.area==='admin'&&tab.key==='accounts'){const balance=await staff.get(tab.path+'/'+validId(id)+'/balance');if(this.alive&&n===this.detailEpoch)this.groups([...this.groups(),...this.evidence(balance,'Current funds')]);}
   if(this.area==='risk'||this.area==='audit')await this.loadTimeline(0);
  }catch(e){if(this.alive&&n===this.detailEpoch)this.error(errorText(e));}
  finally{if(this.alive&&n===this.detailEpoch)this.busy(false);}
 }
 openById=()=>{if(this.lookupId().trim())this.open({[this.tab()!.id]:this.lookupId().trim()});};
 closeDetail=()=>{this.detailEpoch++;this.timelineEpoch++;this.detail(null);this.selectedId('');this.groups([]);this.timeline([]);this.timelineResult(null);this.timelineBusy(false);};
 refreshDetail=async()=>{if(this.busy())return;const d=this.detail();if(d)await this.select(this.tab()!.detail?{[this.tab()!.id]:this.selectedId()}:d);};
 private async loadTimeline(page:number){
  const id=this.transactionId();if(!id||this.timelineBusy())return;const n=++this.timelineEpoch;this.timelineBusy(true);
  try{const result=await staff.list('/transactions/'+validId(id)+'/audit',{page,size:10});if(this.alive&&n===this.timelineEpoch){this.timelineResult(result);this.timelinePage(page);this.timeline(result.items.flatMap((e,i)=>this.evidence(e,'Event '+(page*10+i+1))));}}
  catch(e){if(this.alive&&n===this.timelineEpoch)this.error(errorText(e));}finally{if(this.alive&&n===this.timelineEpoch)this.timelineBusy(false);}
 }
 previousTimeline=()=>void this.loadTimeline(Math.max(0,this.timelinePage()-1));nextTimeline=()=>{if(this.timelineResult()&&!this.timelineResult()!.last)void this.loadTimeline(this.timelinePage()+1);};
 private recovery(){try{const p=reviewRecovery();this.pendingDescription(p?'Review '+p.reviewId+' · '+label(p.action)+'. Confirm the previous outcome before another action.':'');this.replayAvailable(canReplayReview());}catch(e){this.pendingDescription(errorText(e));}}
 inspectPending=()=>{try{const p=reviewRecovery();if(p)this.open({reviewId:p.reviewId});}catch(e){this.error(errorText(e));}};
 replay=()=>this.run(async()=>{await decide();this.message('The original review action has been confirmed. Refresh its evidence.');this.recovery();});
 acknowledgeReview=()=>this.run(async()=>{if(await confirmAction('Finish recovery?','Confirm you have inspected this review and its audit timeline, including notes, and established the original outcome. Do not clear an unknown action merely to submit it again.','Outcome reconciled')){clearReviewRecovery();this.recovery();}});
 decision=async(action:string)=>{await this.run(async()=>{
  if(this.area!=='risk'||this.detail()?.review?.status!=='PENDING'||this.stale()||reviewPending())throw Error('Refresh a pending review before deciding.');
  const id=this.selectedId(),value=action==='notes'?this.note().trim():this.reason().trim();
  if(action!=='approve'&&!value)throw Error(action==='notes'?'Enter a note.':'A reason is required.');
  if(value.length>1000)throw Error('Use at most 1,000 characters.');
  const verbs:Record<string,string>={approve:'Approve review',reject:'Reject payment','request-verification':'Request email verification',notes:'Add permanent note'};
  if(!await confirmAction(verbs[action]+'?',this.detail()!.review.transactionReference+' · '+this.money(this.detail()!.review.amount)+'. '+(action==='approve'?'The backend will determine release and settlement.':action==='notes'?'This note is append-only audit evidence.':'The server checks the current review round and payment state.'),verbs[action],action==='reject'))return;
  this.stale(true);try{await decide(id,action,value);this.message('Action confirmed. Refresh the review to see its current state.');this.reason('');this.note('');}finally{this.recovery();}
 });if(this.alive){await this.refreshDetail();await this.load();}};
 approve=()=>this.decision('approve');reject=()=>this.decision('reject');requestVerification=()=>this.decision('request-verification');addNote=()=>this.decision('notes');
 private adminAction(action:'status'|'assign'|'remove'|'revoke',value=''){return this.run(async()=>{
  if(this.area!=='admin'||this.tab()?.key!=='users'||!this.detail()||this.stale()||this.adminUncertain())throw Error('Refresh user details before changing access.');
  const id=this.selectedId(),d=this.detail()!,verb=action==='status'?'Set status to '+value:action==='assign'?'Assign '+label(value):action==='remove'?'Remove '+label(value):'Revoke all sessions';
  if(!await confirmAction(verb+'?',d.fullName+' (user '+id+'). Access changes can force reauthentication. The server enforces role and last-administrator safeguards.'+(id===session()?.userId?' This affects your own account.':''),verb,true))return;
  this.stale(true);try{const result=await staff.user(id,action,value);if(this.alive){this.detail(result);this.groups(this.evidence(result,'User access'));this.stale(false);this.message('Access change confirmed.');}}
  catch(e){if(e instanceof ApiError&&e.uncertain)this.adminUncertain(true);throw e;}
 });}
 changeStatus=()=>this.adminAction('status',this.statusValue());assignRole=()=>this.adminAction('assign',this.roleValue());removeRole=()=>this.adminAction('remove',this.roleValue());revokeSessions=()=>this.adminAction('revoke');
 decidePinReset=async(row:PinResetRequest,approved:boolean)=>{if(this.busy())return;const action=approved?'Approve PIN reset':'Reject PIN reset';if(!await confirmAction(action+'?',row.fullName+' will '+(approved?'be allowed to set a new SafePay PIN.':'keep their existing SafePay PIN.'),action,!approved))return;this.busy(true);try{await (approved?adminSafePayPinResets.approve(row.userId):adminSafePayPinResets.reject(row.userId));this.message('PIN reset request updated.');this.pinResetRequests(await adminSafePayPinResets.list());}catch(e){this.error(errorText(e));}finally{if(this.alive)this.busy(false);}};
 acknowledgeAdmin=()=>this.run(async()=>{const d=await staff.get('/admin/users/'+validId(this.selectedId()));this.detail(d);this.groups(this.evidence(d,'Current user access'));if(await confirmAction('Access outcome reconciled?','Review the current status and roles. For session revocation, confirm the affected session now requires sign-in.','Outcome reconciled')){this.adminUncertain(false);this.stale(false);}});
 connected(){document.title=this.title()+' | SafePay';if(this.area==='risk')this.recovery();void this.search();this.stop=refreshLoop(async()=>{if(!this.busy()&&!this.detail())await this.load();},20000);}
 disconnected(){super.disconnected();this.epoch++;this.detailEpoch++;this.timelineEpoch++;this.stop?.();}
}
