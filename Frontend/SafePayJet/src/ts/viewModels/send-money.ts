import * as ko from "knockout";
import { beneficiaryService, Beneficiary } from "../services/beneficiaryService";
import { accountService, AccountFunds } from "../services/accountService";
import { Account, PaymentTransaction, TransactionRequest } from "../services/types";
import { transactionService, newIdempotencyKey } from "../services/transactionService";
import { ApiError } from "../services/apiError";
import { PAYMENT_CATEGORIES, PaymentCategory, categoryApplies, categoryLabel, categoryProblem } from "../constants/paymentCategories";
import {
  canCancelPayment, explainReasons, formatCountdown, isInFlight, needsVerification,
  paymentGauge, progressValue, remainingFor, resultTitle, riskClass, statusLabel, tierLabel, tierRisk
} from "../utils/protection";
import { armAudio, chimeForPayment } from "../utils/chime";
import "ojs/ojdialog";
import "ojs/ojbutton";
import "ojs/ojtrain";
import "ojs/ojinputtext";
import "ojs/ojavatar";
import "ojs/ojmessages";

interface FlowRouter { go(route: { path: string; params?: { step: string } }): Promise<unknown>; }
// UI demonstration only. Never sent to the API or persisted; not payment security.
const SIMULATED_S_PIN = "123456";
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
  readonly categoryOptions = PAYMENT_CATEGORIES;
  category = ko.observable<PaymentCategory | undefined>();
  categoryError = ko.observable("");
  highValue = ko.pureComputed(() => categoryApplies(this.amount()));
  othersCategory = ko.pureComputed(() => this.highValue() && this.category() === "OTHERS");
  categoryLabel = categoryLabel;
  reviewCategory = ko.pureComputed(() => this.pinOpen() || this.attemptLocked() ? this.attempt?.body.category : this.category());
  private amountSubscription: ko.Subscription;
  amountError = ko.observable("");
  purposeError = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  loading = ko.observable(false);
  busy = ko.observable(false);
  paymentError = ko.observable("");
  pinOpen = ko.observable(false);
  sPin = ko.observable("");
  pinError = ko.observable("");
  pinAmount = ko.pureComputed(() => this.pinOpen() ? this.money(Number(this.attempt?.body.amount || 0)) : "");
  result = ko.observable<PaymentTransaction | null>(null);
  attemptLocked = ko.observable(false);
  refreshing = ko.observable(false);
  cancelOpen = ko.observable(false);
  now = ko.observable(Date.now());
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
  private balanceTimer?: ReturnType<typeof setInterval>;
  private balancesRefreshing = false;
  private onFocus = (): void => { void this.refreshBalances(); };
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
  gauge = ko.pureComputed(() => paymentGauge(this.result(), this.now()));
  showProcessFlux = ko.pureComputed(() => this.busy() && this.attemptLocked() && !this.result());
  private alive = true;
  private initialStep: string | undefined;
  canContinue = ko.pureComputed(() => !!this.selected() && this.account()?.status === "ACTIVE" && !this.loading() && !this.busy() && !this.error());
  cannotContinue = ko.pureComputed(() => !this.canContinue());
  sheetOpen = ko.pureComputed(() => this.step() !== "beneficiary");
  /** After a payment is placed, tapping the dimmed page should close the receipt. */
  canLeave = ko.pureComputed(() => this.step() === "result" && !!this.result() && !this.busy());
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
  constructor(private context: { router: FlowRouter; params?: { step?: string } }) {
    this.initialStep = context.params?.step;
    this.amountSubscription = this.amount.subscribe(value => {
      this.amountError("");
      if (!this.attemptLocked() && !this.pinOpen() && !categoryApplies(value)) {
        this.category(undefined); this.categoryError("");
      }
    });
  }
  clearCategoryError = (): void => { this.categoryError(""); this.purposeError(""); };
  parametersChanged(params: { step?: string }): void {
    this.closePin();
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
    if (this.loading() || this.attemptLocked() || this.result() || this.pinOpen()) return;
    this.loading(true); this.error(""); this.selected(null); this.beneficiaries([]); this.account(null);
    try {
      const [beneficiaries, account] = await Promise.all([beneficiaryService.list(), accountService.list()]);
      if (!this.alive) return;
      this.allBeneficiaries=beneficiaries;this.accounts(account);
      const chosen=account.find(a=>a.accountId===this.selectedAccountId()) || account.find(a=>a.status==="ACTIVE") || account[0];
      this.selectedAccountId(chosen?.accountId || null);await this.selectAccount();
      if(!chosen)this.error("No account is linked to your profile yet.");
    } catch (error) {
      if (!this.alive) return;
      if (error instanceof ApiError && error.status === 401) {
        window.location.replace("/login?reason=session-expired");
      }
      this.error(error instanceof ApiError && [400, 401, 403, 404].includes(error.status) ? error.message : "We couldn’t load your payment details. Please try again.");
    } finally { if (this.alive) this.loading(false); }
  };
  selectAccount = async (): Promise<void> => {
    if(this.attemptLocked() || this.result() || this.pinOpen())return;
    const account=this.accounts().find(a=>a.accountId===this.selectedAccountId());
    this.account(account||null);this.selected(null);this.funds(null);
    this.beneficiaries(this.allBeneficiaries.filter(b=>b.accountId===account?.accountId && b.status==="ACTIVE"));
    await this.refreshFunds();
  };
  refreshFunds = async (): Promise<boolean> => {
    const id=this.account()?.accountId;const generation=++this.fundsGeneration;this.fundsError("");
    if(!id){this.funds(null);return false;}
    try{const funds=await accountService.funds(id);if(this.alive && generation===this.fundsGeneration && this.account()?.accountId===id){
      if (funds?.accountId !== id || ![funds.balance, funds.reservedBalance, funds.availableToTransfer].every(Number.isFinite) || funds.availableToTransfer < 0) throw new Error("Invalid funds response");
      this.funds(funds);
      if (this.amountError()) this.amountError(this.amountProblem());
      if(this.account()!.balance!==funds.balance){
        const updated={...this.account()!,balance:funds.balance};this.account(updated);
        this.accounts(this.accounts().map(a=>a.accountId===id?updated:a));
      }
      return true;
    }}
    catch(error){if(this.alive && generation===this.fundsGeneration){
      this.funds(null);this.fundsError("We couldn’t check your current balance. Please retry before continuing.");
      if(error instanceof ApiError && error.status===401) { this.closePin(); this.stopPolling(); window.location.replace("/login?reason=session-expired"); }
    }}
    return false;
  };
  refreshBalances = async (): Promise<void> => {
    if(!this.alive || this.balancesRefreshing || this.loading() || this.busy() || document.visibilityState === "hidden")return;
    this.balancesRefreshing=true;
    try {
      const accounts=await accountService.list();if(!this.alive || this.busy())return;
      this.accounts(accounts);
      this.account(accounts.find(a=>a.accountId===this.selectedAccountId()) || null);
      await this.refreshFunds();
    } catch(error) {
      if(this.alive){
        if(error instanceof ApiError && error.status===401){this.closePin();this.stopPolling();window.location.replace("/login?reason=session-expired");}
        this.fundsError("Balances could not be refreshed. Please refresh before sending another payment.");
      }
    } finally {this.balancesRefreshing=false;}
  };
  choose = (beneficiary: Beneficiary): void => { if (!this.loading() && !this.busy() && !this.pinOpen() && !this.attemptLocked()) this.selected(beneficiary); };
  pick = (beneficiary: Beneficiary): void => {
    this.choose(beneficiary);
    void this.next();
  };
  useAmount = (value: string): void => {
    if (this.busy() || this.pinOpen() || this.attemptLocked()) return;
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
    if (this.pinOpen() && !this.busy()) { this.closePin(); return; }
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
    this.closePin();
    this.result(null);
    this.attempt = undefined;
    this.attemptLocked(false);
    this.cancelKey = undefined;
    this.paymentError("");
    this.amount("");
    this.purpose("");
    this.category(undefined); this.categoryError("");
    
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
    const funds = this.funds();
    if (!funds || funds.accountId !== this.account()?.accountId || this.fundsError() || !Number.isFinite(funds.availableToTransfer)) {
      return "Please wait for your current balance, or retry the balance check.";
    }
    if (amount > funds.availableToTransfer) {
      return `Insufficient balance. Your current balance is ${this.money(funds.availableToTransfer)}. Enter this amount or less.`;
    }
    return "";
  }
  private validateDetails(): boolean {
    this.amountError(this.amountProblem()); this.purposeError("");
    this.categoryError(categoryProblem(this.amount(), this.highValue() ? this.category() : undefined));
    if (this.purpose().length > 255) this.purposeError("Keep your note to 255 characters or fewer.");
    if (this.othersCategory() && (!this.purpose().trim() || this.purpose().trim().length > 140)) {
      this.purposeError("Others requires a purpose of 1–140 characters.");
    }
    return !this.amountError() && !this.purposeError() && !this.categoryError();
  }
  continueDetails = async (): Promise<void> => {
    if (this.busy() || this.loading()) return;
    this.notice("");
    const valid = this.validateDetails();
    if (!this.selected()) { this.parametersChanged({ step: "beneficiary" }); return; }
    if (!valid) {
      document.getElementById(this.amountError() ? "payment-amount" : this.categoryError() ? "payment-category" : this.othersCategory() ? "payment-category-purpose" : "payment-purpose")?.focus(); return;
    }
    if (!this.account() || this.error()) return;
    this.busy(true);
    try {
      const refreshed = await this.refreshFunds();
      if (!this.alive) return;
      if (!refreshed || !this.validateDetails()) {
        this.amountError(this.amountProblem());
        document.getElementById("payment-amount")?.focus(); return;
      }
      await this.context.router.go({ path: "send-money", params: { step: "review" } });
    } finally { if (this.alive) this.busy(false); }
  };
  private showPaymentError(error: unknown): void {
    if (error instanceof ApiError && error.status === 401) {
      this.closePin();
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
    const changed=this.result()?.state!==value.state || this.result()?.transactionId!==value.transactionId;
    this.result(value); this.now(Date.now());
    if(changed)void this.refreshBalances();
    if (!isInFlight(value.state)) this.stopPolling();
    chimeForPayment(value);
  }
  confirm = async (): Promise<void> => {
    if (!this.alive || this.busy() || this.loading() || this.pinOpen() || this.result() || this.step() !== "review") return;
    this.paymentError("");
    if (!this.attempt) {
      this.busy(true);
      let refreshed: boolean;
      try { refreshed = await this.refreshFunds(); }
      finally { if (this.alive) this.busy(false); }
      if (!this.alive || this.step() !== "review") return;
      if (!refreshed) { this.paymentError(this.fundsError() || "Please retry the balance check before paying."); return; }
      if (!this.validateDetails() || !this.selected() || !this.account() || this.account()!.status !== "ACTIVE" || this.selected()!.accountId !== this.account()!.accountId) {
        this.paymentError(this.amountError() || this.categoryError() || this.purposeError() || "Choose a beneficiary and source account before paying."); return;
      }
      if (new TextEncoder().encode(this.purpose()).length > 255) {
        this.paymentError("Keep your note to 255 UTF-8 bytes or fewer."); return;
      }
      this.attempt = { key: newIdempotencyKey(), body: {
        fromAccountId: this.account()!.accountId, beneficiaryId: this.selected()!.beneficiaryId,
        amount: this.amount().trim(), purpose: this.othersCategory() ? this.purpose().trim() : this.purpose(),
        ...(this.highValue() ? { category: this.category() } : {})
      } };
    }
    this.sPin(""); this.pinError(""); this.pinOpen(true);
  };
  closePin = (): void => {
    this.pinOpen(false); this.sPin(""); this.pinError("");
    // An uncertain submitted payment must retain its original payload and retry key.
    if (!this.attemptLocked()) this.attempt = undefined;
  };
  onPinInput = (_: unknown, event: Event): void => {
    if (!this.pinOpen() || this.busy()) return;
    this.sPin((event.target as HTMLInputElement).value); this.pinError("");
    if (this.sPin().length >= 6) void this.submitPin();
  };
  submitPin = async (): Promise<void> => {
    if (!this.alive || !this.pinOpen() || this.busy() || this.loading() || !this.attempt || this.step() !== "review" || this.result()) return;
    if (!/^[0-9]{6}$/.test(this.sPin())) { this.pinError("Enter a six-digit S PIN."); return; }
    if (this.sPin() !== SIMULATED_S_PIN) { this.pinError("Incorrect S PIN. Please try again."); return; }
    this.sPin(""); this.pinError(""); this.pinOpen(false);
    await this.submitPayment();
  };
  private async submitPayment(): Promise<void> {
    if (!this.attempt || this.busy() || !this.alive) return;
    this.busy(true); this.attemptLocked(true);
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
    finally { if (this.alive) { this.busy(false); if (this.result()) void this.refreshBalances(); } }
  }
  refreshResult = async (): Promise<void> => {
    const current = this.result();
    if (!current || this.refreshing() || this.busy()) return;
    this.refreshing(true);
    try {
      const response = await transactionService.get(current.transactionId);
      if (this.alive) { this.acceptResult(response); this.paymentError(""); if (!this.timer) this.startPolling(); }
    } catch (error) {
      if (this.alive) {
        // A temporary network failure must not leave the receipt stuck without a refresh button.
        if (error instanceof ApiError && error.status === 401) this.showPaymentError(error);
        else this.paymentError("We couldn’t update this payment just now. We’ll retry automatically; its last confirmed status is shown.");
      }
    }
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
    finally { if (this.alive) { this.busy(false); if (this.result()) void this.refreshBalances(); } }
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
    this.alive=true;
    window.addEventListener?.("focus",this.onFocus);
    this.balanceTimer=setInterval(()=>void this.refreshBalances(),3000);
    document.title = "Send Money | SafePay";
    this.parametersChanged({ step: this.initialStep });
    void this.load();
  }
  disconnected(): void { this.alive = false; this.closePin(); this.amountSubscription.dispose(); this.category(undefined); this.categoryError(""); this.fundsGeneration++; if(this.balanceTimer)clearInterval(this.balanceTimer);window.removeEventListener?.("focus",this.onFocus); this.stopPolling(); this.result(null); this.attempt = undefined; this.selected(null); this.account(null); this.beneficiaries([]); this.amount(""); this.purpose("");  }
}
export = SendMoneyViewModel;
