import { TransactionRequest } from "./types";
// In-memory only: keep one unacknowledged reviewed payment through view replacement.
// No password, cookie or authentication token is stored. Auth boundaries clear it.
export interface PendingPayment { request: TransactionRequest; key: string; session: number; accountLabel: string; beneficiaryLabel: string; }
let pending: PendingPayment | null = null;
export function readPendingPayment(session: number): PendingPayment | null {
  return pending && pending.session === session ? { ...pending, request: { ...pending.request } } : null;
}
export function rememberPendingPayment(value: PendingPayment): void {
  pending = { ...value, request: { ...value.request } };
}
export function clearPendingPayment(key?: string, session?: number): void {
  if (key === undefined || (pending?.key === key && pending.session === session)) pending = null;
}