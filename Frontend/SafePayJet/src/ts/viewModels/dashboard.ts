import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { accountService } from "../services/accountService";
import { transactionService } from "../services/transactionService";
import { ApiError } from "../services/apiError";
import { Account, PaymentTransaction } from "../services/types";

class DashboardViewModel {
  account = ko.observable<Account | null>(null);
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable("");
  transactions = ko.observableArray<PaymentTransaction>([]);
  loading = ko.observable(true);
  sessionExpired = ko.observable(false);
  accountError = ko.observable("");
  transactionError = ko.observable("");
  private generation = 0;
  recent = ko.pureComputed(() => [...this.transactions()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5));
  pending = ko.pureComputed(() => this.transactions().filter(t => t.state === "PROTECTED" || t.state === "HARD_HOLD"));
  canSend = ko.pureComputed(() => !this.loading() && !this.sessionExpired() && this.account()?.status === "ACTIVE" && !this.transactionError());
  formatMoney = (amount: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(amount);
  maskAccount = (value: string): string => value ? "•••• " + value.slice(-4) : "Account number unavailable";
  accountLabel = (value: string): string => value === "SAVINGS" ? "Savings account" : value === "CURRENT" ? "Current account" : "Your account";
  accountOption = (account: Account): string => `${this.accountLabel(account.accountType)} ${this.maskAccount(account.accountNumber)} — ${account.status}`;
  sourceAccount = (id: number): string => {
    const account = this.accounts().find(item => item.accountId === id);
    return account ? this.accountOption(account) : "Account unavailable";
  };
  constructor() {
    this.selectedAccountId.subscribe(value => this.account(this.accounts().find(item => item.accountId === Number(value)) || null));
  }
  formatDate = (value: string): string => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date unavailable" : new Intl.DateTimeFormat("en-IN", {
      day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit"
    }).format(date);
  };
  statusLabel = (value: string): string => ({
    CREATED: "Payment started", AUTHORIZED: "Payment authorised", RISK_ASSESSED: "Payment checked",
    PROTECTED: "In protection", HARD_HOLD: "Verification required", SETTLED: "Completed",
    CANCELLED: "Cancelled", REJECTED: "Not approved", RELEASED: "Processing"
  } as Record<string, string>)[value] || "Processing";
  riskClass = (t: PaymentTransaction): string => t.state === "HARD_HOLD" || ["VERY_HIGH", "HARD_HOLD"].includes(t.riskTier)
    ? "risk-red" : t.riskTier === "HIGH" ? "risk-orange" : t.riskTier === "MEDIUM" ? "risk-amber" : "risk-neutral";
  private message(error: unknown, resource: string): string {
    if (error instanceof ApiError && error.status === 404) return resource === "account"
      ? "Your account is not available yet. Please contact your SafePay team."
      : "Your payments are not available right now.";
    if (error instanceof ApiError && error.status === 403) return "This page is available to customer accounts only.";
    return resource === "account" ? "We couldn’t load your account. Please try again." : "We couldn’t load your recent payments. Please try again.";
  }
  load = async (): Promise<void> => {
    const generation = ++this.generation;
    this.loading(true); this.sessionExpired(false); this.account(null); this.transactions([]);
    this.accountError(""); this.transactionError("");
    const selectedId = this.selectedAccountId();
    const [account, transactions] = await Promise.allSettled([accountService.list(), transactionService.list()]);
    if (generation !== this.generation) return;
    if ([account, transactions].some(r => r.status === "rejected" && r.reason instanceof ApiError && r.reason.status === 401)) {
      this.sessionExpired(true);
      this.accounts([]); this.selectedAccountId("");
      window.location.replace("/login?reason=session-expired");
    } else {
      if (account.status === "fulfilled" && Array.isArray(account.value) && account.value.length) {
        this.accounts(account.value);
        const selected = account.value.find(item => item.accountId === Number(selectedId)) || account.value[0];
        this.selectedAccountId(String(selected.accountId)); this.account(selected);
      } else {
        this.accounts([]); this.selectedAccountId("");
        this.accountError(this.message(account.status === "rejected" ? account.reason : null, "account"));
      }
      if (transactions.status === "fulfilled" && Array.isArray(transactions.value)) this.transactions(transactions.value);
      else this.transactionError(this.message(transactions.status === "rejected" ? transactions.reason : null, "transactions"));
    }
    this.loading(false);
    AccUtils.announce(this.sessionExpired() ? "Please sign in to view your dashboard." : "Dashboard updated.");
  };
  connected(): void { document.title = "Dashboard | SafePay"; void this.load(); }
  disconnected(): void { this.generation++; }
}
export = DashboardViewModel;
