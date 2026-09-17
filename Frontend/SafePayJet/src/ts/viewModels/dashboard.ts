import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { accountService } from "../services/accountService";
import { transactionService, newIdempotencyKey } from "../services/transactionService";
import { ApiError } from "../services/apiError";
import { Account, PaymentTransaction } from "../services/types";
import {
  canCancelPayment, formatCountdown, parseExpiry, progressValue, remainingFor, remainingSeconds, riskClass, statusLabel
} from "../utils/protection";
import { armAudio, chimeForPayment } from "../utils/chime";
import "ojs/ojdialog";
import "ojs/ojbutton";
import "ojs/ojavatar";

class DashboardViewModel {
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable<number | null>(null);
  accountOption = (a: Account): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  account = ko.observable<Account | null>(null);
  transactions = ko.observableArray<PaymentTransaction>([]);
  loading = ko.observable(true);
  sessionExpired = ko.observable(false);
  accountError = ko.observable("");
  transactionError = ko.observable("");
  cancelOpen = ko.observable(false);
  cancelTarget = ko.observable<PaymentTransaction | null>(null);
  cancelError = ko.observable("");
  busy = ko.observable(false);
  now = ko.observable(Date.now());
  private generation = 0;
  private tick?: ReturnType<typeof setInterval>;
  private poll?: ReturnType<typeof setInterval>;
  private cancelKeys = new Map<number, string>();
  recent = ko.pureComputed(() => [...this.transactions()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5));
  pending = ko.pureComputed(() => this.transactions().filter(t => t.state === "PROTECTED" || t.state === "HARD_HOLD"));
  canSend = ko.pureComputed(() => !this.loading() && !this.sessionExpired() && this.account()?.status === "ACTIVE" && !this.transactionError());
  formatMoney = (amount: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(amount);
  maskAccount = (value: string): string => value ? "•••• " + value.slice(-4) : "Account number unavailable";
  accountLabel = (value: string): string => value === "SAVINGS" ? "Savings account" : value === "CURRENT" ? "Current account" : "Your account";
  formatDate = (value: string): string => {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "Date unavailable" : new Intl.DateTimeFormat("en-IN", {
      day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit"
    }).format(date);
  };
  statusLabel = (value: string): string => statusLabel(value);
  riskClass = (t: PaymentTransaction): string => riskClass(t);
  initials = (name: string): string => (name || "?").slice(0, 1).toUpperCase();
  countdownText = (tx: PaymentTransaction): string => {
    this.now();
    if (tx.state === "HARD_HOLD") return "Awaiting administrator approval. This payment will not release automatically.";
    const left = remainingFor(tx, this.now());
    if (!Number.isFinite(left)) return "This payment is in protection. Refresh for the latest status.";
    if (left > 0) return `${formatCountdown(left)} remaining — you can still cancel.`;
    return "The pause has ended. Waiting for settlement.";
  };
  canCancelRow = (tx: PaymentTransaction): boolean => canCancelPayment(tx, this.now()) && !this.busy();
  fluxPercent = (tx: PaymentTransaction): string => { this.now(); return `${progressValue(tx, this.now())}%`; };
  private message(error: unknown, resource: string): string {
    if (error instanceof ApiError && error.status === 404) return resource === "account"
      ? "Your account is not available yet. Please contact your SafePay team."
      : "Your payments are not available right now.";
    if (error instanceof ApiError && error.status === 403) return "This page is available to customer accounts only.";
    return resource === "account" ? "We couldn’t load your account. Please try again." : "We couldn’t load your recent payments. Please try again.";
  }
  private stopLive(): void {
    if (this.tick) clearInterval(this.tick);
    if (this.poll) clearInterval(this.poll);
    this.tick = undefined; this.poll = undefined;
  }
  private startLive(): void {
    this.stopLive();
    this.now(Date.now());
    this.tick = setInterval(() => this.now(Date.now()), 1000);
    this.poll = setInterval(() => { if (this.pending().length) void this.refreshPending(); }, 3000);
  }
  private async refreshPending(): Promise<void> {
    const generation = this.generation;
    try {
      const list = await transactionService.list();
      if (generation !== this.generation || !Array.isArray(list)) return;
      this.transactions(list);
      const accounts=await accountService.list();
      if(generation!==this.generation)return;
      this.accounts(accounts);this.selectAccount();
      if (!this.pending().length) this.stopLive();
    } catch { /* Keep the last good pending list; the next tick retries. */ }
  }
  load = async (): Promise<void> => {
    const generation = ++this.generation;
    this.stopLive();
    this.loading(true); this.sessionExpired(false); this.account(null); this.transactions([]);
    this.accountError(""); this.transactionError(""); this.cancelError("");
    const [account, transactions] = await Promise.allSettled([accountService.list(), transactionService.list()]);
    if (generation !== this.generation) return;
    if ([account, transactions].some(r => r.status === "rejected" && r.reason instanceof ApiError && r.reason.status === 401)) {
      this.sessionExpired(true);
      window.location.replace("/login?reason=session-expired");
    } else {
      if (account.status === "fulfilled" && account.value) { this.accounts(account.value); const chosen=account.value.find(a=>a.accountId===this.selectedAccountId()) || account.value[0]; this.selectedAccountId(chosen?.accountId || null); this.account(chosen || null); if(!chosen)this.accountError("No account is linked to this profile yet."); }
      else this.accountError(this.message(account.status === "rejected" ? account.reason : null, "account"));
      if (transactions.status === "fulfilled" && Array.isArray(transactions.value)) this.transactions(transactions.value);
      else this.transactionError(this.message(transactions.status === "rejected" ? transactions.reason : null, "transactions"));
    }
    this.loading(false);
    if (this.pending().length) this.startLive();
    AccUtils.announce(this.sessionExpired() ? "Please sign in to view your dashboard." : "Dashboard updated.");
  };
  requestCancel = (tx: PaymentTransaction): void => {
    if (!this.canCancelRow(tx)) return;
    armAudio();
    this.cancelTarget(tx); this.cancelOpen(true); this.cancelError("");
  };
  closeCancel = (): void => { this.cancelOpen(false); this.cancelTarget(null); };
  confirmCancel = async (): Promise<void> => {
    armAudio();
    const tx = this.cancelTarget();
    this.closeCancel();
    if (!tx) return;
    this.busy(true); this.cancelError("");
    try {
      const current = await transactionService.get(tx.transactionId);
      this.replaceRow(current);
      if (!canCancelPayment(current, Date.now())) return;
      let key = this.cancelKeys.get(current.transactionId);
      if (!key) { key = newIdempotencyKey(); this.cancelKeys.set(current.transactionId, key); }
      const cancelled = await transactionService.cancel(current.transactionId, key);
      this.replaceRow(cancelled);
      chimeForPayment(cancelled);
    } catch (error) {
      this.cancelError(error instanceof ApiError && [400, 403, 404, 409].includes(error.status)
        ? error.message : "We couldn’t cancel this payment. Refresh and try again.");
    } finally { this.busy(false); }
  };
  private replaceRow(row: PaymentTransaction): void {
    this.transactions(this.transactions().map((item) => item.transactionId === row.transactionId ? row : item));
    if (!this.pending().length) this.stopLive();
  }
  selectAccount = (): void => {this.account(this.accounts().find(a=>a.accountId===this.selectedAccountId()) || null);};
  connected(): void { document.title = "Dashboard | SafePay"; void this.load(); }
  disconnected(): void { this.generation++; this.stopLive(); }
}
export = DashboardViewModel;
