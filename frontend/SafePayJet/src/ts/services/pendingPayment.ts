/** Phase 0B: recovery data only. This module never sends a request or changes a balance. */
export const PENDING_STORAGE_KEY = "safepay.pending-payment.v1";
// Shorter than A's current 12-hour idempotency retention; never extend on reload.
export const PENDING_TTL_MS = 11 * 60 * 60 * 1000;

export type PaymentCategory = "MEDICAL" | "LOAN" | "FRIENDS_FAMILY" | "INVESTMENTS" | "OTHERS";
export type RecoverableOperation = "CREATE" | "AUTHORIZE" | "CANCEL" | "OTP_ISSUE" | "OTP_RESEND";
export interface CreatePaymentDraft {
  readonly sourceAccountId: string;
  readonly beneficiaryId: string;
  readonly amount: string;
  readonly purpose?: string;
  readonly customerReference?: string;
  readonly category?: PaymentCategory;
}
export type PendingIntent =
  | { readonly operation: "CREATE"; readonly payload: CreatePaymentDraft }
  | { readonly operation: "AUTHORIZE"; readonly transactionId: string; readonly payload: { readonly confirmed: true } }
  | { readonly operation: "CANCEL" | "OTP_ISSUE" | "OTP_RESEND"; readonly transactionId: string };
export interface PendingAttempt {
  readonly version: 1;
  readonly userId: string;
  readonly idempotencyKey: string;
  readonly createdAt: number;
  readonly expiresAt: number;
  readonly intent: PendingIntent;
}
export type RecoveryState =
  | { readonly status: "empty" }
  | { readonly status: "pending" | "expired"; readonly attempt: PendingAttempt }
  | { readonly status: "invalid" | "unavailable" };

type StoragePort = Pick<Storage, "getItem" | "setItem" | "removeItem">;
const categories: readonly string[] = ["MEDICAL", "LOAN", "FRIENDS_FAMILY", "INVESTMENTS", "OTHERS"];

function object(value: unknown, keys: readonly string[]): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)
      || Object.keys(value).some(key => !keys.includes(key))) {
    throw new Error("Unexpected recovery fields");
  }
  return value as Record<string, unknown>;
}
function id(value: unknown): string {
  if (typeof value !== "string" || !/^[1-9][0-9]{0,18}$/.test(value)
      || BigInt(value) > 9223372036854775807n) throw new Error("Invalid recovery identifier");
  return value;
}
function optionalText(value: unknown, limit: number): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || value.length > limit || !value.trim() || value !== value.trim()) {
    throw new Error("Recovery text must already be normalized");
  }
  return value;
}
function intent(value: unknown): PendingIntent {
  const input = object(value, ["operation", "payload", "transactionId"]);
  if (input.operation === "CREATE") {
    if (input.transactionId !== undefined) throw new Error("CREATE must not target another payment");
    const body = object(input.payload, ["sourceAccountId", "beneficiaryId", "amount", "purpose", "customerReference", "category"]);
    if (typeof body.amount !== "string" || !/^(0|[1-9][0-9]{0,15})\.[0-9]{2}$/.test(body.amount)) {
      throw new Error("Recovery amount must be an exact two-decimal string");
    }
    const cents = BigInt(body.amount.replace(".", ""));
    if (cents < 100n) throw new Error("Payment must be at least INR 1.00");
    const purpose = optionalText(body.purpose, body.category === "OTHERS" ? 140 : 280);
    const customerReference = optionalText(body.customerReference, 100);
    if (cents > 10000000n) {
      if (typeof body.category !== "string" || !categories.includes(body.category)) {
        throw new Error("High-value recovery needs the original category");
      }
      if (body.category === "OTHERS" && purpose === undefined) throw new Error("OTHERS requires purpose");
    } else if (body.category !== undefined) {
      throw new Error("Lower-value payment must omit category");
    }
    return Object.freeze({ operation: "CREATE", payload: Object.freeze({
      sourceAccountId: id(body.sourceAccountId), beneficiaryId: id(body.beneficiaryId), amount: body.amount,
      ...(purpose === undefined ? {} : { purpose }),
      ...(customerReference === undefined ? {} : { customerReference }),
      ...(body.category === undefined ? {} : { category: body.category as PaymentCategory })
    }) });
  }
  const transactionId = id(input.transactionId);
  if (input.operation === "AUTHORIZE") {
    const body = object(input.payload, ["confirmed"]);
    if (body.confirmed !== true) throw new Error("Authorization must be explicitly confirmed");
    return Object.freeze({ operation: "AUTHORIZE", transactionId, payload: Object.freeze({ confirmed: true as const }) });
  }
  if (["CANCEL", "OTP_ISSUE", "OTP_RESEND"].includes(String(input.operation)) && input.payload === undefined) {
    return Object.freeze({ operation: input.operation as "CANCEL" | "OTP_ISSUE" | "OTP_RESEND", transactionId });
  }
  // OTP_VERIFY is deliberately excluded: the OTP must never enter browser storage.
  throw new Error("Operation cannot be persisted for recovery");
}
function attempt(value: unknown): PendingAttempt {
  const row = object(value, ["version", "userId", "idempotencyKey", "createdAt", "expiresAt", "intent"]);
  if (row.version !== 1 || typeof row.idempotencyKey !== "string"
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(row.idempotencyKey)
      || typeof row.createdAt !== "number" || !Number.isSafeInteger(row.createdAt) || row.createdAt < 0
      || typeof row.expiresAt !== "number" || !Number.isSafeInteger(row.expiresAt)
      || row.expiresAt - row.createdAt !== PENDING_TTL_MS) {
    throw new Error("Invalid recovery record");
  }
  return Object.freeze({ version: 1, userId: id(row.userId), idempotencyKey: row.idempotencyKey,
    createdAt: row.createdAt, expiresAt: row.expiresAt, intent: intent(row.intent) });
}

/** One unresolved mutation per signed-in user/tab. Callers must reconcile before replacing it. */
export class PendingPaymentStore {
  constructor(private readonly storage: StoragePort,
              private readonly now: () => number = Date.now,
              private readonly newKey: () => string = () => globalThis.crypto.randomUUID()) {}

  read(userId: string): RecoveryState {
    id(userId);
    let raw: string | null;
    try { raw = this.storage.getItem(PENDING_STORAGE_KEY); }
    catch { return { status: "unavailable" }; }
    if (raw === null) return { status: "empty" };
    let saved: PendingAttempt;
    try {
      if (raw.length > 8192) throw new Error("Oversized recovery data");
      saved = attempt(JSON.parse(raw));
    } catch { return { status: "invalid" }; }
    if (saved.userId !== userId) {
      // Identity changes must never expose the previous customer's draft.
      try { this.storage.removeItem(PENDING_STORAGE_KEY); }
      catch { return { status: "unavailable" }; }
      return { status: "empty" };
    }
    const observed = this.now();
    const expired = !Number.isSafeInteger(observed) || observed < saved.createdAt || observed >= saved.expiresAt;
    // Keep an expired blocker until explicit reconciliation; do not silently allow a new payment.
    return { status: expired ? "expired" : "pending", attempt: saved };
  }

  begin(userId: string, request: PendingIntent): PendingAttempt {
    if (this.read(userId).status !== "empty") {
      throw new Error("Resolve the saved operation before starting another");
    }
    const createdAt = this.now();
    const saved = attempt({ version: 1, userId, idempotencyKey: this.newKey(), createdAt,
      expiresAt: createdAt + PENDING_TTL_MS, intent: request });
    const serialized = JSON.stringify(saved);
    try {
      this.storage.setItem(PENDING_STORAGE_KEY, serialized);
      if (this.storage.getItem(PENDING_STORAGE_KEY) !== serialized) throw new Error("Recovery write not retained");
    } catch {
      // Caller must NOT send a mutation if durable per-tab recovery could not be written.
      throw new Error("Browser recovery storage is unavailable; payment was not submitted by this module");
    }
    return saved;
  }

  clearAfterReconciliation(userId: string, idempotencyKey: string): void {
    const state = this.read(userId);
    if ((state.status !== "pending" && state.status !== "expired") || state.attempt.idempotencyKey !== idempotencyKey) {
      throw new Error("Recovery key does not match the reconciled operation");
    }
    this.storage.removeItem(PENDING_STORAGE_KEY);
  }

  /** Only logout/identity reset calls this; warn about unresolved outcomes before logout. */
  clearForLogout(): void {
    this.storage.removeItem(PENDING_STORAGE_KEY);
  }
}
