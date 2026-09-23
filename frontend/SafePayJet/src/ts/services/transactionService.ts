import { request, query } from './apiClient';
import { wireId, wireAmount } from './wireCodec';
import { Transaction, TransactionSummary, Page, RiskExplanation, AuditEvent } from './types';
import { PendingAttempt, PendingIntent, PendingPaymentStore } from './pendingPayment';
import { validId, decimal } from '../utils/money';
import { ApiError } from './apiError';
import { session } from './authService';
import { verificationPending } from './otpService';
export type PaymentFilter = {sourceAccountId?: string; state?: string; from?: string; to?: string; page?: number; size?: number};
export function checkedPage<T>(value: Page<T>): Page<T> {
  if (!value || !Array.isArray(value.items) || !Number.isSafeInteger(value.page) || !Number.isSafeInteger(value.totalPages) || typeof value.last !== 'boolean') throw new Error('Invalid paged response. Refresh required.');
  return value;
}
export const transactions = {
  async list(filter: PaymentFilter): Promise<Page<TransactionSummary>> { return checkedPage(await request<Page<TransactionSummary>>('/transactions' + query({...filter, size: filter.size || 10}))); },
  async get(id: string): Promise<Transaction> {
    const result = await request<Transaction>('/transactions/' + validId(id));
    if (result.transactionId !== id || typeof result.state !== 'string') throw new Error('Invalid transaction response.');
    decimal(result.amount); return result;
  },
  risk: (id: string) => request<RiskExplanation>('/transactions/' + validId(id) + '/risk-explanation'),
  audit: async (id: string, page = 0) => checkedPage(await request<Page<AuditEvent>>('/transactions/' + validId(id) + '/audit' + query({page, size: 10})))
};
export function recoveryStore(): PendingPaymentStore { return new PendingPaymentStore(sessionStorage); }
export function recovery() { const user = session()?.userId; if (!user) throw new Error('Please sign in.'); return recoveryStore().read(user); }
let sending = false;
export function paymentBusy(): boolean { return sending; }
export async function perform<T>(intent?: PendingIntent, safePayPin?: string): Promise<T> {
  if (sending) throw new Error('A payment request is still running.');
  if (verificationPending()) throw new Error('Resolve the uncertain OTP verification before another payment operation.');
  const userId = session()?.userId; if (!userId) throw new Error('Please sign in.');
  const store = recoveryStore();
  let saved: PendingAttempt;
  if (intent) saved = store.begin(userId, intent);
  else { const current = store.read(userId); if (current.status !== 'pending') throw new Error('This record cannot be replayed. Review payment history; do not submit a replacement.'); saved = current.attempt; }
  const op = saved.intent;
  let path = '/transactions'; let body: unknown;
  if (op.operation === 'CREATE') body = {...op.payload, sourceAccountId: wireId(op.payload.sourceAccountId), beneficiaryId: wireId(op.payload.beneficiaryId), amount: wireAmount(op.payload.amount), ...(safePayPin ? {safePayPin} : {})};
  else { path += '/' + validId(op.transactionId) + ({AUTHORIZE: '/authorize', CANCEL: '/cancel', OTP_ISSUE: '/otp', OTP_RESEND: '/otp/resend'}[op.operation]); if (op.operation === 'AUTHORIZE') body = op.payload; }
  sending = true;
  try {
    const result = await request<T>(path, {method: 'POST', key: saved.idempotencyKey, body});
    if (session()?.userId !== userId) throw new ApiError('Session changed. Reconcile the saved payment after sign-in.', 0);
    // A malformed success body is still an unknown outcome: retain its replay record.
    try {
      const row = result as Record<string, unknown>;
      validId(row.transactionId);
      if (op.operation !== 'CREATE' && row.transactionId !== op.transactionId) throw new Error('Transaction mismatch');
      if (op.operation === 'OTP_ISSUE' || op.operation === 'OTP_RESEND') {
        validId(row.challengeId);
        for (const key of ['expiresAt','resendAvailableAt','serverTime']) if (typeof row[key] !== 'string' || !Number.isFinite(Date.parse(row[key] as string))) throw new Error('Invalid challenge time');
        if (typeof row.remainingIssues !== 'number' || !Number.isSafeInteger(row.remainingIssues) || row.remainingIssues < 0) throw new Error('Invalid issue count');
      } else { if (typeof row.state !== 'string' || typeof row.amount !== 'string') throw new Error('Invalid payment'); decimal(row.amount); }
    } catch { throw new ApiError('The response could not be matched to the saved operation. Recover the original outcome before continuing.', 0, 'INVALID_RESPONSE'); }
    store.clearAfterReconciliation(userId, saved.idempotencyKey);
    window.dispatchEvent(new Event('safepay:financial-change'));
    return result;
  } catch (error) {
    // Confirmed rejection can be corrected; unknown outcomes and idempotency conflicts remain blocked.
    if (error instanceof ApiError && !error.uncertain && error.status >= 400 && error.status < 500 && session()?.userId === userId) store.clearAfterReconciliation(userId, saved.idempotencyKey);
    throw error;
  } finally { sending = false; }
}
