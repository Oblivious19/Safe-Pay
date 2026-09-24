import * as ko from "knockout";
import app from "../appController";
import * as AccUtils from "../accUtils";
import { PaymentTransaction } from "../services/types";
import { transactionService, newIdempotencyKey } from "../services/transactionService";
import { ApiError } from "../services/apiError";
import {
  canCancelPayment, explainReasons, formatCountdown, needsVerification, parseExpiry, progressValue,
  remainingFor, remainingSeconds, resultTitle, riskClass, statusLabel, tierLabel
} from "../utils/protection";
import { armAudio, chimeForPayment, chimeIfSettled, rememberSettled } from "../utils/chime";
import "ojs/ojdialog";
import "ojs/ojbutton";
import "ojs/ojavatar";
import "ojs/ojdrawerpopup";

class TransactionsViewModel {
  signedInEmail = ko.pureComputed(() => app.profile()?.email || "");
  transactions = ko.observableArray<PaymentTransaction>([]);
  loading = ko.observable(false);
  error = ko.observable("");
  detailError = ko.observable("");
  query = ko.observable("");
  filter = ko.observable("all");
  page = ko.observable(1);
  pageReveal = ko.observable(0);
  readonly pageSize = 8;
  filtered = ko.pureComputed(() => {
    const query = this.query().trim().toLowerCase();
    return this.transactions().filter(tx => {
      const state = this.filter();
      const matches = state === "all" || (state === "pending" ? ["PROTECTED", "HARD_HOLD", "CREATED", "AUTHORIZED", "RISK_ASSESSED"].includes(tx.state) : state === "cancelled" ? ["CANCELLED", "REJECTED"].includes(tx.state) : tx.state === "SETTLED");
      return matches && (!query || [tx.counterpartyName, tx.beneficiaryName, tx.transactionRef, tx.purpose, String(tx.amount)].some(value => (value || "").toLowerCase().includes(query)));
    }).slice().sort((a,b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
  });
  pageCount = ko.pureComputed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize)));
  currentPage = ko.pureComputed(() => Math.min(this.page(), this.pageCount()));
  displayedTransactions = ko.pureComputed(() => this.filtered().slice((this.currentPage() - 1) * this.pageSize, this.currentPage() * this.pageSize));
  pageLabel = ko.pureComputed(() => `Page ${this.currentPage()} of ${this.pageCount()} · ${this.filtered().length} payments`);
  previousPage = (): void => { this.page(Math.max(1, this.currentPage() - 1)); this.pageReveal(this.pageReveal() + 1); };
  nextPage = (): void => { this.page(Math.min(this.pageCount(), this.currentPage() + 1)); this.pageReveal(this.pageReveal() + 1); };
  private filters = [this.query.subscribe(() => this.page(1)), this.filter.subscribe(() => this.page(1))];
  now = ko.observable(Date.now());
  selected = ko.observable<PaymentTransaction | null>(null);
  detailOpen = ko.observable(false);
  cancelOpen = ko.observable(false);
  cancelTarget = ko.observable<PaymentTransaction | null>(null);
  busy = ko.observable(false);
  private alive=true;private revision=0;private polling=false;
  private sessionEnded=false;
  private tick?: ReturnType<typeof setInterval>;
  private poll?: ReturnType<typeof setInterval>;
  private cancelKeys = new Map<number, string>();
  pendingCount = ko.pureComputed(() => this.transactions().filter((t) => t.state === "PROTECTED" || t.state === "HARD_HOLD").length);
  selectedTitle = ko.pureComputed(() => resultTitle(this.selected(), remainingFor(this.selected(), this.now())));
  selectedReasons = ko.pureComputed(() => explainReasons(this.selected()?.riskReason));
  selectedCountdown = ko.pureComputed(() => {
    const tx = this.selected();
    if (!tx || tx.state !== "PROTECTED") return "";
    const left = remainingFor(tx, this.now());
    if (!Number.isFinite(left)) return "Expiry unavailable.";
    if (left > 0) return `${formatCountdown(left)} remaining`;
    return "Settling…";
  });

  load = async (): Promise<void> => {
    if(this.loading())return;const revision=++this.revision;
    this.loading(true); this.error("");
    try {
      const list = await transactionService.list();
      if(!this.alive || revision!==this.revision)return;
      if (!Array.isArray(list)) throw new Error("list");
      this.transactions(list);
    } catch (error) {
      if(!this.alive || revision!==this.revision)return;
      this.error(this.message(error));
    }
    this.loading(false);
    try {
      for (const row of this.transactions()) rememberSettled(row);
      this.startLive();
    } catch { /* keep the list */ }
  };

  private message(error: unknown, action = "load"): string {
    if(error instanceof ApiError && error.status===401){this.sessionEnded=true;this.stopLive();this.transactions([]);this.selected(null);this.detailOpen(false);window.location.replace("/login?reason=session-expired");return "Your session has ended. Please sign in again to see your payments.";}
    if (error instanceof ApiError && error.status === 403) return "We couldn’t access this payment. Refresh your session or contact your bank if this continues.";
    if (error instanceof ApiError && error.status === 404) return "This payment is no longer available. Refresh your history to see the latest records.";
    if (error instanceof ApiError && error.status === 409) return "The payment status has changed. It will update automatically to show whether it settled or was cancelled.";
    return action === "cancel" ? "We couldn’t confirm the cancellation. We’ll check the status automatically; the payment may already have settled." : "Your payment history is temporarily unavailable. Check your connection; we’ll try again automatically. Your existing payments are unchanged.";
  }

  private stopLive(): void {
    if (this.tick) clearInterval(this.tick);
    if (this.poll) clearInterval(this.poll);
    this.tick = undefined; this.poll = undefined;
  }

  private startLive(): void {
    this.stopLive();
    if(this.sessionEnded || !this.alive)return;
    this.now(Date.now());
    this.tick = setInterval(() => this.now(Date.now()), 1000);
    this.poll = setInterval(() => void this.refresh(), 3000);
  }

  private async refresh(): Promise<void> {
    if(!this.alive || this.polling || this.loading() || this.busy() || document.visibilityState === "hidden")return;const revision=this.revision;this.polling=true;
    try {
      const list = await transactionService.list();
      if (!this.alive || revision!==this.revision || !Array.isArray(list)) return;
      this.transactions(list); this.error(""); this.detailError("");
      for (const row of list) chimeIfSettled(row);
      const selected = this.selected();
      if (selected) this.selected(list.find((row) => row.transactionId === selected.transactionId) || selected);
    } catch(e) {if(this.alive && revision===this.revision){const message=this.message(e);if(this.detailOpen())this.detailError(message);else this.error(message);}}
    finally{this.polling=false;}
  }

  openDetail = (tx: PaymentTransaction): void => { this.detailError(""); this.selected(tx); this.detailOpen(true); };
  closeDetail = (): void => { this.detailOpen(false); this.detailError(""); };
  refreshDetail = async (): Promise<void> => {
    const tx = this.selected(); if (!tx || this.busy()) return;
    this.busy(true); this.detailError("");
    try { const current = await transactionService.get(tx.transactionId); if (this.alive) this.replaceRow(current); }
    catch(e) { if(this.alive) this.detailError(this.message(e)); }
    finally { if(this.alive) this.busy(false); }
  };

  requestCancel = (tx: PaymentTransaction): void => {
    if (!this.canCancel(tx) || this.busy()) return;
    armAudio();
    this.cancelTarget(tx); this.cancelOpen(true);
  };
  closeCancel = (): void => { this.cancelOpen(false); this.cancelTarget(null); };
  confirmCancel = async (): Promise<void> => {
    armAudio();
    const tx = this.cancelTarget();
    this.closeCancel();
    if (tx) await this.cancel(tx);
  };

  cancel = async (transaction: PaymentTransaction): Promise<void> => {
    if(this.busy())return;
    this.error(""); this.detailError(""); this.busy(true);
    try {
      const current = await transactionService.get(transaction.transactionId);
      if(!this.alive)return;
      this.replaceRow(current);
      if (!canCancelPayment(current, Date.now())) return;
      let key = this.cancelKeys.get(current.transactionId);
      if (!key) { key = newIdempotencyKey(); this.cancelKeys.set(current.transactionId, key); }
      const cancelled = await transactionService.cancel(current.transactionId, key);
      if(this.alive)this.replaceRow(cancelled);
    } catch (error) { if(this.alive){const message=this.message(error,"cancel"); if(this.detailOpen())this.detailError(message);else this.error(message);} }
    finally { this.busy(false); }
  };

  private replaceRow(row: PaymentTransaction): void {
    this.transactions(this.transactions().map((item) => item.transactionId === row.transactionId ? row : item));
    if (this.selected()?.transactionId === row.transactionId) this.selected(row);
    if (row.state === "CANCELLED" || row.state === "REJECTED") chimeForPayment(row);
    else chimeIfSettled(row);
  }

  canCancel = (transaction: PaymentTransaction): boolean => canCancelPayment(transaction, this.now()) && !this.busy();
  isCredit = (tx: PaymentTransaction): boolean => tx.direction === "CREDIT";
  displayName = (tx: PaymentTransaction): string => tx.counterpartyName || tx.beneficiaryName || tx.transactionRef;
  directionLabel = (tx: PaymentTransaction): string => this.isCredit(tx) ? "Incoming · credit" : "Outgoing · debit";
  directionClass = (tx: PaymentTransaction): string => this.isCredit(tx) ? "money-in" : "money-out";
  directionArrow = (tx: PaymentTransaction): string => this.isCredit(tx) ? "↙" : "↗";
  signedAmount = (tx: PaymentTransaction): string => (tx.state === "SETTLED" ? this.isCredit(tx) ? "+ " : "− " : "") + this.formatMoney(tx.amount);
  needsCall = (transaction: PaymentTransaction): boolean => needsVerification(transaction);
  fluxPercent = (tx: PaymentTransaction): string => { this.now(); return `${progressValue(tx, this.now())}%`; };
  countdownText = (tx: PaymentTransaction): string => {
    this.now();
    if (tx.state !== "PROTECTED") return "";
    const left = remainingFor(tx, this.now());
    if (!Number.isFinite(left)) return "Protection time unavailable";
    if (left > 0) return `${formatCountdown(left)} left`;
    return "Settling";
  };
  formatMoney = (amount: number): string =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number.isFinite(amount) ? amount : 0);
  formatDate = (value: string): string => {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Date unavailable";
    try {
      return new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "medium" }).format(date);
    } catch { return "Date unavailable"; }
  };
  maskAccountNumber = (accountNumber: string): string =>
    accountNumber ? `••••${accountNumber.slice(-4)}` : "—";
  statusLabel = (value: string): string => statusLabel(value);
  tierLabel = (value: string): string => tierLabel(value);
  riskClass = (t: PaymentTransaction): string => riskClass(t);
  initials = (name: string): string => (name || "?").slice(0, 1).toUpperCase();
  connected(): void { AccUtils.announce("Transactions page loaded."); document.title = "Transactions | SafePay"; void this.load(); }
  disconnected(): void { this.alive=false;this.revision++;this.transactions([]);this.selected(null);this.stopLive();this.filters.forEach(subscription=>subscription.dispose()); }
}
export = TransactionsViewModel;
