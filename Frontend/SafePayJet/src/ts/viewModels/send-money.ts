import * as ko from "knockout";
import { beneficiaryService, Beneficiary } from "../services/beneficiaryService";
import { accountService, AccountFunds } from "../services/accountService";
import { Account, PaymentTransaction, TransactionRequest } from "../services/types";
import { transactionService, newIdempotencyKey } from "../services/transactionService";
import { ApiError } from "../services/apiError";
import {
  arcOffset as fluxArcOffset, canCancelPayment, CHECK_PHASES, explainReasons, fluxLetters,
  formatCountdown, isInFlight, needsVerification, parseExpiry, phaseFor, progressKind, progressValue,
  PROTECTION_PHASES, remainingFor, remainingSeconds, resultTitle, riskClass, statusLabel, tierLabel, tierRisk
} from "../utils/protection";
import { armAudio, chimeForPayment } from "../utils/chime";
import "ojs/ojprogress-circle";
import "ojs/ojdialog";
import "ojs/ojbutton";
import "ojs/ojtrain";
import "ojs/ojinputtext";
import "ojs/ojavatar";
import "ojs/ojmessages";

interface FlowRouter { go(route: { path: string; params?: { step: string } }): Promise<unknown>; }
class SendMoneyViewModel {
  step = ko.observable("beneficiary");
  beneficiaries = ko.observableArray<Beneficiary>([]);
  selected = ko.observable<Beneficiary | null>(null);
  accounts = ko.observableArray<Account>([]);
  selectedAccountId = ko.observable<number | null>(null);
  accountOption = (a: Account): string => a.accountType + " •••• " + a.accountNumber.slice(-4) + " · " + a.status;
  private allBeneficiaries: Beneficiary[] = [];
  account = ko.observable<Account | null>(null);
  funds = ko.observable<AccountFunds | null>(null);
  fundsError = ko.observable("");
  private fundsGeneration = 0;
  amount = ko.observable("");
  purpose = ko.observable("");
  amountError = ko.observable("");
  purposeError = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  loading = ko.observable(false);
  busy = ko.observable(false);
  paymentError = ko.observable("");
  result = ko.observable<PaymentTransaction | null>(null);
  attemptLocked = ko.observable(false);
  refreshing = ko.observable(false);
  cancelOpen = ko.observable(false);
  now = ko.observable(Date.now());
  processing = ko.observable(0);
  trainSteps = [
    { id: "beneficiary", label: "Recipient" },
    { id: "details", label: "Amount" },
    { id: "review", label: "Review" },
    { id: "result", label: "Status" }
  ];
  paymentMessages = ko.pureComputed(() => this.paymentError()
    ? [{ severity: "error" as const, summary: this.paymentError(), autoTimeout: 0 }] : []);
  trainStep = ko.pureComputed(() => this.step());
  payPips = ko.pureComputed(() => {
    const reached = this.step() === "result" ? 2 : this.step() === "review" ? 1 : 0;
    return [0, 1, 2].map((index) => index <= reached);
  });
  private attempt?: { key: string; body: TransactionRequest };
  private cancelKey?: string;
  private timer?: ReturnType<typeof setInterval>;
  private processTimer?: ReturnType<typeof setInterval>;
  private lastPoll = 0;


  remaining = ko.pureComputed(() => remainingFor(this.result(), this.now()));
  canCancel = ko.pureComputed(() => canCancelPayment(this.result(), this.now()) && !this.busy() && !this.refreshing());
  resultTitle = ko.pureComputed(() => resultTitle(this.result(), this.remaining()));
  countdown = ko.pureComputed(() => {
    const left = this.remaining();
    if (!Number.isFinite(left)) return "Expiry unavailable. Refresh for the latest status.";
    if (left > 0) return `${formatCountdown(left)} remaining`;
    return "Protection window ended. Waiting for the server’s final status.";
  });
  countdownClock = ko.pureComputed(() => formatCountdown(this.remaining()) || "0:00");
  progressValue = ko.pureComputed(() => progressValue(this.result(), this.now()));
  reasons = ko.pureComputed(() => explainReasons(this.result()?.riskReason));
  statusText = ko.pureComputed(() => statusLabel(this.result()?.state || ""));
  pauseLabel = ko.pureComputed(() => tierLabel(this.result()?.riskTier || ""));
  riskBadge = ko.pureComputed(() => tierRisk(this.result()?.riskTier || ""));
  amountSize = ko.pureComputed(() => Math.min(12, Math.max(1, this.amount().length)));
  isProtected = ko.pureComputed(() => this.result()?.state === "PROTECTED");
  isSettling = ko.pureComputed(() => this.isProtected() && Number.isFinite(this.remaining()) && this.remaining() <= 0);
  isHold = ko.pureComputed(() => this.result()?.state === "HARD_HOLD");
  needsCall = ko.pureComputed(() => needsVerification(this.result()));
  isSettled = ko.pureComputed(() => this.result()?.state === "SETTLED");
  isCancelled = ko.pureComputed(() => this.result()?.state === "CANCELLED");
  // An instant settle has nothing to explain; pauses and holds still show their reasons.
  showReasons = ko.pureComputed(() => this.reasons().length > 0
    && !(this.isSettled() && !(this.result()?.protectionSeconds || 0)));
  riskTone = ko.pureComputed(() => this.result() ? riskClass(this.result() as PaymentTransaction) : "risk-neutral");
  fluxPhase = ko.pureComputed(() => this.isProtected()
    ? phaseFor(this.progressValue(), PROTECTION_PHASES)
    : phaseFor(this.processing(), CHECK_PHASES));
  fluxChars = ko.pureComputed(() => fluxLetters(this.fluxPhase()));
  arcOffset = ko.pureComputed(() => fluxArcOffset(this.progressValue()));
  fluxPercent = ko.pureComputed(() => `${this.isProtected() ? this.progressValue() : this.processing()}%`);
  showProcessFlux = ko.pureComputed(() => this.busy() && this.attemptLocked() && !this.result());
  private alive = true;
  private initialStep: string | undefined;
  canContinue = ko.pureComputed(() => !!this.selected() && this.account()?.status === "ACTIVE" && !this.loading() && !this.busy() && !this.error());
  cannotContinue = ko.pureComputed(() => !this.canContinue());
  sheetOpen = ko.pureComputed(() => this.step() !== "beneficiary");
  /** After a payment is placed, tapping the dimmed page should close the receipt. */
  canLeave = ko.pureComputed(() => this.step() === "result" && !!this.result() && !this.busy());
  sheetKind = ko.pureComputed(() => progressKind(this.result(), this.showProcessFlux()));
  longTicks = ko.pureComputed(() => {
    const filled = this.progressValue();
    return Array.from({ length: 12 }, (_, index) => ((index + 1) * 100) / 12 <= filled + 0.5);
  });
  quickAmounts = ["500", "1000", "2000", "5000"];
  mask = (value: string): string => value.length > 4 ? "•••• " + value.slice(-4) : "••••";
  money = (value: number): string => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
  amountPreview = ko.pureComputed(() => {
    const value = this.amount().trim();
    if (!/^\d{1,16}(\.\d{1,2})?$/.test(value)) return "";
    const parts = value.split(".");
    const integer = parts[0].replace(/^0+(?=\d)/, "");
    const tail = integer.slice(-3), head = integer.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
    return "₹" + (head ? head + "," : "") + tail + "." + (parts[1] || "").padEnd(2, "0");
  });
  amountLong = ko.pureComputed(() => this.amount().trim().length > 8);
  amountIssue = ko.pureComputed(() => this.amount().trim() ? this.amountProblem() : "");
  amountMessage = ko.pureComputed(() => this.amountError() || this.amountIssue());
  amountReady = ko.pureComputed(() => !!this.amount().trim() && !this.amountProblem());
  isNew = (date: string): boolean => { const age = Date.now() - new Date(date).getTime(); return age >= 0 && age < 86400000; };
  added = (date: string): string => Number.isNaN(new Date(date).getTime()) ? "Added date unavailable" : "Added " + new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(date));
  initials = (name: string): string => (name || "?").slice(0, 1).toUpperCase();
  constructor(private context: { router: FlowRouter; params?: { step?: string } }) { this.initialStep = context.params?.step; }
  parametersChanged(params: { step?: string }): void {
    if (this.result()) {
      this.step("result");
      if (params.step !== "result") void this.context.router.go({ path: "send-money", params: { step: "result" } });
      return;
    }
    if (this.attemptLocked()) {
      this.step("review");
      if (params.step !== "review") void this.context.router.go({ path: "send-money", params: { step: "review" } });
      return;
    }
    let target = "beneficiary";
    if (this.selected() && this.account()) {
      if (params.step === "details") target = "details";
      if (params.step === "review") target = this.validateDetails() ? "review" : "details";
    }
    this.step(target);
    if (params.step !== this.step()) void this.context.router.go({ path: "send-money", params: { step: this.step() } });
    this.notice("");
  }
  load = async (): Promise<void> => {
    if (this.loading() || this.attemptLocked() || this.result()) return;
    this.loading(true); this.error(""); this.selected(null); this.beneficiaries([]); this.account(null);
    try {
      const [beneficiaries, account] = await Promise.all([beneficiaryService.list(), accountService.list()]);
      if (!this.alive) return;
      this.allBeneficiaries=beneficiaries;this.accounts(account);
      const chosen=account.find(a=>a.accountId===this.selectedAccountId()) || account.find(a=>a.status==="ACTIVE") || account[0];
      this.selectedAccountId(chosen?.accountId || null);this.selectAccount();
      if(!chosen)this.error("No account is linked to your profile yet.");
    } catch (error) {
      if (!this.alive) return;
      if (error instanceof ApiError && error.status === 401) {
        window.location.replace("/login?reason=session-expired");
      }
      this.error(error instanceof ApiError && [400, 401, 403, 404].includes(error.status) ? error.message : "We couldn’t load your payment details. Please try again.");
    } finally { if (this.alive) this.loading(false); }
  };
  selectAccount = (): void => {
    if(this.attemptLocked() || this.result())return;
    const account=this.accounts().find(a=>a.accountId===this.selectedAccountId());
    this.account(account||null);this.selected(null);this.funds(null);void this.refreshFunds();
    this.beneficiaries(this.allBeneficiaries.filter(b=>b.accountId===account?.accountId && b.status==="ACTIVE"));
  };
  refreshFunds = async (): Promise<void> => {
    const id=this.account()?.accountId;const generation=++this.fundsGeneration;this.fundsError("");
    if(!id){this.funds(null);return;}
    try{const funds=await accountService.funds(id);if(this.alive && generation===this.fundsGeneration && this.account()?.accountId===id)this.funds(funds);}
    catch(error){if(this.alive && generation===this.fundsGeneration){this.funds(null);this.fundsError("Available funds could not be refreshed. The server will check your balance when you confirm.");}}
  };
  choose = (beneficiary: Beneficiary): void => { if (!this.loading() && !this.busy()) this.selected(beneficiary); };
  pick = (beneficiary: Beneficiary): void => {
    this.choose(beneficiary);
    void this.next();
  };
  useAmount = (value: string): void => {
    if (this.busy()) return;
    this.amount(value);
    this.amountError("");
  };
  next = async (): Promise<void> => {
    if (!this.canContinue()) return;
    this.busy(true);
    try { await this.context.router.go({ path: "send-money", params: { step: "details" } }); }
    finally { if (this.alive) this.busy(false); }
  };
  back = async (): Promise<void> => {
    if (this.canLeave()) { await this.leave(); return; }
    if (this.busy() || this.attemptLocked()) return;
    await this.context.router.go(this.step() === "review"
      ? { path: "send-money", params: { step: "details" } }
      : this.step() === "details" ? { path: "send-money", params: { step: "beneficiary" } } : { path: "dashboard" });
  };
  /** Close the receipt and return to the recipient list. */
  leave = async (): Promise<void> => {
    if (!this.canLeave()) return;
    this.stopPolling();
    this.result(null);
    this.attempt = undefined;
    this.attemptLocked(false);
    this.cancelKey = undefined;
    this.paymentError("");
    this.amount("");
    this.purpose("");
    
    this.selected(null);
    await this.load();
    await this.context.router.go({ path: "send-money", params: { step: "beneficiary" } });
  };
  private amountProblem(): string {
    const value = this.amount().trim();
    if (!value) return "Enter an amount.";
    if (!/^\d{1,16}(\.\d{1,2})?$/.test(value) || !/[1-9]/.test(value)) {
      return "Enter a positive amount in rupees, digits only, up to 2 decimal places.";
    }
    const amount = Number(value);
    const balance = this.account()?.balance;
    if (typeof balance === "number" && amount > balance) {
      return `That is more than the ${this.money(balance)} account balance.`;
    }
    return "";
  }
  private validateDetails(): boolean {
    this.amountError(this.amountProblem()); this.purposeError("");
    if (this.purpose().length > 255) this.purposeError("Keep your note to 255 characters or fewer.");
    return !this.amountError() && !this.purposeError();
  }
  continueDetails = async (): Promise<void> => {
    if (this.busy() || this.loading()) return;
    this.notice("");
    const valid = this.validateDetails();
    if (!this.selected()) { this.parametersChanged({ step: "beneficiary" }); return; }
    if (!valid) {
      document.getElementById(this.amountError() ? "payment-amount" : "payment-purpose")?.focus(); return;
    }
    if (!this.account() || this.error()) return;
    this.busy(true);
    try {
      await this.refreshFunds();
      await this.context.router.go({ path: "send-money", params: { step: "review" } });
    } finally { if (this.alive) this.busy(false); }
  };
  private showPaymentError(error: unknown): void {
    if (error instanceof ApiError && error.status === 401) {
      this.result(null); this.stopPolling();
      window.location.replace("/login?reason=session-expired");
    }
    this.paymentError(error instanceof ApiError && [400, 403, 404, 409].includes(error.status)
      ? error.message : "We couldn’t confirm the payment status. Check Transactions before starting another payment. You can retry this unchanged request here safely.");
  }
  private acceptResult(value: PaymentTransaction): void {
    if (!value || !Number.isSafeInteger(value.transactionId) || value.transactionId <= 0 || typeof value.state !== "string" || !Number.isFinite(value.amount)) {
      throw new ApiError(200, "Invalid payment response", "response");
    }
    this.result(value); this.now(Date.now());
    if (!isInFlight(value.state)) this.stopPolling();
    chimeForPayment(value);
  }
  confirm = async (): Promise<void> => {
    if (this.busy() || this.loading() || this.result() || this.step() !== "review") return;
    this.paymentError("");
    if (!this.attempt) {
      if (!this.validateDetails() || !this.selected() || !this.account() || this.account()!.status !== "ACTIVE" || this.selected()!.accountId !== this.account()!.accountId) {
        this.paymentError(this.amountError() || this.purposeError() || "Choose a beneficiary and source account before paying."); return;
      }
      if (new TextEncoder().encode(this.purpose()).length > 255) {
        this.paymentError("Keep your note to 255 UTF-8 bytes or fewer."); return;
      }
      this.attempt = { key: newIdempotencyKey(), body: {
        fromAccountId: this.account()!.accountId, beneficiaryId: this.selected()!.beneficiaryId,
        amount: this.amount().trim(), purpose: this.purpose()
      } };
    }
    this.busy(true); this.attemptLocked(true); this.startProcessFlux();
    armAudio();
    try {
      const response = await transactionService.create(this.attempt.body, this.attempt.key);
      if (!this.alive) return;
      this.acceptResult(response);
      await this.context.router.go({ path: "send-money", params: { step: "result" } });
      this.startPolling();
    } catch (error) {
      if (this.alive) {
        if (error instanceof ApiError && [400, 403, 404].includes(error.status)) {
          this.attempt = undefined; this.attemptLocked(false);
        }
        this.showPaymentError(error);
      }
    }
    finally { this.stopProcessFlux(); if (this.alive) this.busy(false); }
  };
  private startProcessFlux(): void {
    this.stopProcessFlux(); this.processing(8);
    this.processTimer = setInterval(() => {
      const next = this.processing() + 6;
      this.processing(next >= 88 ? 88 : next);
    }, 140);
  }
  private stopProcessFlux(): void {
    if (this.processTimer) clearInterval(this.processTimer);
    this.processTimer = undefined;
    this.processing(this.result() ? 100 : 0);
  }
  refreshResult = async (): Promise<void> => {
    const current = this.result();
    if (!current || this.refreshing() || this.busy()) return;
    this.refreshing(true);
    try {
      const response = await transactionService.get(current.transactionId);
      if (this.alive) { this.acceptResult(response); this.paymentError(""); if (!this.timer) this.startPolling(); }
    } catch (error) { if (this.alive) { this.stopPolling(); this.showPaymentError(error); } }
    finally { if (this.alive) this.refreshing(false); }
  };
  requestCancel = (): void => { if (this.canCancel()) { armAudio(); this.cancelOpen(true); } };
  closeCancel = (): void => { this.cancelOpen(false); };
  confirmCancel = async (): Promise<void> => {
    armAudio();
    this.cancelOpen(false);
    await this.cancelPayment();
  };
  cancelPayment = async (): Promise<void> => {
    if (!this.canCancel()) return;
    armAudio();
    this.busy(true); this.paymentError("");
    try {
      const current = await transactionService.get(this.result()!.transactionId);
      if (!this.alive) return;
      this.acceptResult(current);
      if (!canCancelPayment(current, Date.now())) return;
      this.cancelKey ||= newIdempotencyKey();
      const response = await transactionService.cancel(current.transactionId, this.cancelKey);
      if (this.alive) this.acceptResult(response);
    } catch (error) { if (this.alive) this.showPaymentError(error); }
    finally { if (this.alive) this.busy(false); }
  };
  private startPolling(): void {
    this.stopPolling(); this.lastPoll = Date.now();
    if (!isInFlight(this.result()?.state)) return;
    this.timer = setInterval(() => {
      this.now(Date.now());
      if (Date.now() - this.lastPoll >= 3000) { this.lastPoll = Date.now(); void this.refreshResult(); }
    }, 1000);
  }
  private stopPolling(): void { if (this.timer) clearInterval(this.timer); this.timer = undefined; }
  connected(): void {
    document.title = "Send Money | SafePay";
    this.parametersChanged({ step: this.initialStep });
    void this.load();
  }
  disconnected(): void { this.alive = false;    this.stopPolling(); this.stopProcessFlux(); this.result(null); this.attempt = undefined; this.selected(null); this.account(null); this.beneficiaries([]); this.amount(""); this.purpose("");  }
}
export = SendMoneyViewModel;
