import * as ko from "knockout";
import { ApiError } from "../services/apiError";
import { demoHoldCode } from "../services/demoSession";
import { newIdempotencyKey, transactionService } from "../services/transactionService";
import { PaymentTransaction } from "../services/types";
import { armAudio, chimeIfSettled, playChime, startRing, stopRing } from "../utils/chime";

export type CallPhase = "ringing" | "connected" | "verifying" | "verified" | "failed" | "closed" | "ended";

/**
 * The simulated verification call that stands between a HARD_HOLD and settlement.
 *
 * SafePay "rings" the customer, reads out why the payment is held, and takes the six-digit
 * code the bank sent to their email. Nothing here decides the outcome: the code is checked
 * by the service layer, and only its answer moves the payment.
 */
export class VerifyCallModel {
  open = ko.observable(false);
  phase = ko.observable<CallPhase>("ringing");
  code = ko.observable("");
  error = ko.observable("");
  notice = ko.observable("");
  busy = ko.observable(false);
  sentTo = ko.observable("");
  /** Simulation only: stands in for opening the inbox the code was emailed to. */
  inboxCode = ko.observable("");
  attemptsLeft = ko.observable(3);
  seconds = ko.observable(0);
  codeSeconds = ko.observable(NaN);
  transactionId = ko.observable(0);
  amount = ko.observable(0);
  payee = ko.observable("");
  callerNumber = "1800 11 7233";
  callerName = "SafePay Verification Desk";
  ringing = ko.pureComputed(() => this.phase() === "ringing");
  live = ko.pureComputed(() => ["connected", "verifying", "failed"].includes(this.phase()));
  done = ko.pureComputed(() => this.phase() === "verified");
  closed = ko.pureComputed(() => this.phase() === "closed");
  entryDisabled = ko.pureComputed(() => this.busy() || this.phase() !== "connected");
  codeDots = ko.pureComputed(() => {
    const filled = this.code().length;
    return Array.from({ length: 6 }, (_, index) => index < filled);
  });
  clock = ko.pureComputed(() => {
    const total = Math.max(0, this.seconds());
    return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
  });
  codeClock = ko.pureComputed(() => {
    const left = this.codeSeconds();
    if (!Number.isFinite(left)) return "";
    if (left <= 0) return "Code expired";
    return `Code valid for ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
  });
  script = ko.pureComputed(() => {
    switch (this.phase()) {
      case "ringing": return "Incoming verification call for a payment we have put on hold.";
      case "connected": return `Please key in the 6-digit code we sent to ${this.sentTo() || "your email"}.`;
      case "verifying": return "Checking that code now. Please stay on the line.";
      case "verified": return "Thank you. That payment has been released.";
      case "failed": return "That code was not accepted. Ask for a new one when you are ready.";
      case "closed": return this.error() || "This payment has already been decided. There is nothing left to verify.";
      default: return "Call ended.";
    }
  });
  money = (value: number): string => new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(value);
  private timer?: ReturnType<typeof setInterval>;
  private expiresAt = 0;
  private key?: string;
  private entry: { dispose(): void };
  constructor(private onSettled: (tx: PaymentTransaction) => void) {
    // Six digits is the whole code, so answer the caller as soon as it is complete.
    this.entry = this.code.subscribe((value) => {
      const digits = (value || "").replace(/\D/g, "").slice(0, 6);
      if (digits !== value) { this.code(digits); return; }
      if (digits.length === 6 && this.phase() === "connected") void this.submit();
    });
  }
  ring = (tx: PaymentTransaction): void => {
    if (tx.state !== "HARD_HOLD" || tx.verification === "VERIFIED") return;
    this.transactionId(tx.transactionId); this.amount(tx.amount); this.payee(tx.beneficiaryName);
    this.sentTo(tx.verificationSentTo || ""); this.inboxCode("");
    this.code(""); this.error(""); this.notice(""); this.attemptsLeft(3);
    this.codeSeconds(NaN); this.seconds(0); this.expiresAt = 0; this.key = undefined;
    this.phase("ringing"); this.open(true);
    armAudio();
    startRing();
    this.startClock();
  };
  accept = async (): Promise<void> => {
    if (this.phase() !== "ringing" || this.busy()) return;
    armAudio();
    stopRing();
    playChime("answer");
    await this.request("connected");
  };
  resend = async (): Promise<void> => {
    if (this.busy() || this.phase() === "verified") return;
    await this.request(this.phase() === "failed" ? "connected" : this.phase(), true);
  };
  private async request(next: CallPhase, fresh = false): Promise<void> {
    this.busy(true);
    if (!fresh) this.error("");
    try {
      const challenge = await transactionService.requestVerification(this.transactionId());
      this.sentTo(challenge.sentTo);
      this.attemptsLeft(challenge.attemptsLeft);
      this.inboxCode(challenge.simulatedCode || demoHoldCode(this.transactionId()));
      this.expiresAt = Date.parse(challenge.expiresAt) || 0;
      this.code("");
      this.notice(fresh
        ? `A new code has been sent to ${challenge.sentTo}. Use this one.`
        : `Code sent to ${challenge.sentTo}.`);
      this.phase(next);
      this.startClock();
    } catch (error) {
      const status = error instanceof ApiError ? error.status : 0;
      this.error(this.messageFor(error, "We could not send a verification code. Please try again."));
      this.phase(status === 409 ? "closed" : "failed");
      if (status === 409) { this.inboxCode(""); this.stopClock(); }
    } finally { this.busy(false); }
  }
  submit = async (): Promise<void> => {
    if (this.busy() || !/^\d{6}$/.test(this.code())) {
      if (!this.busy()) this.error("Key in the 6-digit code from your email.");
      return;
    }
    const code = this.code();
    this.busy(true); this.error(""); this.notice(""); this.phase("verifying");
    this.key ||= newIdempotencyKey();
    try {
      const settled = await transactionService.verifyHold(this.transactionId(), code, this.key);
      this.code(""); this.phase("verified"); this.codeSeconds(NaN);
      chimeIfSettled(settled);
      this.onSettled(settled);
      this.stopClock();
    } catch (error) {
      this.code("");
      this.key = undefined;
      const status = error instanceof ApiError ? error.status : 0;
      this.error(this.messageFor(error, "We could not check that code. Please try again."));
      if (status === 409) {
        this.phase("closed"); this.attemptsLeft(0); this.inboxCode(""); this.stopClock();
      } else if (status === 400) {
        const left = Math.max(0, this.attemptsLeft() - 1);
        this.attemptsLeft(left);
        if (left > 0) {
          this.phase("connected");
          playChime("off");
          await this.request("connected", true);
          this.error("That code was not right.");
        } else {
          this.phase("failed");
          playChime("off");
          this.notice("Send a new code to try again.");
        }
      } else {
        this.attemptsLeft(0);
        this.phase("failed");
      }
    } finally { this.busy(false); }
  };
  hangUp = (): void => {
    armAudio();
    stopRing();
    if (this.phase() !== "verified") playChime("end");
    this.stopClock();
    this.open(false); this.phase("ended"); this.code(""); this.error(""); this.notice("");
  };
  private messageFor(error: unknown, fallback: string): string {
    if (error instanceof ApiError && error.status === 401) {
      window.location.replace("/login?reason=session-expired");
    }
    return error instanceof ApiError && [400, 403, 404, 409, 410].includes(error.status)
      ? error.message : fallback;
  }
  private startClock(): void {
    this.stopClock();
    this.timer = setInterval(() => {
      this.seconds(this.seconds() + 1);
      if (!this.expiresAt) return;
      const left = Math.max(0, Math.ceil((this.expiresAt - Date.now()) / 1000));
      this.codeSeconds(left);
      if (left === 0 && this.phase() === "connected") {
        this.phase("failed");
        this.error("That code has expired. Ask for a new one.");
      }
    }, 1000);
  }
  private stopClock(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }
  dispose(): void {
    stopRing();
    this.stopClock();
    this.entry.dispose();
    this.open(false);
  }
}
