import * as ko from 'knockout';
import {accounts as accountApi} from '../services/accountService';
import {profile as loadProfile} from '../services/profileService';
import {Account,Balance,Profile} from '../services/types';
import {errorText} from '../services/apiError';
import {safePayPin} from '../services/safePayPinService';
import {rupees} from '../utils/money';
import {label} from '../utils/paymentState';
class ProfilePage {
 loading=ko.observable(true);error=ko.observable('');accounts=ko.observableArray<Account>([]);selectedAccountId=ko.observable('');account=ko.observable<Account|null>(null);balance=ko.observable<Balance|null>(null);identity=ko.observable<Profile|null>(null);
 name=ko.pureComputed(()=>this.identity()?.fullName||'');email=ko.pureComputed(()=>this.identity()?.email||'');phone=ko.pureComputed(()=>this.identity()?.mobileNumber||'');initials=ko.pureComputed(()=>(this.name()||'?')[0]);simulated=ko.observable(true);pinResetMessage=ko.observable('');pinBusy=ko.observable(false);
 accountOption=(a:Account)=>a.accountType+' '+a.maskedAccountNumber+' · '+a.status;accountLabel=label;mask=(v:string)=>v;money=rupees;private alive=true;private epoch=0;
 load=async()=>{this.loading(true);this.error('');try{const [rows,p]=await Promise.all([accountApi.list(),loadProfile()]);if(!this.alive)return;this.accounts(rows);this.identity(p);this.selectedAccountId(rows[0]?.accountId||'');await this.selectAccount();}catch(e){if(this.alive)this.error(errorText(e));}finally{if(this.alive)this.loading(false);}};
 selectAccount=async()=>{const epoch=++this.epoch,id=this.selectedAccountId();this.account(this.accounts().find(a=>a.accountId===id)||null);this.balance(null);if(!id)return;try{const [a,b]=await Promise.all([accountApi.get(id),accountApi.balance(id)]);if(this.alive&&epoch===this.epoch){this.account(a);this.balance(b);}}catch(e){if(this.alive&&epoch===this.epoch)this.error(errorText(e));}};
 requestPinReset=async()=>{if(this.pinBusy())return;this.pinBusy(true);this.pinResetMessage('');try{await safePayPin.requestReset();this.pinResetMessage('Your SafePay PIN reset request has been sent for administrator approval.');}catch(e){this.pinResetMessage(errorText(e));}finally{if(this.alive)this.pinBusy(false);}};
 connected(){document.title='Profile | SafePay';void this.load();}disconnected(){this.alive=false;this.epoch++;}
}
export = ProfilePage;
