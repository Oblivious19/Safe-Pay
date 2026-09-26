import type { PaymentTransaction } from "../services/types";

export const IN_FLIGHT_STATES = ["CREATED", "AUTHORIZED", "RISK_ASSESSED", "PROTECTED", "HARD_HOLD", "RELEASED"];

const STATUS_LABELS: Record<string, string> = {
  CREATED: "Payment started",
  AUTHORIZED: "Authorised",
  RISK_ASSESSED: "Checked",
  PROTECTED: "Protected — you can still cancel",
  HARD_HOLD: "Held for extra review",
  SETTLED: "Settled",
  CANCELLED: "Cancelled",
  REJECTED: "Not approved",
  RELEASED: "Settling"
};

const TIER_LABELS: Record<string, string> = {
  LOW: "Settled immediately",
  MEDIUM: "Short pause",
  HIGH: "Longer pause",
  VERY_HIGH: "Extra review",
  HARD_HOLD: "Extra review"
};

export function parseExpiry(value?: string | null): number {
  return /(?:Z|[+-]\d{2}:\d{2})$/.test(value || "") ? Date.parse(value as string) : NaN;
}

export function remainingSeconds(expiresAt: string | undefined, now: number): number {
  const expiry = parseExpiry(expiresAt);
  if (!Number.isFinite(expiry)) return NaN;
  return Math.max(0, Math.ceil((expiry - now) / 1000));
}

export function remainingFor(tx: PaymentTransaction | null | undefined, now: number): number {
  return typeof tx?.protectionDeadline === "number" ? Math.max(0,Math.ceil((tx.protectionDeadline-now)/1000)) : NaN;
}
export function canCancelPayment(tx: PaymentTransaction | null | undefined, now: number): boolean {
  if (!tx || tx.canCancel !== true || tx.direction === "CREDIT") return false;
  if (tx.state === "HARD_HOLD") return true;
  return tx.state === "PROTECTED" && remainingFor(tx,now) > 0;
}

export function statusLabel(state: string): string {
  return STATUS_LABELS[state] || "Processing";
}

export function tierLabel(tier: string): string {
  return TIER_LABELS[tier] || "Checked";
}

const TIER_RISK: Record<string, string> = {
  LOW: "Low risk",
  MEDIUM: "Medium risk",
  HIGH: "High risk",
  VERY_HIGH: "Very high risk",
  HARD_HOLD: "Very high risk"
};

export function tierRisk(tier: string): string {
  return TIER_RISK[tier] || "Checked";
}

export function resultTitle(tx: PaymentTransaction | null | undefined, remaining: number): string {
  const state = tx?.state;
  if (state === "SETTLED") return (tx?.protectionSeconds || 0) > 0 ? "Settled" : "Settled immediately";
  if (state === "PROTECTED") return Number.isFinite(remaining) && remaining <= 0 ? "Settling…" : "This payment is held";
  if (state === "HARD_HOLD") return "Held for extra review";
  if (state === "CANCELLED") return "Payment cancelled";
  if (state === "REJECTED") return "Payment not completed";
  return "Payment processing";
}

export function formatCountdown(seconds: number): string {
  if (!Number.isFinite(seconds)) return "";
  const total = Math.max(0, Math.floor(seconds));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

export function progressValue(tx: PaymentTransaction | null | undefined, now: number): number {
  if (!tx || tx.state !== "PROTECTED") return 0;
  const remaining = remainingFor(tx, now);
  const total = tx.protectionSeconds || 0;
  if (!total || !Number.isFinite(remaining)) return 0;
  return Math.min(100, Math.max(0, Math.round(((total - remaining) / total) * 100)));
}

export function explainReasons(riskReason?: string | null): string[] {
  if (!riskReason) return [];
  return riskReason.split(/;\s*/).map((line) => line.trim()).filter((line) => line && !/^Total score\b/i.test(line));
}

export function riskClass(tx: PaymentTransaction | null | undefined): string {
  if (!tx) return "risk-neutral";
  if (tx.state === "HARD_HOLD" || ["VERY_HIGH", "HARD_HOLD"].includes(tx.riskTier)) return "risk-red";
  if (tx.riskTier === "HIGH") return "risk-orange";
  if (tx.riskTier === "MEDIUM") return "risk-amber";
  return "risk-neutral";
}

export function isInFlight(state?: string | null): boolean {
  return !!state && IN_FLIGHT_STATES.includes(state);
}

/** Customers cannot approve a held payment. */
export function needsVerification(tx?: PaymentTransaction | null): boolean {
  return false; // HARD_HOLD is approved only by an administrator on the existing backend.
}

export const PROTECTION_PHASES = [
  { at: 0, label: "held" },
  { at: 35, label: "cancellable" },
  { at: 75, label: "releasing" },
  { at: 100, label: "settling" }
];

export const CHECK_PHASES = [
  { at: 0, label: "checking" },
  { at: 40, label: "scoring" },
  { at: 75, label: "deciding" },
  { at: 100, label: "ready" }
];

export const FLUX_ARC = 257.6;

export function phaseFor(progress: number, phases: { at: number; label: string }[] = PROTECTION_PHASES): string {
  let label = phases[0]?.label || "";
  for (const phase of phases) if (progress >= phase.at) label = phase.label;
  return label;
}

export function fluxLetters(label: string): { char: string; delay: string }[] {
  return (label || "").split("").map((char, index) => ({
    char: char === " " ? "\u00a0" : char,
    delay: `${index * 45}ms`
  }));
}

export function arcOffset(progress: number): number {
  const value = Math.min(100, Math.max(0, progress || 0));
  return FLUX_ARC * (1 - value / 100);
}

/** Presentation only: one semicircle for every risk tier, driven by server state. */
export function paymentGauge(tx: PaymentTransaction | null | undefined, now: number): {
  mode: "timed" | "waiting" | "settled" | "stopped";
  label: string; caption: string; offset: number;
} {
  if (tx?.state === "SETTLED") return {
    mode: "settled", label: "Settled", offset: 0,
    caption: (tx.protectionSeconds || 0) > 0
      ? "Payment complete. The protection window has ended."
      : "Payment complete. No protection pause was needed."
  };
  if (tx?.state === "CANCELLED" || tx?.state === "REJECTED") return {
    mode: "stopped", label: tx.state === "CANCELLED" ? "Cancelled" : "Not approved", offset: 0,
    caption: tx.state === "CANCELLED"
      ? "Payment cancelled. Your money stayed in your account."
      : "Payment not approved. No money was sent."
  };
  if (tx?.state === "HARD_HOLD") return {
    mode: "waiting", label: "Awaiting review", offset: FLUX_ARC,
    caption: "Awaiting admin approval. You can cancel before approval. No timed release."
  };
  if (tx?.state === "PROTECTED") {
    const remaining = remainingFor(tx, now);
    if (Number.isFinite(remaining) && remaining > 0) return {
      mode: "timed", label: formatCountdown(remaining), offset: arcOffset(progressValue(tx, now)),
      caption: "Your payment is paused. You can cancel until the timer ends."
    };
    return {
      mode: "waiting", label: "Checking status", offset: FLUX_ARC,
      caption: Number.isFinite(remaining)
        ? "Pause ended. Confirming your payment status."
        : "Timer unavailable. Checking your payment status."
    };
  }
  return { mode: "waiting", label: "Processing", offset: FLUX_ARC, caption: "Checking this payment before it can settle." };
}

export type ProgressKind = "idle" | "check" | "short" | "long" | "hold" | "done" | "cancelled";

export function progressKind(tx?: PaymentTransaction | null, checking = false): ProgressKind {
  if (checking && !tx) return "check";
  if (!tx) return "idle";
  if (tx.state === "CANCELLED" || tx.state === "REJECTED") return "cancelled";
  if (tx.state === "SETTLED") return "done";
  if (tx.state === "HARD_HOLD") return "hold";
  if (tx.state === "PROTECTED") return tx.riskTier === "HIGH" || (tx.protectionSeconds || 0) >= 30 ? "long" : "short";
  return "check";
}
