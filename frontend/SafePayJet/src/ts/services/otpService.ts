import { request } from './apiClient';
import { wireId } from './wireCodec';
import { validId } from '../utils/money';
import { session } from './authService';
import { ApiError } from './apiError';
import { Verification } from './types';
// Sensitive retry payload stays in memory only and survives view-model navigation.
let pending: {user: string; transactionId: string; challengeId: string; otp: string; key: string} | null = null;
let busy = false;
session.subscribe(() => { if (pending && pending.user !== session()?.userId) pending = null; });
export function verificationPending(): boolean { return pending !== null; }
export function verificationTransactionId(): string | null { return pending?.transactionId || null; }
export async function verify(transactionId: string, challengeId: string, otp: string, replay = false): Promise<Verification> {
  if (busy) throw new Error('Verification is still running.');
  const user = session()?.userId; if (!user) throw new Error('Please sign in.');
  if (pending && pending.transactionId !== transactionId) throw new Error('Open payment ' + pending.transactionId + ' to resolve its verification first.');
  if (pending && !replay) throw new Error('Resolve the previous verification before entering another code.');
  if (!pending) { if (replay || !/^\d{6}$/.test(otp)) throw new Error('Enter the six-digit code.'); pending = {user, transactionId: validId(transactionId), challengeId: validId(challengeId), otp, key: crypto.randomUUID()}; }
  const attempt = pending; busy = true;
  try { const result = await request<Verification>('/transactions/' + attempt.transactionId + '/otp/verify', {method: 'POST', key: attempt.key, body: {challengeId: wireId(attempt.challengeId), otp: attempt.otp}}); if (!result || result.transactionId !== attempt.transactionId || result.challengeId !== attempt.challengeId || result.verified !== true) throw new ApiError('Verification response could not be confirmed. Recover its original outcome.',0,'INVALID_RESPONSE'); pending = null; return result; }
  catch (error) { if (error instanceof ApiError && !error.uncertain) pending = null; throw error; }
  finally { busy = false; }
}
export function reconcileVerification(transactionId: string, state: string): void { if (pending?.transactionId === transactionId && state !== 'VERIFICATION_REQUIRED') pending = null; }
