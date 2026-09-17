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
  now = ko.observable(Date.now());
  selected = ko.observable<PaymentTransaction | null>(null);
  detailOpen = ko.observable(false);
  cancelOpen = ko.observable(false);
  cancelTarget = ko.observable<PaymentTransaction | null>(null);
  busy = ko.observable(false);
  private alive=true;private revision=0;private polling=false;
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

  private message(error: unknown): string {
    if(error instanceof ApiError && error.status===401){this.stopLive();this.transactions([]);this.selected(null);window.location.replace("/login?reason=session-expired");}
    if (error instanceof ApiError && [400, 401, 403, 404, 409].includes(error.status)) return error.message;
    return "Could not load transactions.";
  }

  private stopLive(): void {
    if (this.tick) clearInterval(this.tick);
    if (this.poll) clearInterval(this.poll);
    this.tick = undefined; this.poll = undefined;
  }

  private startLive(): void {
    this.stopLive();
    this.now(Date.now());
    if (!this.pendingCount()) return;
    this.tick = setInterval(() => this.now(Date.now()), 1000);
    this.poll = setInterval(() => void this.refresh(), 3000);
  }

  private async refresh(): Promise<void> {
    if(this.polling || this.loading() || this.busy())return;const revision=this.revision;this.polling=true;
    try {
      const list = await transactionService.list();
      if (!this.alive || revision!==this.revision || !Array.isArray(list)) return;
      this.transactions(list);
      for (const row of list) chimeIfSettled(row);
      const selected = this.selected();
      if (selected) this.selected(list.find((row) => row.transactionId === selected.transactionId) || selected);
      if (!this.pendingCount()) this.stopLive();
    } catch(e) {if(this.alive && revision===this.revision)this.error(this.message(e));}
    finally{this.polling=false;}
  }

  openDetail = (tx: PaymentTransaction): void => { this.selected(tx); this.detailOpen(true); };
  closeDetail = (): void => { this.detailOpen(false); };

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
    this.error(""); this.busy(true);
    try {
      const current = await transactionService.get(transaction.transactionId);
      this.replaceRow(current);
      if (!canCancelPayment(current, Date.now())) return;
      let key = this.cancelKeys.get(current.transactionId);
      if (!key) { key = newIdempotencyKey(); this.cancelKeys.set(current.transactionId, key); }
      this.replaceRow(await transactionService.cancel(current.transactionId, key));
    } catch (error) { this.error(error instanceof ApiError ? error.message : "Could not cancel transaction."); }
    finally { this.busy(false); }
  };

  private replaceRow(row: PaymentTransaction): void {
    this.transactions(this.transactions().map((item) => item.transactionId === row.transactionId ? row : item));
    if (this.selected()?.transactionId === row.transactionId) this.selected(row);
    if (row.state === "CANCELLED" || row.state === "REJECTED") chimeForPayment(row);
    else chimeIfSettled(row);
    if (!this.pendingCount()) this.stopLive();
  }

  canCancel = (transaction: PaymentTransaction): boolean => canCancelPayment(transaction, this.now()) && !this.busy();
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
  disconnected(): void { this.alive=false;this.revision++;this.transactions([]);this.selected(null);this.stopLive();  }
}
export = TransactionsViewModel;
