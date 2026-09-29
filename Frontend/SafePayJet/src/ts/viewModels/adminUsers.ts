import * as ko from "knockout";
import { adminUserService, AdminUser, AdminAccount } from "../services/adminUserService";
import { ApiError } from "../services/apiError";

export class AdminUsersModel {
  users = ko.observableArray<AdminUser>([]);
  searchBy = ko.observable("id");
  searchQuery = ko.observable("");
  searched = ko.observable(false);
  selected = ko.observable<AdminUser | null>(null);
  accounts = ko.observableArray<AdminAccount>([]);
  selectedAccountId = ko.observable<number | null>(null);
  account = ko.observable<AdminAccount | null>(null);
  loading = ko.observable(false);
  forbidden = ko.observable(false);
  error = ko.observable("");
  noAccount = ko.observable(false);
  private generation = 0;
  private poll?: ReturnType<typeof setInterval>;
  private refreshing=false;
  private alive=true;

  startLive():void { if(!this.poll)this.poll=setInterval(()=>void this.refreshBalances(),3000); }
  refreshBalances=async():Promise<void>=>{
    const user=this.selected();const generation=this.generation;
    if(!user || !this.alive || this.loading() || this.refreshing || this.forbidden() || document.visibilityState==="hidden")return;
    this.refreshing=true;
    try {
      const accounts=await adminUserService.accounts(user.userId);
      if(!this.alive || generation!==this.generation)return;
      this.accounts(accounts);this.selectAccount();this.noAccount(!accounts.length);this.error("");
    } catch(error) {if(this.alive && generation===this.generation)this.fail(error);}
    finally {this.refreshing=false;}
  };

  accountLabel = (a: AdminAccount): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  money = (value: string): string => {
    const [whole, fraction = ""] = value.split(".");
    return "₹" + BigInt(whole).toLocaleString("en-IN") + "." + fraction.padEnd(2, "0");
  };
  masked = (number: string): string => "•••• " + number.slice(-4);
  when = (value: string): string => value && Number.isFinite(Date.parse(value))
    ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";

  resetSearch = (): void => {
    this.generation++; this.searchQuery(""); this.searched(false); this.users([]);
    this.selected(null); this.clearAccounts(); this.noAccount(false); this.error(""); this.loading(false);
  };

  private clearAccounts(): void {
    this.accounts([]); this.selectedAccountId(null); this.account(null);
  }
  private fail(error: unknown): void {
    if (error instanceof ApiError && error.status === 401) window.location.replace("/admin/login");
    else if (error instanceof ApiError && error.status === 403) {
      this.forbidden(true); this.users([]); this.selected(null); this.clearAccounts();
    } else {
      this.error(error instanceof ApiError && [400, 404, 409].includes(error.status)
        ? error.message : "Unable to load users or account details. Please try again.");
    }
  }
  load = async (): Promise<void> => {
    if (this.loading() || this.forbidden() || !this.alive) return;
    const by = this.searchBy();
    const query = by === "email" ? this.searchQuery().trim().toLowerCase() : this.searchQuery().trim();
    const generation = ++this.generation;
    this.users([]); this.selected(null); this.clearAccounts(); this.noAccount(false); this.searched(false);
    if (!query || query.length > 150 || /[\u0000-\u001f\u007f]/.test(query)) {
      this.error("Enter a user ID, exact first/full name or email (maximum 150 characters)."); return;
    }
    if (by === "id" && (!/^[0-9]+$/.test(query) || !Number.isSafeInteger(Number(query)) || Number(query) <= 0)) {
      this.error("Enter a valid positive user ID."); return;
    }
    this.searchQuery(query);
    this.loading(true); this.error("");
    try {
      const users = await adminUserService.searchUsers(by, query);
      if (generation === this.generation) { this.users(users); this.searched(true); }
    } catch (error) {
      if (generation === this.generation) this.fail(error);
    } finally {
      if (generation === this.generation) this.loading(false);
    }
  };
  select = async (user: AdminUser): Promise<void> => {
    if (this.forbidden()) return;
    const generation = ++this.generation;
    this.selected(user); this.clearAccounts(); this.noAccount(false); this.error(""); this.loading(true);
    try {
      const accounts = await adminUserService.accounts(user.userId);
      if (generation === this.generation) {
        this.accounts(accounts); this.selectedAccountId(accounts[0]?.accountId || null);
        this.selectAccount(); this.noAccount(!accounts.length);
      }
    } catch (error) {
      if (generation === this.generation) {
        if (error instanceof ApiError && error.status === 404) this.noAccount(true);
        else this.fail(error);
      }
    } finally {
      if (generation === this.generation) this.loading(false);
    }
  };
  selectAccount = (): void => {
    this.account(this.accounts().find(a => a.accountId === this.selectedAccountId()) || null);
  };
  disconnected(): void {
    this.alive=false;if(this.poll)clearInterval(this.poll);
    this.generation++; this.users([]); this.selected(null); this.clearAccounts(); this.loading(false);
  }
}
