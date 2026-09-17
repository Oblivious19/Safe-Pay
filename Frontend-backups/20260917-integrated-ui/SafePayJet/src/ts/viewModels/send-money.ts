import * as ko from "knockout";
import app from "../appController";
import * as AccUtils from "../accUtils";
import { Account, Beneficiary, getAccounts, getBeneficiaries, initiateTransaction, PaymentTransaction, transactionService, cancelTransaction, newIdempotencyKey, apiClient } from "../services/api";
import { ApiError } from "../services/apiError";
import { readPendingPayment, rememberPendingPayment, clearPendingPayment } from "../services/pendingPayment";
import { TransactionRequest } from "../services/types";
import { RefreshLoop } from "../services/refreshLoop";
import { endExpiredSession } from "../services/customerSession";

class SendMoneyViewModel {
  signedInEmail = ko.pureComputed(() => app.profile()?.email || "");
  accounts = ko.observableArray<Account>([]);
  beneficiaries = ko.observableArray<Beneficiary>([]);
  accountId = ko.observable("");
  beneficiaryId = ko.observable("");
  amount = ko.observable("");
  purpose = ko.observable("");
  loading = ko.observable(false);
  sending = ko.observable(false);
  error = ko.observable("");
  result = ko.observable<PaymentTransaction | null>(null);
  statusError = ko.observable("");
  reviewDraft = ko.observable<TransactionRequest | null>(null);
  reviewAccountLabel = ko.observable("");
  reviewBeneficiaryLabel = ko.observable("");
  private restorePending(): boolean {
    const saved = readPendingPayment(apiClient.sessionRevision());
    if (!saved) return false;
    this.attempt = { ...saved.request }; this.attemptKey = saved.key; this.attemptSession = saved.session;
    this.reviewDraft({ ...saved.request }); this.reviewAccountLabel(saved.accountLabel); this.reviewBeneficiaryLabel(saved.beneficiaryLabel);
    this.attemptLocked(true); this.amount(saved.request.amount); this.purpose(saved.request.purpose || "");
    return true;
  }
  private discardOldSession(): void {
    this.attempt = null; this.attemptKey = undefined; this.attemptLocked(false); this.reviewDraft(null);
    this.accounts([]); this.beneficiaries([]); this.result(null); this.poll.stop(); this.stopClock();
    this.error("Your session changed. Reload your accounts before reviewing another payment.");
  }
  attemptLocked = ko.observable(false);
  cancelling = ko.observable(false);
  private attempt: TransactionRequest | null = null;
  private attemptKey: string | undefined;
  private attemptSession = -1;
  private receiptTime = 0;
  private timer?: ReturnType<typeof setInterval>;
  private clockTick = ko.observable(0);
  private monotonicNow = (): number => typeof performance === "undefined" ? Date.now() : performance.now();
  remainingSeconds = ko.pureComputed(() => {
    this.clockTick();
    const remaining = this.result()?.protectionRemainingMillis;
    return this.result()?.state === "PROTECTED" && typeof remaining === "number" && Number.isFinite(remaining)
      ? Math.max(0, Math.ceil((remaining - (this.monotonicNow() - this.receiptTime)) / 1000)) : null;
  });
  countdown = ko.pureComputed(() => this.remainingSeconds() === null ? "Refresh for the current protection status."
    : this.remainingSeconds()! > 0 ? this.remainingSeconds() + " seconds remaining (estimated)"
    : "Protection window ended. Waiting for the server's current status.");
  canCancelResult = ko.pureComputed(() => !!this.result() && (this.result()!.canCancel ?? this.result()!.state === "PROTECTED") && !this.cancelling() && !this.sending());
  decimalMoney = (value: string): string => {
    const [integer, fraction = ""] = value.split(".");
    const tail = integer.slice(-3), head = integer.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
    return "₹" + (head ? head + "," : "") + tail + "." + fraction.padEnd(2, "0");
  };
  private stopClock(): void { if (this.timer !== undefined) clearInterval(this.timer); this.timer = undefined; }
  private acceptResult(result: PaymentTransaction | null): void {
    this.receiptTime = this.monotonicNow(); this.result(result); this.clockTick(this.receiptTime);
    this.stopClock();
    if (result?.state === "PROTECTED" && typeof result.protectionRemainingMillis === "number" && typeof setInterval !== "undefined") {
      this.timer = setInterval(() => this.clockTick(this.monotonicNow()), 250);
    }
  }
  reviewPayment = (): void => {
    if (this.loading() || this.sending() || this.cancelling() || this.attemptLocked()) return;
    const account = this.selectedAccount();
    const beneficiary = this.beneficiaries().find(item => item.beneficiaryId === Number(this.beneficiaryId()));
    const amount = this.amount().trim(), purpose = this.purpose().trim();
    if (!account || account.status !== "ACTIVE" || !beneficiary || beneficiary.accountId !== account.accountId
      || !/^\d{1,16}(\.\d{1,2})?$/.test(amount) || !/[1-9]/.test(amount)) {
      this.error("Choose an active account, its beneficiary, and a positive amount with up to two decimals."); return;
    }
    if (new TextEncoder().encode(purpose).length > 255) { this.error("Keep the purpose to 255 UTF-8 bytes or fewer."); return; }
    this.reviewAccountLabel(this.accountLabel(account)); this.reviewBeneficiaryLabel(beneficiary.beneficiaryName + " · •••• " + beneficiary.bankAccountNumber.slice(-4));
    this.error(""); this.reviewDraft({ fromAccountId: account.accountId, beneficiaryId: beneficiary.beneficiaryId, amount, purpose });
  };
  editReview = (): void => { if (!this.attemptLocked() && !this.sending()) this.reviewDraft(null); };
  cancelResult = async (): Promise<void> => {
    const result = this.result();
    if (!result || !this.canCancelResult()) return;
    const lifecycle = this.lifecycle;
    this.cancelling(true); this.poll.invalidate(); this.statusError("");
    try {
      const current = await transactionService.get(result.transactionId);
      if (!this.active || lifecycle !== this.lifecycle) return;
      this.acceptResult(current);
      if (!(current.canCancel ?? current.state === "PROTECTED")) return;
      const saved = await cancelTransaction(result.transactionId);
      if (this.active && lifecycle === this.lifecycle) this.acceptResult(saved);
    } catch (error) { if (this.active && lifecycle === this.lifecycle && !this.expired(error)) this.statusError(error instanceof Error ? error.message : "Could not cancel payment. Refresh its status before retrying."); }
    finally { if (this.active && lifecycle === this.lifecycle) { this.cancelling(false); this.poll.start(); } }
  };
  accountLabel = (account: Account): string => `${account.accountType} •••• ${account.accountNumber.slice(-4)} — ${account.status} — ${new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(account.balance)}`;
  selectedAccount = ko.pureComputed(() => this.accounts().find(account => account.accountId === Number(this.accountId())));
  canSend = ko.pureComputed(() => !this.loading() && !this.sending() && !this.cancelling() && this.selectedAccount()?.status === "ACTIVE" && !!this.beneficiaryId());
  private active = false;
  private lifecycle = 0;
  private loadRevision = 0;
  private updatingSelection = false;
  constructor() {
    this.accountId.subscribe(() => {
      if (!this.updatingSelection && this.active) {
        this.poll.stop(); this.stopClock(); this.result(null); this.statusError(""); if (!this.attemptLocked()) this.reviewDraft(null);
        void this.loadBeneficiaries();
      }
    });
  }
  private poll = new RefreshLoop(
    async () => {
      const result = this.result();
      return result ? transactionService.get(result.transactionId) : null;
    },
    result => { this.acceptResult(result); this.statusError(""); },
    error => { if (!this.expired(error)) this.statusError(error instanceof Error ? error.message : "Could not refresh payment status."); },
    () => !!this.result() && !["CANCELLED", "SETTLED"].includes(this.result()!.state)
  );
  refreshStatus = (): Promise<void> => this.cancelling() || this.sending() ? Promise.resolve() : this.poll.refresh();
  private expired(error: unknown): boolean {
    return endExpiredSession(error, () => {
      this.active = false; this.lifecycle++; this.loadRevision++;
      this.accounts([]); this.beneficiaries([]); this.result(null); this.amount(""); this.purpose(""); this.poll.stop();
      this.stopClock(); this.reviewDraft(null); this.attempt = null; this.attemptKey = undefined; this.attemptLocked(false);
      this.error("Your session has expired. Please sign in again.");
    });
  }

  load = async (): Promise<void> => {
    if (this.sending() || this.cancelling() || this.reviewDraft() || this.attemptLocked()) return;
    const revision = ++this.loadRevision;
    this.loading(true); this.error(""); this.beneficiaries([]); this.beneficiaryId("");
    try {
      const accounts = await getAccounts();
      if (!this.active || revision !== this.loadRevision) return;
      this.updatingSelection = true;
      this.accounts(accounts);
      if (!accounts.some(account => account.accountId === Number(this.accountId()))) this.accountId(accounts.length ? String(accounts[0].accountId) : "");
      this.updatingSelection = false;
      await this.loadBeneficiaries();
    } catch (error) { if (this.active && revision === this.loadRevision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not load payment details."); }
    finally { if (this.active && revision === this.loadRevision) this.loading(false); }
  };

  private loadBeneficiaries = async (): Promise<void> => {
    const revision = ++this.loadRevision;
    const account = this.selectedAccount();
    this.beneficiaries([]); this.beneficiaryId(""); this.error("");
    this.loading(!!account);
    if (!account) return;
    try {
      const beneficiaries = await getBeneficiaries(undefined, false, account.accountId);
      if (!this.active || revision !== this.loadRevision) return;
      // Also check the returned association before offering a beneficiary for payment.
      const owned = beneficiaries.filter(beneficiary => beneficiary.accountId === account.accountId && beneficiary.status === "ACTIVE");
      this.beneficiaries(owned);
      if (owned.length) this.beneficiaryId(String(owned[0].beneficiaryId));
    } catch (error) { if (this.active && revision === this.loadRevision && !this.expired(error)) this.error(error instanceof Error ? error.message : "Could not load beneficiaries for this account."); }
    finally { if (this.active && revision === this.loadRevision) this.loading(false); }
  };

  send = async (): Promise<void> => {
    if (this.sending() || this.loading() || this.cancelling()) return;
    if (!this.reviewDraft()) { this.error("Review the payment details before confirming."); return; }
    const session = apiClient.sessionRevision();
    if (this.attempt && this.attemptSession !== session) { this.discardOldSession(); return; }
    const saved = readPendingPayment(session);
    if (!this.attempt && saved) {
      // Another view already sent a payment. Restore it for explicit confirmation;
      // never silently substitute it for the different draft being confirmed here.
      this.restorePending(); this.error("An earlier payment is awaiting confirmation. Review it before retrying."); return;
    }
    if (!this.attempt) {
      this.attempt = { ...this.reviewDraft()! }; this.attemptKey = newIdempotencyKey(); this.attemptSession = session;
      rememberPendingPayment({ request: this.attempt, key: this.attemptKey, session,
        accountLabel: this.reviewAccountLabel(), beneficiaryLabel: this.reviewBeneficiaryLabel() });
    }
    const request = { ...this.attempt }, key = this.attemptKey!;
    const lifecycle = this.lifecycle;
    let reloadAccounts = false;
    this.poll.stop(); this.stopClock(); this.sending(true); this.attemptLocked(true);
    this.error(""); this.statusError(""); this.result(null);
    try {
      const result = await initiateTransaction(request, key);
      if (!this.active || lifecycle !== this.lifecycle) return;
      if (apiClient.sessionRevision() !== session) { this.discardOldSession(); return; }
      this.acceptResult(result); this.poll.start(); clearPendingPayment(key, session);
      this.amount(""); this.purpose(""); this.reviewDraft(null); this.attempt = null; this.attemptKey = undefined; this.attemptLocked(false);
      reloadAccounts = !this.accounts().length;
    } catch (error) {
      if (!this.active || lifecycle !== this.lifecycle) return;
      if (apiClient.sessionRevision() !== session) { this.discardOldSession(); return; }
      if (!this.expired(error)) {
        if (error instanceof ApiError && [400, 403, 404].includes(error.status)) {
          clearPendingPayment(key, session); this.attempt = null; this.attemptKey = undefined; this.attemptLocked(false);
        }
        this.error(error instanceof Error ? error.message : "Could not confirm payment. Retry this unchanged request or check Transactions.");
      }
    } finally {
      if (this.active && lifecycle === this.lifecycle) {
        this.sending(false);
        if (reloadAccounts && apiClient.sessionRevision() === session) void this.load();
      }
    }
  };  connected(): void { this.active = true; this.lifecycle++; this.sending(false); this.cancelling(false); AccUtils.announce("Send money page loaded."); document.title = "Send Money | SafePay"; if (!this.restorePending()) void this.load(); if (this.result()) this.poll.start(); }
  disconnected(): void { this.active = false; this.lifecycle++; this.loadRevision++; this.poll.stop(); this.stopClock(); }
}
export = SendMoneyViewModel;
