import * as ko from "knockout";
import {adminHoldService} from "../services/adminHoldService";
import {ApiError} from "../services/apiError";
import {newIdempotencyKey} from "../services/transactionService";
import {HeldPayment} from "../services/types";
import {explainReasons} from "../utils/protection";
import {PAYMENT_CATEGORIES, PaymentCategory, QueueOrder, categoryLabel, orderedPayments} from "../constants/paymentCategories";
export class AdminHoldsModel {
 holds=ko.observableArray<HeldPayment>([]);selected=ko.observable<HeldPayment|null>(null);
 confirmed=ko.observable(false);loading=ko.observable(false);busy=ko.observable(false);forbidden=ko.observable(false);
 pendingDecision=ko.observable<"approve"|"decline"|null>(null);
 error=ko.observable("");notice=ko.observable("");updated=ko.observable("");
 private generation=0;private alive=true;private attempts=new Map<number,{action:"approve"|"decline";key:string}>();private poll?:ReturnType<typeof setInterval>;
 categoryOptions=PAYMENT_CATEGORIES;categoryFilter=ko.observable<PaymentCategory|"">("");queueOrder=ko.observable<QueueOrder>("PRIORITY");categoryLabel=categoryLabel;
 pending=ko.pureComputed(()=>orderedPayments(this.holds().filter(row=>!this.categoryFilter()||row.category===this.categoryFilter()),this.queueOrder()));
 heldAmount=ko.pureComputed(()=>this.holds().reduce((sum,row)=>sum+row.amount,0));
 selectedReasons=ko.pureComputed(()=>explainReasons(this.selected()?.riskReason));
 money=(value:number):string=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(value);
 when=(value:string):string=>value?new Date(value).toLocaleString("en-IN"):"—";
 masked=(value:string):string=>value?"•••• "+value.slice(-4):"—";
 load=async():Promise<void>=>{
  if(this.loading() || this.busy())return;const generation=++this.generation;this.loading(true);this.error("");
  try{const rows=await adminHoldService.list();if(!this.alive || generation!==this.generation)return;
   this.holds(rows);this.forbidden(false);this.updated(new Date().toLocaleTimeString("en-IN"));
   if(this.selected()){const previous=this.selected()!;const current=rows.find(r=>r.transactionId===previous.transactionId)||null;this.selected(current);if(!current || current.amount!==previous.amount)this.confirmed(false);}
   if(!this.poll)this.poll=setInterval(()=>{if(!document.hidden)void this.load();},5000);
  }catch(e){if(this.alive && generation===this.generation){this.holds([]);this.selected(null);this.fail(e);}}
  finally{if(this.alive && generation===this.generation)this.loading(false);}
 };
 select=(row:HeldPayment):void=>{if(this.busy())return;this.selected(row);this.pendingDecision(this.attempts.get(row.transactionId)?.action || null);this.confirmed(false);this.error("");this.notice("");};
 clear=():void=>{if(!this.busy()){this.selected(null);this.confirmed(false);}};
 approve=async():Promise<void>=>this.decide("approve");
 decline=async():Promise<void>=>this.decide("decline");
 private async decide(action:"approve"|"decline"):Promise<void>{
  const row=this.selected();if(!row || !this.confirmed() || this.busy() || this.loading() || this.forbidden())return;
  let attempt=this.attempts.get(row.transactionId);
  if(attempt && attempt.action!==action){this.error("The previous decision is not yet confirmed. Refresh or retry that same decision first.");return;}
  if(!attempt){attempt={action,key:newIdempotencyKey()};this.attempts.set(row.transactionId,attempt);}
  this.pendingDecision(action);const generation=this.generation;this.busy(true);this.error("");
  try{
   await adminHoldService[action](row.transactionId,attempt.key);
   if(!this.alive || generation!==this.generation)return;
   this.attempts.delete(row.transactionId);this.pendingDecision(null);
   this.holds(this.holds().filter(r=>r.transactionId!==row.transactionId));this.selected(null);this.confirmed(false);
   this.notice(action==="approve"?"Payment approved and settled by the backend.":"Payment declined and cancelled. Its reserved funds are available again; no money was deducted.");
  }catch(e){if(this.alive && generation===this.generation){
   if(e instanceof ApiError && [400,404,409].includes(e.status)){this.attempts.delete(row.transactionId);this.pendingDecision(null);}
   this.fail(e);
  }}finally{if(this.alive)this.busy(false);}
 }
 private fail(e:unknown):void{
  if(e instanceof ApiError && e.status===401){window.location.replace("/admin/login");return;}
  if(e instanceof ApiError && e.status===403)this.forbidden(true);
  this.error(e instanceof ApiError?e.message:"The decision could not be confirmed. Refresh the queue or retry the same decision.");
 }
 disconnected():void{this.alive=false;this.generation++;if(this.poll)clearInterval(this.poll);this.holds([]);this.selected(null);}
}
