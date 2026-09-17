import * as ko from "knockout";
import app from "../appController";
import { accountService } from "../services/accountService";
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
  selectAccount = (): void => {this.account(this.accounts().find(a=>a.accountId===this.selectedAccountId()) || null);};
  connected(): void { this.alive=true; document.title = "Profile | SafePay"; void this.load(); }
  disconnected(): void { this.alive = false; }
}
export = ProfileViewModel;
