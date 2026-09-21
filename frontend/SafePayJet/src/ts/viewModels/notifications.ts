import * as ko from 'knockout';
import {PageModel} from '../pageModel';
import {notifications} from '../services/notificationService';
import {Notice,Page} from '../services/types';
import {refreshLoop} from '../services/refreshLoop';
import {errorText} from '../services/apiError';
class Notifications extends PageModel {
 rows=ko.observableArray<Notice>([]);page=ko.observable(0);result=ko.observable<Page<Notice>|null>(null);loading=ko.observable(false);private stop?:()=>void;private epoch=0;
 load=async()=>{if(this.loading())return;const n=++this.epoch;this.loading(true);this.error('');try{const result=await notifications.list(this.page());if(this.alive&&n===this.epoch){this.result(result);this.rows(result.items);}}catch(e){if(this.alive)this.error(errorText(e));}finally{if(this.alive)this.loading(false);}};
 read=(notice:Notice)=>this.run(async()=>{try{await notifications.read(notice.notificationId);}finally{await this.load();}});
 open=(notice:Notice)=>{if(notice.transactionId)this.navigate('transactions',{transactionId:notice.transactionId});};
 previous=()=>{if(this.page()>0&&!this.loading()){this.page(this.page()-1);void this.load();}};
 next=()=>{if(this.result()&&!this.result()!.last&&!this.loading()){this.page(this.page()+1);void this.load();}};
 connected(){document.title='Notifications | SafePay';this.stop=refreshLoop(this.load);}
 disconnected(){super.disconnected();this.epoch++;this.stop?.();}
}
export = Notifications;
