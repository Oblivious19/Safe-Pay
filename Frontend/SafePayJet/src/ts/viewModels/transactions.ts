import * as ko from "knockout";
import app from "../appController";
import * as AccUtils from "../accUtils";
import { cancelTransaction, verifyTransaction, PaymentTransaction, transactionService } from "../services/api";
import { TransactionState } from "../services/types";
import { RefreshLoop } from "../services/refreshLoop";
import { endExpiredSession } from "../services/customerSession";

class TransactionsViewModel {
  signedInEmail = ko.pureComputed(() => app.profile()?.email || "");
  transactions = ko.observableArray<PaymentTransaction>([]);
  stateFilter = ko.observable("");
  loading = ko.observable(false);
  busyId = ko.observable<number | null>(null);
  error = ko.observable("");
  refreshError = ko.observable("");
  success = ko.observable("");
  verificationTarget = ko.observable<PaymentTransaction | null>(null);
  password = ko.observable("");
  private active = false;
  private lifecycle = 0;
  private poll = new RefreshLoop(
    async () => { this.loading(true); return transactionService.list(this.stateFilter() as TransactionState || undefined); },
    transactions => { this.transactions(transactions); this.loading(false); this.refreshError(""); },
    error => { this.loading(false); if (!this.expired(error)) this.refreshError(error instanceof Error ? error.message : "Could not load transactions."); },
    () => this.transactions().some(t => this.isPending(t))
  );
  load = (): Promise<void> => this.poll.refresh();
  private expired(error: unknown): boolean {
    return endExpiredSession(error, () => {
      this.active = false; this.lifecycle++;
      this.transactions([]); this.password(""); this.verificationTarget(null); this.poll.stop();
      this.error("Your session has expired. Please sign in again.");
    });
  }
  isPending = (transaction: PaymentTransaction): boolean =>
    !["CANCELLED", "SETTLED"].includes(transaction.state);
  canCancel = (transaction: PaymentTransaction): boolean => transaction.canCancel ?? transaction.state === "PROTECTED";
  canVerify = (transaction: PaymentTransaction): boolean => transaction.state === "HARD_HOLD";

  cancel = async (transaction: PaymentTransaction): Promise<void> => {
    if (this.busyId() !== null) return;
    const lifecycle = this.lifecycle;
    this.poll.invalidate(); this.loading(false); this.busyId(transaction.transactionId); this.error(""); this.success("");
    try {
      const result = await cancelTransaction(transaction.transactionId);
      if (!this.active || lifecycle !== this.lifecycle) return;
      this.replace(result); this.success("Payment cancelled.");
    } catch (error) {
      if (this.active && lifecycle === this.lifecycle && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not cancel transaction.");
    } finally {
      if (this.active && lifecycle === this.lifecycle) { this.busyId(null); await this.poll.refresh(); }
    }
  };

  openVerification = (transaction: PaymentTransaction): void => {
    if (this.busyId() !== null) return;
    this.password(""); this.error(""); this.success(""); this.verificationTarget(transaction);
  };
  closeVerification = (): void => { if (this.busyId() === null) { this.password(""); this.verificationTarget(null); } };
  verify = async (): Promise<void> => {
    const target = this.verificationTarget();
    if (!target || this.busyId() !== null) return;
    if (!this.password().trim()) { this.error("Enter your account password to verify this payment."); return; }
    const lifecycle = this.lifecycle;
    const password = this.password();
    this.password(""); this.poll.invalidate(); this.loading(false);
    this.busyId(target.transactionId); this.error(""); this.success("");
    try {
      const result = await verifyTransaction(target.transactionId, password);
      if (!this.active || lifecycle !== this.lifecycle) return;
      this.replace(result); this.verificationTarget(null);
      this.success("Payment verified. Current status: " + result.state + ".");
    } catch (error) {
      if (this.active && lifecycle === this.lifecycle && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not verify payment.");
    } finally {
      if (this.active && lifecycle === this.lifecycle) { this.busyId(null); await this.poll.refresh(); }
    }
  };
  private replace(transaction: PaymentTransaction): void {
    this.transactions(this.transactions().map(t => t.transactionId === transaction.transactionId ? transaction : t));
  }
  formatMoney = (amount: number): string =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(amount);
  formatDate = (value: string): string =>
    value ? new Intl.DateTimeFormat("en-IN", { dateStyle: "medium", timeStyle: "medium" }).format(new Date(value)) : "—";
  maskAccountNumber = (accountNumber: string): string => accountNumber ? `••••${accountNumber.slice(-4)}` : "—";
  connected(): void {
    this.active = true; this.lifecycle++; this.busyId(null);
    AccUtils.announce("Transactions page loaded."); document.title = "Transactions | SafePay"; this.poll.start();
  }
  disconnected(): void { this.active = false; this.lifecycle++; this.password(""); this.verificationTarget(null); this.poll.stop(); }
}
export = TransactionsViewModel;
