import * as ko from 'knockout';
import { errorText } from './services/apiError';
import { rupees } from './utils/money';
import { date } from './utils/format';
import { navigate } from './services/sessionRouteService';
export const confirmation = ko.observable<{title:string; message:string; action:string; danger:boolean; resolve:(yes:boolean)=>void} | null>(null);
export function confirmAction(title:string, message:string, action:string, danger=false):Promise<boolean> {
  if (confirmation()) return Promise.resolve(false);
  return new Promise(resolve => confirmation({title,message,action,danger,resolve}));
}
export class PageModel {
  busy=ko.observable(false); error=ko.observable(''); message=ko.observable(''); alive=true;
  money=rupees; date=date; navigate=navigate;
  async run(action:()=>Promise<void>):Promise<void> {
    if(this.busy()) return; this.busy(true); this.error('');
    try { await action(); } catch(e) { if(this.alive) this.error(errorText(e)); }
    finally { if(this.alive) this.busy(false); }
  }
  disconnected():void { this.alive=false; }
}
