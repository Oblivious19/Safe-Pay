import * as ko from "knockout";
import app from "../appController";
import { accountService, AccountFunds } from "../services/accountService";
import { ApiError } from "../services/apiError";
import { Account } from "../services/types";
import { profileService } from "../services/profileService";

class ProfileViewModel {
  loading = ko.observable(true);
  error = ko.observable("");
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable<number | null>(null);
  accountOption = (a: Account): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  account = ko.observable<Account | null>(null);
  funds = ko.observable<AccountFunds | null>(null);
  fundsLoading = ko.observable(false);
  fundsError = ko.observable("");
  private fundsRevision = 0;
  status = ko.pureComputed(() => {
    const value = app.profile()?.status;
    return value ? value.charAt(0) + value.slice(1).toLowerCase() : "Status unavailable";
  });
  date = (value: string): string => {
    const date = new Date(value);
    return value && Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat("en-IN", {dateStyle:"medium"}).format(date) : "Not available";
  };
  name = ko.pureComputed(() => app.profile()?.name || "Your SafePay");
  email = ko.pureComputed(() => app.profile()?.email || "");
  initials = ko.pureComputed(() => (this.name() || "?").slice(0, 1).toUpperCase());
  simulated = ko.pureComputed(() => false);
  phone = ko.pureComputed(() => app.profile()?.phone || "");
  mask = (value: string): string => value && value.length > 4 ? "•••• " + value.slice(-4) : "Account number unavailable";
  accountLabel = (value: string): string => value === "SAVINGS" ? "Savings account" : value === "CURRENT" ? "Current account" : "Your account";
  money = (value: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(value);
  private alive = true;
  private poll?: ReturnType<typeof setInterval>;
  private refreshing=false;
  private onFocus=():void=>{void this.refreshBalances();};
  refreshBalances=async():Promise<void>=>{
    if(!this.alive || this.loading() || this.refreshing || document.visibilityState==="hidden")return;
    this.refreshing=true;
    try {
      const accounts=await accountService.list();if(!this.alive)return;
      this.accounts(accounts);this.account(accounts.find(a=>a.accountId===this.selectedAccountId()) || null);
      await this.loadFunds();
    } catch(error) {
      if(this.alive){
        this.fundsError("Live balance updates are unavailable. Please try again.");
        if(error instanceof ApiError && error.status===401)window.location.replace("/login?reason=session-expired");
      }
    } finally {this.refreshing=false;}
  };
  load = async (): Promise<void> => {
    this.loading(true); this.error(""); this.account(null);
    try {
      const [accounts,profile]=await Promise.all([accountService.list(),profileService.getCurrent()]);
      if(!this.alive)return;app.profile(profile);this.accounts(accounts);
      this.selectedAccountId((accounts.find(a=>a.accountId===this.selectedAccountId()) || accounts[0])?.accountId || null);this.selectAccount();
    } catch (error) {
      if (!this.alive) return;
      if (error instanceof ApiError && error.status === 401) {
        window.location.replace("/login?reason=session-expired");
        return;
      }
      this.error("We couldn’t load your profile details. Please try again.");
    } finally { if (this.alive) this.loading(false); }
  };
  selectAccount = (): void => {
    this.account(this.accounts().find(a=>a.accountId===this.selectedAccountId()) || null);
    this.funds(null); this.fundsError(""); void this.loadFunds();
  };
  loadFunds = async (): Promise<void> => {
    const id = this.account()?.accountId, revision = ++this.fundsRevision;
    if(!id){this.fundsLoading(false);return;}
    this.fundsLoading(true);this.fundsError("");
    try { const funds=await accountService.funds(id);if(this.alive && revision===this.fundsRevision){
      this.funds(funds);
      const updated={...this.account()!,balance:funds.balance};this.account(updated);
      this.accounts(this.accounts().map(a=>a.accountId===id?updated:a));
    } }
    catch { if(this.alive && revision===this.fundsRevision)this.fundsError("Available balance is temporarily unavailable."); }
    finally { if(this.alive && revision===this.fundsRevision)this.fundsLoading(false); }
  };
  connected(): void { this.alive=true; document.title = "Profile | SafePay"; window.addEventListener?.("focus",this.onFocus); this.poll=setInterval(()=>void this.refreshBalances(),3000); void this.load(); }
  disconnected(): void { this.alive = false; this.fundsRevision++; if(this.poll)clearInterval(this.poll); window.removeEventListener?.("focus",this.onFocus); }
}
export = ProfileViewModel;
