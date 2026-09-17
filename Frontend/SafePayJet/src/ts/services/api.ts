/** Compatibility facade for existing view models. New code should use the named services.
 * Legacy email/account-owner arguments are ignored, never sent as caller identity.
 */
import { apiClient } from "./apiClient";
import { accountService } from "./accountService";
import { transactionService, newIdempotencyKey } from "./transactionService";
import { Account, PaymentTransaction, TransactionRequest } from "./types";
export { Account, PaymentTransaction } from "./types";
export { apiClient, configureApi, DEFAULT_API_BASE_URL } from "./apiClient";
export { ApiError } from "./apiError";
export { authService } from "./authService";
export { accountService } from "./accountService";
export { transactionService, newIdempotencyKey } from "./transactionService";
export { adminReportService } from "./adminReportService";

export interface Beneficiary {
  beneficiaryId: number; beneficiaryName: string; bankAccountNumber: string;
  ifsc: string; status: string; createdAt: string;
}
type BeneficiaryInput = { beneficiaryName: string; bankAccountNumber: string; ifsc: string; };

export async function getAccounts(_legacyEmail?: string): Promise<Account[]> {
  return accountService.list();
}
export function getTransactions(_legacyEmail?: string): Promise<PaymentTransaction[]> {
  return transactionService.list();
}
// Retain the pre-existing beneficiary exports only; no new beneficiary API is introduced.
export function getBeneficiaries(_legacyEmail?: string): Promise<Beneficiary[]> {
  return apiClient.request("/api/beneficiaries");
}
export function addBeneficiary(_legacyAccountId: number, _legacyEmail: string, input: BeneficiaryInput): Promise<Beneficiary> {
  const { beneficiaryName, bankAccountNumber, ifsc } = input;
  return apiClient.request("/api/beneficiaries", { method: "POST", csrf: true, body: { accountId: _legacyAccountId, beneficiaryName, bankAccountNumber, ifsc } });
}
// Old screens do not own an idempotency key yet. Retain the key after an ambiguous failure.
const pendingPayments = new Map<string, string>();
export async function initiateTransaction(input: TransactionRequest & { userEmail?: string }): Promise<PaymentTransaction> {
  const { fromAccountId, beneficiaryId, amount, purpose } = input;
  const payload = { fromAccountId, beneficiaryId, amount, purpose };
  const signature = JSON.stringify(payload);
  const key = pendingPayments.get(signature) || newIdempotencyKey();
  pendingPayments.set(signature, key);
  const result = await transactionService.create(payload, key);
  pendingPayments.delete(signature);
  return result;
}
export function cancelTransaction(id: number, _legacyEmail?: string): Promise<PaymentTransaction> {
  return transactionService.cancel(id, newIdempotencyKey());
}
