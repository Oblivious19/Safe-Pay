import * as ko from "knockout";
import { adminUserService, AdminUser, CreditAccount, CreditReceipt, validCredit } from "../services/adminUserService";
import { newIdempotencyKey } from "../services/transactionService";
import { ApiError } from "../services/apiError";

export class AdminUsersModel {
  users = ko.observableArray<AdminUser>([]); selected = ko.observable<AdminUser | null>(null);
  accounts=ko.observableArray<CreditAccount>([]);
  selectedAccountId=ko.observable<number|null>(null);
  accountLabel=(a:CreditAccount):string=>a.accountType+" •••• "+a.accountNumber.slice(-4)+" · "+a.status;
  private operation?:{id:number;amount:string;key:string};
  account = ko.observable<CreditAccount | null>(null); receipt = ko.observable<CreditReceipt | null>(null);
  amount = ko.observable(""); confirmed = ko.observable(false); pending = ko.observable(false);
  loading = ko.observable(false); busy = ko.observable(false); forbidden = ko.observable(false);
  error = ko.observable(""); noAccount = ko.observable(false);
  private generation = 0;
  money = (value: string): string => {
    const [whole, fraction = ""] = value.split('.');
    return "₹" + BigInt(whole).toLocaleString("en-IN") + "." + fraction.padEnd(2, "0");
  };
  masked = (number: string): string => "•••• " + number.slice(-4);
  private fail(e: unknown): void {
    if (e instanceof ApiError && e.status === 401) window.location.replace("/admin/login");
    else if (e instanceof ApiError && e.status === 403) { this.forbidden(true); this.users([]); this.account(null); }
    else this.error(e instanceof ApiError && [400,404,409].includes(e.status) ? e.message : "Unable to confirm the request. Check the account and audit log before making another credit.");
  }
  load = async (): Promise<void> => {
    if (this.loading() || this.pending()) return;
    const generation = ++this.generation; this.loading(true); this.error("");
    try { const users = await adminUserService.users(); if (generation === this.generation) this.users(users); }
    catch(e) { if (generation === this.generation) this.fail(e); }
    finally { if (generation === this.generation) this.loading(false); }
  };
  select = async (user: AdminUser): Promise<void> => {
    if (this.pending() || this.busy()) return;
    const generation = ++this.generation;
    this.selected(user); this.account(null); this.receipt(null); this.noAccount(false); this.error("");
    this.amount(""); this.confirmed(false); this.loading(true);
    try { const accounts = await adminUserService.accounts(user.userId); if (generation === this.generation) {this.accounts(accounts);this.selectedAccountId(accounts[0]?.accountId || null);this.selectAccount();this.noAccount(!accounts.length);} }
    catch(e) { if (generation === this.generation) {
      if (e instanceof ApiError && e.status === 404) this.noAccount(true); else this.fail(e);
    }} finally { if (generation === this.generation) this.loading(false); }
  };
  selectAccount = ():void=>{if(this.pending() || this.busy())return;this.account(this.accounts().find(a=>a.accountId===this.selectedAccountId()) || null);this.receipt(null);this.amount("");this.confirmed(false);};
  retry = async ():Promise<void>=>{if(this.operation && !this.busy())await this.sendCredit();};
  submit = async (): Promise<void> => {
    if (this.busy() || this.forbidden() || this.pending()) return;
      if (!this.account() || this.account()!.status !== "ACTIVE" || !validCredit(this.amount()) || !this.confirmed()) {
        this.error("Select an ACTIVE account, enter a positive amount (up to two decimals), and confirm the credit."); return;
      }
    this.operation={id:this.account()!.accountId,amount:this.amount(),key:newIdempotencyKey()};
    await this.sendCredit();
  };
  private async sendCredit():Promise<void>{
    const operation=this.operation!;const generation=this.generation;
    this.pending(true); this.busy(true); this.error("");
    try {
      const receipt = await adminUserService.credit(operation.id, operation.amount, operation.key);
      if (generation !== this.generation) return;
      this.receipt(receipt); this.account({...this.account()!, balance: receipt.balanceAfter});
      this.operation=undefined;this.pending(false); this.amount(""); this.confirmed(false);
    } catch(e) {
      if (generation !== this.generation) return;
      // Never repeat an uncertain credit automatically. Inspect account/audit before starting over.
      if (e instanceof ApiError && [400,404,409].includes(e.status)) {this.pending(false);this.operation=undefined;}
      this.fail(e);
    } finally { if (generation === this.generation) this.busy(false); }
  };
  disconnected(): void { this.generation++; this.users([]); this.selected(null); this.account(null); this.receipt(null); }
}
