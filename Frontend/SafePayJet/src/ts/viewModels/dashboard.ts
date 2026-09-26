import * as ko from "knockout";
import * as AccUtils from "../accUtils";
import { accountService, AccountFunds } from "../services/accountService";
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
  funds = ko.observable<AccountFunds | null>(null);
  fundsError = ko.observable("");
  fundsLoading = ko.observable(false);
  private fundsGeneration = 0;
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
  private refreshing = false;
  private alive = true;
  private onFocus = (): void => { void this.refreshPending(); };
  private tick?: ReturnType<typeof setInterval>;
  private poll?: ReturnType<typeof setInterval>;
  private cancelKeys = new Map<number, string>();
  recent = ko.pureComputed(() => this.transactions().filter(t => !this.selectedAccountId() || !t.fromAccountId
    || t.fromAccountId === this.selectedAccountId() || t.toAccountId === this.selectedAccountId())
    .slice().sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 5));
  pending = ko.pureComputed(() => this.transactions().filter(t => t.state === "PROTECTED" || t.state === "HARD_HOLD"));
  heldPaymentCount = ko.pureComputed<number | null>(() => {
    if (this.loading() || this.transactionError() || !this.account() || !this.selectedAccountId()) return null;
    return this.pending().filter(tx => tx.fromAccountId === this.selectedAccountId()).length;
  });
  canSend = ko.pureComputed(() => !this.loading() && !this.sessionExpired() && this.account()?.status === "ACTIVE" && !this.transactionError() && !!this.funds() && !this.fundsError());
  formatMoney = (amount: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(amount);
  // The legacy endpoint lists outgoing payments; only explicit CREDIT records are incoming.
  isCredit = (tx: PaymentTransaction): boolean => tx.direction === "CREDIT"
    || (!!tx.toAccountId && tx.toAccountId === this.selectedAccountId() && tx.fromAccountId !== this.selectedAccountId());
  displayName = (tx: PaymentTransaction): string => this.isCredit(tx)
    ? tx.senderName || tx.counterpartyName || tx.beneficiaryName : tx.counterpartyName || tx.beneficiaryName;
  signedAmount = (tx: PaymentTransaction): string =>
    (tx.state === "SETTLED" ? (this.isCredit(tx) ? "+ " : "− ") : "") + this.formatMoney(tx.amount);
  amountClass = (tx: PaymentTransaction): string => tx.state !== "SETTLED" ? "" : this.isCredit(tx) ? "amount-credit" : "amount-debit";
  directionLabel = (tx: PaymentTransaction): string => this.isCredit(tx) ? "Incoming · credit" : "Outgoing · debit";
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
    this.poll = setInterval(() => void this.refreshPending(), 3000);
  }
  private async refreshPending(): Promise<void> {
    if (!this.alive || this.refreshing || this.loading() || this.busy() || this.sessionExpired() || document.visibilityState === "hidden") return;
    const generation = this.generation;
    this.refreshing = true;
    try {
      const [list, accounts] = await Promise.all([transactionService.list(), accountService.list()]);
      if (generation !== this.generation || !Array.isArray(list)) return;
      this.transactions(list);
      this.accounts(accounts);this.account(accounts.find(a => a.accountId === this.selectedAccountId()) || null);
      await this.refreshFunds();
      if (generation !== this.generation) return;
      this.accountError(""); this.transactionError("");
    } catch (error) {
      if (generation !== this.generation) return;
      if (error instanceof ApiError && error.status === 401) {
        this.sessionExpired(true); this.stopLive(); this.account(null); this.accounts([]); this.transactions([]); this.funds(null); this.fundsGeneration++;
        window.location.replace("/login?reason=session-expired");
      } else this.transactionError("Live updates are temporarily unavailable. Showing the last loaded balances and payments; retrying automatically.");
    } finally { this.refreshing = false; }
  }
  load = async (): Promise<void> => {
    const generation = ++this.generation;
    this.stopLive();
    this.loading(true); this.sessionExpired(false); this.account(null); this.transactions([]);
    this.fundsGeneration++; this.funds(null); this.fundsError("");
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
    if (!this.sessionExpired()) await this.refreshFunds();
    if (!this.alive || generation !== this.generation) return;
    this.loading(false);
    if (!this.sessionExpired()) this.startLive();
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
    } finally { this.busy(false); if (this.alive) void this.refreshFunds(); }
  };
  private replaceRow(row: PaymentTransaction): void {
    this.transactions(this.transactions().map((item) => item.transactionId === row.transactionId ? row : item));
  }
  refreshFunds = async (): Promise<void> => {
    const id = this.account()?.accountId;
    const generation = ++this.fundsGeneration;
    if (this.funds()?.accountId !== id) this.funds(null);
    this.fundsError(""); this.fundsLoading(!!id);
    if (!id) return;
    try {
      const funds = await accountService.funds(id);
      if (!this.alive || generation !== this.fundsGeneration || this.account()?.accountId !== id) return;
      if (funds?.accountId !== id || ![funds.balance, funds.reservedBalance, funds.availableToTransfer].every(Number.isFinite) || funds.availableToTransfer < 0) throw new Error("Invalid funds response");
      this.funds(funds);
    } catch (error) {
      if (!this.alive || generation !== this.fundsGeneration || this.account()?.accountId !== id) return;
      this.funds(null);
      this.fundsError("We couldn’t load your current balance. Please try again.");
      if (error instanceof ApiError && error.status === 401) {
        this.sessionExpired(true); this.stopLive(); this.account(null); this.accounts([]); this.transactions([]);
        window.location.replace("/login?reason=session-expired");
      }
    } finally { if (generation === this.fundsGeneration) this.fundsLoading(false); }
  };
  selectAccount = (): void => {
    this.account(this.accounts().find(a=>a.accountId===this.selectedAccountId()) || null);
    this.funds(null); void this.refreshFunds();
  };
  connected(): void { this.alive=true; document.title = "Dashboard | SafePay"; window.addEventListener?.("focus",this.onFocus); void this.load(); }
  disconnected(): void { this.alive=false; this.generation++; this.fundsGeneration++; this.stopLive(); window.removeEventListener?.("focus",this.onFocus); }
}
export = DashboardViewModel;
