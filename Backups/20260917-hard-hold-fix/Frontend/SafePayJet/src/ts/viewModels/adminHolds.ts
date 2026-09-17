import * as ko from "knockout";
import {adminHoldService} from "../services/adminHoldService";
import {ApiError} from "../services/apiError";
import {newIdempotencyKey} from "../services/transactionService";
import {HeldPayment} from "../services/types";
import {explainReasons} from "../utils/protection";
export class AdminHoldsModel {
 holds=ko.observableArray<HeldPayment>([]);selected=ko.observable<HeldPayment|null>(null);
 confirmed=ko.observable(false);loading=ko.observable(false);busy=ko.observable(false);forbidden=ko.observable(false);
 error=ko.observable("");notice=ko.observable("");updated=ko.observable("");
 private generation=0;private alive=true;private keys=new Map<number,string>();private poll?:ReturnType<typeof setInterval>;
 pending=ko.pureComputed(()=>this.holds());heldAmount=ko.pureComputed(()=>this.holds().reduce((sum,row)=>sum+row.amount,0));
 selectedReasons=ko.pureComputed(()=>explainReasons(this.selected()?.riskReason));
 money=(value:number):string=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(value);
 when=(value:string):string=>value?new Date(value).toLocaleString("en-IN"):"—";
 masked=(value:string):string=>value?"•••• "+value.slice(-4):"—";
 load=async():Promise<void>=>{
  if(this.loading() || this.busy())return;const generation=++this.generation;this.loading(true);this.error("");
  try{const rows=await adminHoldService.list();if(!this.alive || generation!==this.generation)return;
   this.holds(rows.slice().sort((a,b)=>b.createdAt.localeCompare(a.createdAt)));this.forbidden(false);this.updated(new Date().toLocaleTimeString("en-IN"));
   if(this.selected()){const previous=this.selected()!;const current=rows.find(r=>r.transactionId===previous.transactionId)||null;this.selected(current);if(!current || current.amount!==previous.amount)this.confirmed(false);}
   if(!this.poll)this.poll=setInterval(()=>{if(!document.hidden)void this.load();},5000);
  }catch(e){if(this.alive && generation===this.generation){this.holds([]);this.selected(null);this.fail(e);}}
  finally{if(this.alive && generation===this.generation)this.loading(false);}
 };
 select=(row:HeldPayment):void=>{if(this.busy())return;this.selected(row);this.confirmed(false);this.error("");this.notice("");};
 clear=():void=>{if(!this.busy()){this.selected(null);this.confirmed(false);}};
 approve=async():Promise<void>=>{
  const row=this.selected();if(!row || !this.confirmed() || this.busy() || this.forbidden())return;
  let key=this.keys.get(row.transactionId);if(!key){key=newIdempotencyKey();this.keys.set(row.transactionId,key);}
  const generation=this.generation;this.busy(true);this.error("");
  try{await adminHoldService.approve(row.transactionId,key);if(!this.alive || generation!==this.generation)return;
   this.holds(this.holds().filter(r=>r.transactionId!==row.transactionId));this.selected(null);this.confirmed(false);
   this.notice("Payment approved and settled by the backend.");
  }catch(e){if(this.alive && generation===this.generation)this.fail(e);}
  finally{if(this.alive)this.busy(false);}
 };
 private fail(e:unknown):void{
  if(e instanceof ApiError && e.status===401){window.location.replace("/admin/login");return;}
  if(e instanceof ApiError && e.status===403)this.forbidden(true);
  this.error(e instanceof ApiError?e.message:"Approval could not be confirmed. Refresh the queue before retrying the same request.");
 }
 disconnected():void{this.alive=false;this.generation++;if(this.poll)clearInterval(this.poll);this.holds([]);this.selected(null);}
}
