import {categories,states} from '../utils/paymentState';
export interface Filter {key:string;label:string;options?:readonly string[];type?:string}
export interface StaffTab {key:string;label:string;path:string;id:string;columns:string[];filters:Filter[];detail?:boolean;stats?:boolean}
const f=(key:string,label:string,options?:readonly string[],type?:string):Filter=>({key,label,options,type});
const dates=[f('from','From',undefined,'datetime-local'),f('to','To (exclusive)',undefined,'datetime-local')];
const tx=f('transactionId','Transaction ID');const status=(values:string[])=>f('status','Status',values);
const reviewStatuses=['PENDING','APPROVED','REJECTED','REVERIFICATION_REQUESTED','CANCELLED'];
export const roles=['CUSTOMER','SYSTEM_ADMIN','RISK_OFFICER','AUDITOR'];
export const staffTabs:Record<string,StaffTab[]>={
 admin:[
 {key:'dashboard',label:'Overview',path:'/admin/operations/stats',id:'',columns:[],filters:[],stats:true},
 {key:'users',label:'Users & access',path:'/admin/users',id:'userId',columns:['fullName','email','roles','status'],filters:[f('q','Name, email or mobile'),f('role','Role',roles),status(['ACTIVE','LOCKED','DISABLED'])],detail:true},
 {key:'accounts',label:'Accounts',path:'/admin/accounts',id:'accountId',columns:['ownerName','maskedAccountNumber','accountType','bankName','status'],filters:[f('customerId','Customer ID'),f('minCurrentBalance','Minimum current balance'),f('accountType','Account type',['SAVINGS','CURRENT','OUTBOUND_CLEARING','OPENING_BALANCE_CONTROL'])],detail:true},
 {key:'failures',label:'Operational failures',path:'/admin/operations/failures',id:'recordId',columns:['source','transactionId','processingStage','status','displayExplanation','occurredAt'],filters:[f('source','Source',['TRANSACTION','NOTIFICATION']),tx,...dates]}
 ],
 risk:[{key:'reviews',label:'Review queue',path:'/admin/risk-reviews',id:'reviewId',columns:['transactionReference','customerName','beneficiaryName','amount','category','reviewRound','requestedAt'],filters:[f('category','Category',categories.map(c=>c.value)),f('sort','Queue order',['PRIORITY','OLDEST'])],detail:true}],
 audit:[
 {key:'logs',label:'Audit trail',path:'/audit-logs',id:'auditLogId',columns:['occurredAt','actionCode','actorType','transactionId','outcome'],filters:[tx,f('actionCode','Action'),f('outcome','Outcome'),f('actorType','Actor type'),f('correlationId','Correlation reference'),...dates]},
 {key:'transactions',label:'Payments',path:'/audit/transactions',id:'transactionId',columns:['transactionReference','beneficiaryName','amount','state','category','createdAt'],filters:[f('customerId','Customer ID'),f('state','State',states),...dates],detail:true},
 {key:'reviews',label:'Risk reviews',path:'/audit/risk-reviews',id:'reviewId',columns:['transactionReference','customerName','amount','status','reviewRound','updatedAt'],filters:[status(reviewStatuses)],detail:true},
 {key:'ledger',label:'Ledger',path:'/audit/ledger-postings',id:'postingId',columns:['postingReference','transactionId','amount','status','postedAt'],filters:[tx,status(['PENDING','POSTED','FAILED']),...dates],detail:true},
 {key:'ledger-reconciliation',label:'Ledger checks',path:'/audit/reconciliation/ledger',id:'postingId',columns:['postingReference','postingAmount','debitTotal','creditTotal','reconciliationStatus'],filters:[f('postingId','Posting ID'),f('reconciliationStatus','Reconciliation result')]},
 {key:'reservations',label:'Reservation checks',path:'/audit/reconciliation/reservations',id:'accountId',columns:['maskedAccountNumber','storedReservedAmount','calculatedReservedAmount','reservationDifference','reconciliationStatus'],filters:[f('accountId','Account ID'),f('reconciliationStatus','Reconciliation result')]},
 {key:'exceptions',label:'Exceptions',path:'/audit/exceptions',id:'exceptionId',columns:['exceptionReference','transactionId','processingStage','status','displayExplanation'],filters:[tx,f('processingStage','Processing stage'),f('status','Status'),...dates],detail:true},
 {key:'policies',label:'Risk policies',path:'/audit/risk-policies',id:'policyVersion',columns:['policyVersion','policyName','status','effectiveFrom'],filters:[status(['DRAFT','ACTIVE','RETIRED'])],detail:true}
 ]};
