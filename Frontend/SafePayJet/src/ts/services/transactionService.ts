import { apiClient } from "./apiClient";
import { ApiError, requireText, resourceId } from "./apiError";
import { TransactionRequest, PaymentTransaction, TransactionState } from "./types";
import { categoryProblem } from "../constants/paymentCategories";

export function newIdempotencyKey(): string { return crypto.randomUUID(); }
/** Anchor countdowns to the server duration, never interpret Oracle local timestamps as UTC. */
export function receivedPayment(tx: PaymentTransaction, started: number): PaymentTransaction {
  return {...tx, protectionDeadline: tx.state === "PROTECTED" && typeof tx.protectionRemainingMillis === "number"
    ? started + Math.max(0, tx.protectionRemainingMillis) : undefined};
}
async function paymentRequest(path: string, options: Parameters<typeof apiClient.request>[1] = {}): Promise<PaymentTransaction> {
  const started = Date.now(); return receivedPayment(await apiClient.request<PaymentTransaction>(path, options), started);
}
export const transactionService = {
  async list(state?: TransactionState): Promise<PaymentTransaction[]> {
    const started=Date.now(); const rows=await apiClient.request<PaymentTransaction[]>(`/api/transactions${state ? `?state=${encodeURIComponent(state)}` : ""}`);
    return rows.map(row=>receivedPayment(row,started));
  },
  async get(id: number): Promise<PaymentTransaction> {
    return paymentRequest(`/api/transactions/${resourceId(id)}`);
  },
  async create(input: TransactionRequest, idempotencyKey: string): Promise<PaymentTransaction> {
    resourceId(input.fromAccountId); resourceId(input.beneficiaryId); requireText(idempotencyKey, "Idempotency key");
    if (!/^\d{1,16}(\.\d{1,2})?$/.test(input.amount) || !/[1-9]/.test(input.amount)) {
      throw new ApiError(400, "Enter a positive amount with at most two decimal places.", "validation");
    }
    if (new TextEncoder().encode(input.purpose || "").length > 255 || new TextEncoder().encode(idempotencyKey).length > 100) {
      throw new ApiError(400, "Purpose or idempotency key is too long.", "validation");
    }
    const { fromAccountId, beneficiaryId, amount, category } = input;
    const problem = categoryProblem(amount, category);
    if (problem) throw new ApiError(400, problem, "validation");
    const purpose = category === "OTHERS" ? (input.purpose || "").trim() : input.purpose;
    if (category === "OTHERS" && (!purpose || purpose.length > 140)) {
      throw new ApiError(400, "Others requires a purpose of 1–140 characters.", "validation");
    }
    return paymentRequest("/api/transactions", {
      method: "POST", csrf: true, idempotencyKey,
      body: { fromAccountId, beneficiaryId, amount, purpose, ...(category ? { category } : {}) }
    });
  },
  async cancel(id: number, idempotencyKey: string): Promise<PaymentTransaction> {
    resourceId(id); requireText(idempotencyKey, "Idempotency key");
    return paymentRequest(`/api/transactions/${id}/cancel`, { method: "POST", csrf: true, idempotencyKey });
  }
};
