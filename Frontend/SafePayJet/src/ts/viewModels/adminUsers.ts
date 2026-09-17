import * as ko from "knockout";
import { adminUserService, AdminUser, AdminAccount } from "../services/adminUserService";
import { ApiError } from "../services/apiError";

export class AdminUsersModel {
  users = ko.observableArray<AdminUser>([]);
  selected = ko.observable<AdminUser | null>(null);
  accounts = ko.observableArray<AdminAccount>([]);
  selectedAccountId = ko.observable<number | null>(null);
  account = ko.observable<AdminAccount | null>(null);
  loading = ko.observable(false);
  forbidden = ko.observable(false);
  error = ko.observable("");
  noAccount = ko.observable(false);
  private generation = 0;

  accountLabel = (a: AdminAccount): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  money = (value: string): string => {
    const [whole, fraction = ""] = value.split(".");
    return "₹" + BigInt(whole).toLocaleString("en-IN") + "." + fraction.padEnd(2, "0");
  };
  masked = (number: string): string => "•••• " + number.slice(-4);

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
    if (this.loading()) return;
    const generation = ++this.generation;
    this.loading(true); this.error("");
    try {
      const users = await adminUserService.users();
      if (generation === this.generation) this.users(users);
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
    this.generation++; this.users([]); this.selected(null); this.clearAccounts(); this.loading(false);
  }
}
