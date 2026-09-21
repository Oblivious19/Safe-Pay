import * as ko from 'knockout';
import {accounts as accountApi} from '../services/accountService';
import {transactions as transactionApi} from '../services/transactionService';
import {Account,Balance,TransactionSummary} from '../services/types';
import {refreshLoop} from '../services/refreshLoop';
import {rupees} from '../utils/money';
import {date} from '../utils/format';
import {description,label} from '../utils/paymentState';
import {errorText} from '../services/apiError';
import {navigate} from '../services/sessionRouteService';
import 'ojs/ojavatar';import 'ojs/ojbutton';
class Dashboard {
 accounts=ko.observableArray<Account>([]);selectedAccountId=ko.observable('');account=ko.observable<Account|null>(null);balance=ko.observable<Balance|null>(null);
 transactions=ko.observableArray<TransactionSummary>([]);loading=ko.observable(true);sessionExpired=ko.observable(false);accountError=ko.observable('');transactionError=ko.observable('');cancelError=ko.observable('');busy=ko.observable(false);cancelOpen=ko.observable(false);
 recent=ko.pureComputed(()=>this.transactions().slice(0,5));pending=ko.observableArray<TransactionSummary>([]);canSend=ko.pureComputed(()=>!this.loading()&&this.account()?.status==='ACTIVE'&&!!this.balance());
 formatMoney=rupees;formatDate=date;statusLabel=label;countdownText=(tx:TransactionSummary)=>description(tx.state);accountOption=(a:Account)=>a.accountType+' '+a.maskedAccountNumber+' · '+a.status;accountLabel=label;maskAccount=(v:string)=>v;
 initials=(v:string)=>(v||'?')[0].toUpperCase();riskClass=(tx:TransactionSummary)=>'risk-'+(tx.riskTier==='VERY_HIGH'?'red':tx.riskTier==='HIGH'?'orange':tx.riskTier==='MEDIUM'?'amber':'neutral');
 canCancelRow=()=>false;fluxPercent=()=> '0%';requestCancel=(row:TransactionSummary)=>navigate('transactions',{transactionId:row.transactionId});closeCancel=()=>this.cancelOpen(false);confirmCancel=()=>{};
 private alive=true;private epoch=0;private stop?:()=>void;
 load=async()=>{if(this.busy())return;this.loading(true);this.accountError('');const epoch=++this.epoch;
 try{const rows=await accountApi.list();if(!this.alive||epoch!==this.epoch)return;this.accounts(rows);const chosen=rows.find(a=>a.accountId===this.selectedAccountId())||rows.find(a=>a.status==='ACTIVE')||rows[0];this.selectedAccountId(chosen?.accountId||'');if(!chosen){this.account(null);this.balance(null);this.transactions([]);this.pending([]);this.accountError('No account is linked to this customer yet.');return;}await this.selectAccount();}
 catch(e){if(this.alive)this.accountError(errorText(e));}finally{if(this.alive)this.loading(false);}};
 selectAccount=async()=>{const epoch=++this.epoch,id=this.selectedAccountId();this.account(this.accounts().find(a=>a.accountId===id)||null);this.balance(null);this.transactions([]);this.pending([]);this.transactionError('');if(!id)return;
 const results=await Promise.allSettled([accountApi.balance(id),transactionApi.list({sourceAccountId:id,size:5}),...['CREATED','PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','RELEASED'].map(state=>transactionApi.list({sourceAccountId:id,state,size:5}))]);
 if(!this.alive||epoch!==this.epoch)return;const b=results[0];if(b.status==='fulfilled')this.balance(b.value as Balance);else this.accountError(errorText(b.reason));
 const h=results[1];if(h.status==='fulfilled')this.transactions((h.value as any).items);else this.transactionError(errorText(h.reason));
 const pending:TransactionSummary[]=[];for(const r of results.slice(2)){if(r.status==='fulfilled')pending.push(...(r.value as any).items);else this.transactionError('Some pending payments could not be refreshed. Open Transactions to check their state.');}const unique=new Map<string,TransactionSummary>();for(const item of pending){const prior=unique.get(item.transactionId);if(!prior||item.updatedAt>=prior.updatedAt)unique.set(item.transactionId,item);}this.pending([...unique.values()].sort((a,b)=>b.createdAt.localeCompare(a.createdAt)));};
 open=(row:TransactionSummary)=>navigate('transactions',{transactionId:row.transactionId});
 openKey=(row:TransactionSummary,event:KeyboardEvent)=>{if(event.key==='Enter'||event.key===' '){this.open(row);return false;}return true;};
 connected(){document.title='Dashboard | SafePay';void this.load();this.stop=refreshLoop(async()=>{if(!this.loading()&&!this.busy())await this.selectAccount();},15000);}
 disconnected(){this.alive=false;this.epoch++;this.stop?.();}
}
export = Dashboard;
