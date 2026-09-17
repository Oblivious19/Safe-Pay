import { apiClient } from "./apiClient";
import { ApiError, requireText, resourceId } from "./apiError";
import { TransactionRequest, PaymentTransaction, TransactionState } from "./types";

export function newIdempotencyKey(): string { return crypto.randomUUID(); }
export const transactionService = {
  list(state?: TransactionState): Promise<PaymentTransaction[]> {
    return apiClient.request(`/api/transactions${state ? `?state=${encodeURIComponent(state)}` : ""}`);
  },
  async get(id: number): Promise<PaymentTransaction> {
    return apiClient.request(`/api/transactions/${resourceId(id)}`);
  },
  async create(input: TransactionRequest, idempotencyKey: string): Promise<PaymentTransaction> {
    resourceId(input.fromAccountId); resourceId(input.beneficiaryId); requireText(idempotencyKey, "Idempotency key");
    if (!/^\d{1,16}(\.\d{1,2})?$/.test(input.amount) || !/[1-9]/.test(input.amount)) {
      throw new ApiError(400, "Enter a positive amount with at most two decimal places.", "validation");
    }
    if (new TextEncoder().encode(input.purpose || "").length > 255 || new TextEncoder().encode(idempotencyKey).length > 100) {
      throw new ApiError(400, "Purpose or idempotency key is too long.", "validation");
    }
    const { fromAccountId, beneficiaryId, amount, purpose } = input;
    return apiClient.request("/api/transactions", {
      method: "POST", csrf: true, idempotencyKey, body: { fromAccountId, beneficiaryId, amount, purpose }
    });
  },
  async cancel(id: number, idempotencyKey: string): Promise<PaymentTransaction> {
    resourceId(id); requireText(idempotencyKey, "Idempotency key");
    return apiClient.request(`/api/transactions/${id}/cancel`, { method: "POST", csrf: true, idempotencyKey });
  },
  async verify(id: number, password: string, idempotencyKey: string): Promise<PaymentTransaction> {
    resourceId(id); requireText(password, "Password"); requireText(idempotencyKey, "Idempotency key");
    return apiClient.request(`/api/transactions/${id}/verify`, {
      method: "POST", csrf: true, idempotencyKey, body: { password }
    });
  }
};
