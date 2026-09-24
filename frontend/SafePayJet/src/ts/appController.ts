import * as ko from 'knockout';
import CoreRouter = require('ojs/ojcorerouter');
import ModuleRouterAdapter = require('ojs/ojmodulerouter-adapter');
import KnockoutRouterAdapter = require('ojs/ojknockoutrouteradapter');
import UrlPathAdapter = require('ojs/ojurlpathparamadapter');
import Context = require('ojs/ojcontext');
import { session, restore, logout } from './services/authService';
import { profile as profileApi } from './services/profileService';
import { Profile } from './services/types';
import { mayOpen, landing, configureNavigation } from './services/sessionRouteService';
import { startRealtime } from './services/realtimeService';
import { errorText } from './services/apiError';
import { confirmation, confirmAction } from './pageModel';
import { reviewPending } from './services/staffService';
import { recovery } from './services/transactionService';
import 'ojs/ojknockout'; import 'ojs/ojmodule-element'; import 'ojs/ojbutton';
class RootViewModel {
  manner=ko.observable('polite'); message=ko.observable(''); routeBusy=ko.observable(true); entryLoaderBusy=ko.observable(document.documentElement.hasAttribute('data-safepay-entry-loader'));
  currentYear=new Date().getFullYear(); private entryTimer:number|undefined;
  profile=ko.observable<Profile|null>(null); profileLoading=ko.observable(false); loggingOut=ko.observable(false); logoutError=ko.observable('');
  session=session; confirmation=confirmation; ready=ko.observable(false); sessionError=ko.observable('');
  navItems=ko.pureComputed(()=>{
    const r=session()?.authorities || []; const rows:{path:string;label:string;iconClass:string}[]=[];
    const add=(path:string,label:string,icon='oj-ux-ico-list')=>rows.push({path,label,iconClass:icon+' customer-nav-icon'});
    if(r.includes('CUSTOMER')) { add('dashboard','Dashboard','oj-ux-ico-home');add('send-money','Send Money','oj-ux-ico-send');add('beneficiaries','Beneficiaries','oj-ux-ico-contact-group');add('transactions','Transactions');add('notifications','Notifications');add('profile','Profile','oj-ux-ico-contact'); }
    if(r.includes('SYSTEM_ADMIN')) add('admin/dashboard','Administration'); if(r.includes('RISK_OFFICER')) add('risk/reviews','Risk Review'); if(r.includes('AUDITOR')) add('audit/logs','Audit'); return rows;
  });
  moduleAdapter:ModuleRouterAdapter<any>; selection:KnockoutRouterAdapter<any>; router:CoreRouter<any,any>; private identity:string|null=null;
  constructor(){
    if(location.pathname==='/admin/login') history.replaceState(null,'','/login');
    if(location.pathname==='/admin' || location.pathname==='/admin/') history.replaceState(null,'','/admin/dashboard');
    if(location.pathname==='/register') history.replaceState(null,'','/register/mobile');
    if(location.pathname==='/send-money') history.replaceState(null,'','/send-money/beneficiary'+location.search);
    this.router=new CoreRouter([{path:'',redirect:'home'},...['home','about','login','dashboard','beneficiaries','transactions','notifications','profile'].map(path=>({path,detail:{label:path}})),{path:'register/{step}',detail:{label:'Register'}},{path:'send-money/{step}',detail:{label:'Send money'}},{path:'admin/{page}',detail:{label:'Administration'}},{path:'risk/{page}',detail:{label:'Risk review'}},{path:'audit/{page}',detail:{label:'Audit'}}],{urlAdapter:new UrlPathAdapter('/')});
    this.moduleAdapter=new ModuleRouterAdapter(this.router); this.selection=new KnockoutRouterAdapter(this.router);
    configureNavigation((path,params)=>{
      const base=path.split('/')[0]; if(['admin','risk','audit'].includes(base)){params={...params,page:path.split('/')[1] || 'dashboard'};path=base;}
      if(path==='send-money') params={step:'beneficiary',...params}; if(path==='register') params={step:'mobile',...params};
      const extra=new URLSearchParams(Object.entries(params).filter(([key])=>!['step','page'].includes(key))).toString();
      void this.router.go({path,params}).then(()=>{ history.replaceState(null,'',location.pathname+(extra?'?'+extra:'')); window.dispatchEvent(new CustomEvent('safepay:route-params',{detail:params})); }).catch(e=>this.sessionError(errorText(e)));
    });
    this.router.beforeStateChange.subscribe(event=>{ if(!event.state)return; this.routeBusy(true); event.accept(Promise.resolve().then(()=>{if(!mayOpen(event.state.path || 'home')){this.routeBusy(false);throw new Error('Access denied');}})); });
    this.router.currentState.subscribe(()=>{this.routeBusy(false);});
    session.subscribe(value=>{ if(!this.ready()) return; if(!value){this.profile(null);this.ready(false);if(!this.loggingOut())location.replace('/login?reason=session-expired');return;} if(this.identity && this.identity!==value.userId){this.profile(null);location.replace('/'+landing());} });
    confirmation.subscribe(value=>{ const dialog=document.getElementById('app-confirm') as HTMLDialogElement|null; if(value) requestAnimationFrame(()=>{if(dialog && !dialog.open)dialog.showModal();}); else if(dialog?.open)dialog.close(); });
    document.getElementById('globalBody')?.addEventListener('announce',((e:CustomEvent)=>{this.message(e.detail.message);this.manner(e.detail.manner);}) as EventListener);
    void this.initialize();
  }
  answer=(yes:boolean)=>{const c=confirmation();confirmation(null);c?.resolve(yes);};
  cancelConfirmation=()=>{this.answer(false);return false;};
  private finishEntry(){
    if(this.entryTimer!==undefined)return;
    const started=Number(document.documentElement.getAttribute('data-safepay-entry-start')) || Date.now();
    const reveal=()=>{
      this.entryLoaderBusy(false);
      document.documentElement.removeAttribute('data-safepay-entry-loader');
      document.documentElement.removeAttribute('data-safepay-booting');
      document.documentElement.removeAttribute('data-safepay-bootstrap-timeout');
      document.getElementById('safepay-app-content')?.removeAttribute('inert');
    };
    if(this.entryLoaderBusy())this.entryTimer=window.setTimeout(reveal,Math.max(0,2000-(Date.now()-started)));
    else reveal();
  }
  initialize=async()=>{
    this.sessionError('');this.routeBusy(true);
    try { await restore();this.identity=session()?.userId || null;const route=location.pathname.split('/')[1] || 'home';
      if(!mayOpen(route)){location.replace(session()?'/'+landing():'/login');return;}
      if(session()?.authorities.includes('CUSTOMER')){this.profileLoading(true);try{this.profile(await profileApi());}catch(e){this.logoutError(errorText(e));}finally{this.profileLoading(false);}}
      this.ready(true);startRealtime();await this.router.sync();
    } catch(e){this.sessionError(errorText(e));} finally{this.routeBusy(false);this.finishEntry();Context.getPageContext().getBusyContext().applicationBootstrapComplete();}
  };
  logout=async()=>{if(this.loggingOut())return;try{if(reviewPending() && !await confirmAction('Unconfirmed review action','Inspect the review and its audit evidence before signing out. Signing out discards the original request text.','Sign out anyway',true))return;if(session()?.authorities.includes('CUSTOMER') && recovery().status!=='empty' && !await confirmAction('Unconfirmed payment','A payment outcome still needs reconciliation. Signing out clears recovery metadata; retain its reference and review history before starting a replacement.','Sign out anyway',true))return;
    this.loggingOut(true);await logout();location.replace('/home');}catch(e){this.logoutError(errorText(e));this.loggingOut(false);}};
}
export default new RootViewModel();
