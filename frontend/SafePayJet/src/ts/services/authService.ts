import * as ko from 'knockout';
import { request, configureSession } from './apiClient';
import { ApiError } from './apiError';
import { Role, Profile } from './types';
import { PendingPaymentStore } from './pendingPayment';
import { validId } from '../utils/money';
export interface Session { accessToken: string; tokenType: string; expiresAt: string; userId: string; authorities: Role[]; }
export const session = ko.observable<Session | null>(null);
let renewal: Promise<string> | undefined;
let generation = 0;
const channel = typeof BroadcastChannel === 'function' ? new BroadcastChannel('safepay.session') : null;
function reset(): void { generation++; session(null); }
channel?.addEventListener('message', event => { if (event.data === 'logout' || event.data === 'identity-change') { reset(); try { new PendingPaymentStore(sessionStorage).clearForLogout(); } catch { /* Storage remains blocked; payment submission will fail closed. */ } } });
async function locked<T>(action: () => Promise<T>): Promise<T> {
  if (!navigator.locks || !channel) throw new Error('This prototype requires current Chrome or Edge with browser locks and tab coordination enabled.');
  return navigator.locks.request('safepay.refresh-cookie', action);
}
function accept(result: Session, expected?: string): string {
  if (!result || !result.accessToken || result.tokenType !== 'Bearer' || !Array.isArray(result.authorities) || !Number.isFinite(Date.parse(result.expiresAt)) || (expected && result.userId !== expected)) { reset(); throw new ApiError('Session identity changed. Sign in again.', 401); }
  try { validId(result.userId); if (result.authorities.some(role => !['CUSTOMER','SYSTEM_ADMIN','RISK_OFFICER','AUDITOR'].includes(role))) throw new Error('Unsupported authority'); }
  catch { reset(); throw new ApiError('Invalid session response. Sign in again.',401); }
  session(result); return result.accessToken;
}
async function csrf(): Promise<Record<string, string>> {
  const result = await request<{headerName: string; token: string}>('/auth/csrf', {public: true});
  if (!result.headerName || !result.token) throw new Error('CSRF protection is unavailable.');
  return {[result.headerName]: result.token};
}
export function renew(): Promise<string> {
  if (renewal) return renewal;
  const epoch = generation; const user = session()?.userId;
  renewal = locked(async () => {
    if (epoch !== generation) throw new ApiError('Session changed.', 401);
    const result = await request<Session>('/auth/refresh', {public: true, method: 'POST', headers: await csrf()});
    if (epoch !== generation) throw new ApiError('Session changed.', 401);
    return accept(result, user);
  }).catch(error => { if (epoch === generation) reset(); throw error; }).finally(() => { renewal = undefined; });
  return renewal;
}
export async function token(): Promise<string> {
  const current = session();
  if (current && Date.parse(current.expiresAt) - Date.now() > 30000) return current.accessToken;
  return renew();
}
configureSession(token, renew, () => session()?.userId || null, reset);
export async function restore(): Promise<void> { try { await renew(); } catch (error) { if (!(error instanceof ApiError && (error.status === 401 || error.status === 403))) throw error; } }
export async function login(loginIdentifier: string, password: string): Promise<void> {
  await locked(async () => { const result = await request<Session>('/auth/login', {public: true, method: 'POST', body: {loginIdentifier, password}}); generation++; accept(result); channel?.postMessage('identity-change'); });
}
export async function logout(): Promise<void> {
  const access = await token();
  await locked(async () => { await request<void>('/auth/logout', {public: true, method: 'POST', headers: {...await csrf(), Authorization: 'Bearer ' + access}}); reset(); new PendingPaymentStore(sessionStorage).clearForLogout(); channel?.postMessage('logout'); });
}
export function register(body: {fullName: string; email: string; mobileNumber: string; password: string}): Promise<Profile> { return request('/auth/register', {public: true, method: 'POST', body}); }
export function passwordProblem(password: string, email: string, name: string): string {
  if (password.length < 12 || password.length > 72 || new TextEncoder().encode(password).length > 72) return 'Use 12–72 characters and no more than 72 UTF-8 bytes.';
  const compact = password.toLowerCase().replace(/[\s\W_]/g, '');
  if (['password1234', '123456789012', 'qwerty123456', 'safepay12345', 'letmein123456'].includes(compact) || /^(.)\1+$/.test(compact)) return 'Choose a less predictable passphrase.';
  if ([email.split('@')[0], name.replace(/\s/g, '')].some(x => x.length >= 4 && compact.includes(x.toLowerCase()))) return 'Avoid using your name or email in your passphrase.';
  return '';
}
