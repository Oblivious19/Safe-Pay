/** Compatibility facade for existing view models. New code should use the named services.
 * Legacy email arguments are ignored. Account IDs select a resource; the server checks ownership.
 */
import { apiClient } from "./apiClient";
import { accountService } from "./accountService";
import { customerService } from "./customerService";
import { ApiError, resourceId } from "./apiError";
import { validateBeneficiary } from "./beneficiaryValidation";
export { validateBeneficiary } from "./beneficiaryValidation";
import { readRetryKey, saveRetryKey } from "./retryKeys";
import { transactionService, newIdempotencyKey } from "./transactionService";
import { Account, PaymentTransaction, TransactionRequest } from "./types";
export { Account, PaymentTransaction } from "./types";
export { apiClient, configureApi, DEFAULT_API_BASE_URL } from "./apiClient";
export { ApiError } from "./apiError";
export { authService } from "./authService";
export { accountService } from "./accountService";
export { transactionService, newIdempotencyKey } from "./transactionService";
export { adminReportService } from "./adminReportService";
export const users = customerService;

export interface Beneficiary {
  beneficiaryId: number; accountId: number; beneficiaryName: string; bankAccountNumber: string;
  ifsc: string; status: string; createdAt: string;
}
type BeneficiaryInput = { beneficiaryName: string; bankAccountNumber: string; ifsc: string; };

export async function getAccounts(_legacyEmail?: string): Promise<Account[]> {
  return accountService.list();
}
export function getTransactions(_legacyEmail?: string): Promise<PaymentTransaction[]> {
  return transactionService.list();
}
// Default remains active beneficiaries for existing payment screens.
export function getBeneficiaries(_legacyEmail?: string, includeInactive = false, accountId?: number): Promise<Beneficiary[]> {
  const query: string[] = [];
  if (accountId !== undefined) query.push("accountId=" + resourceId(accountId));
  if (includeInactive) query.push("includeInactive=true");
  return apiClient.request(`/api/beneficiaries${query.length ? "?" + query.join("&") : ""}`);
}
export async function getBeneficiary(id: number): Promise<Beneficiary> {
  return apiClient.request(`/api/beneficiaries/${resourceId(id)}`);
}
export async function deleteBeneficiary(id: number): Promise<void> {
  return apiClient.request(`/api/beneficiaries/${resourceId(id)}`, { method: "DELETE", csrf: true });
}
export async function updateBeneficiaryStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<Beneficiary> {
  if (status !== "ACTIVE" && status !== "INACTIVE") throw new ApiError(400, "Choose an active or inactive status.", "validation");
  return apiClient.request(`/api/beneficiaries/${resourceId(id)}/status`, { method: "PATCH", csrf: true, body: { status } });
}
export function addBeneficiary(accountId: number, _legacyEmail: string, input: BeneficiaryInput): Promise<Beneficiary> {
  resourceId(accountId);
  const errors = validateBeneficiary(input);
  if (Object.keys(errors).length) throw new ApiError(400, Object.values(errors)[0]!, "validation");
  const beneficiaryName = input.beneficiaryName.trim(), bankAccountNumber = input.bankAccountNumber.trim(), ifsc = input.ifsc.trim().toUpperCase();
  return apiClient.request("/api/beneficiaries", { method: "POST", csrf: true, body: { accountId, beneficiaryName, bankAccountNumber, ifsc } });
}
// Keep each logical operation's key after a failed/uncertain response, including navigation.
// Only cancel/verify keys also survive a tab reload; passwords are never stored.
const pendingOperations = new Map<string, string>();
let operationSession = -1;
async function withRetryKey<T>(signature: string, operation: (key: string) => Promise<T>): Promise<T> {
  const session = apiClient.sessionRevision();
  if (operationSession !== session) { pendingOperations.clear(); operationSession = session; }
  const key = pendingOperations.get(signature) || readRetryKey(signature) || newIdempotencyKey();
  pendingOperations.set(signature, key);
  saveRetryKey(signature, key);
  const result = await operation(key);
  if (operationSession === session && pendingOperations.get(signature) === key) {
    pendingOperations.delete(signature); saveRetryKey(signature);
  }
  return result;
}
export async function initiateTransaction(input: TransactionRequest & { userEmail?: string }, explicitKey?: string): Promise<PaymentTransaction> {
  const { fromAccountId, beneficiaryId, amount, purpose } = input;
  const payload = { fromAccountId, beneficiaryId, amount, purpose };
  // A reviewed payment owns its key until the view accepts the response, including
  // when a successful response arrives after the view has been disconnected.
  if (explicitKey !== undefined) return transactionService.create(payload, explicitKey);
  return withRetryKey("create:" + JSON.stringify(payload), key => transactionService.create(payload, key));
}
export function cancelTransaction(id: number, _legacyEmail?: string): Promise<PaymentTransaction> {
  return withRetryKey("cancel:" + id, key => transactionService.cancel(id, key));
}
export function verifyTransaction(id: number, password: string): Promise<PaymentTransaction> {
  return withRetryKey("verify:" + id, key => transactionService.verify(id, password, key));
}
