import { decode, encode } from './wireCodec';
import { ApiError, problem } from './apiError';
// Local prototype origin matches A's existing localhost:8000 browser contract.
export const API_ORIGIN = 'http://localhost:8080';
let credentials: () => Promise<string> = async () => { throw new ApiError('Please sign in.', 401); };
let refresh: () => Promise<string> = credentials;
let identity: () => string | null = () => null;
let expire: () => void = () => {};
export function configureSession(token: typeof credentials, renew: typeof refresh, user: typeof identity, invalidate: () => void = () => {}): void { credentials = token; refresh = renew; identity = user; expire = invalidate; }
export interface RequestOptions { method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'; body?: unknown; key?: string; public?: boolean; headers?: Record<string, string>; }
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('Invalid API path');
  const method = options.method || 'GET';
  let token = options.public ? '' : await credentials();
  const user = identity();
  for (let attempt = 0; attempt < 2; attempt++) {
    const headers: Record<string, string> = { Accept: 'application/json', 'X-Correlation-ID': crypto.randomUUID(), ...options.headers };
    if (token) headers.Authorization = 'Bearer ' + token;
    if (options.key) headers['Idempotency-Key'] = options.key;
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    let response: Response;
    try { response = await fetch(API_ORIGIN + '/api/v1' + path, { method, headers, credentials: 'include', cache: 'no-store', body: options.body === undefined ? undefined : encode(options.body), signal: AbortSignal.timeout(30000) }); }
    catch { throw new ApiError(method === 'GET' ? 'Unable to reach SafePay. Check the connection and refresh.' : 'No response received. The outcome is unknown; do not start a replacement request.'); }
    if (!options.public && identity() !== user) throw new ApiError('Session changed. Sign in again before continuing.', 401, 'SESSION_CHANGED');
    // Only a rejected authentication response is retried; money requests keep the original body/key.
    if (response.status === 401 && !options.public && attempt === 0) { token = await refresh(); if (identity() !== user) throw new ApiError('Session changed.', 401); continue; }
    let body: unknown;
    try { const text = await response.text(); body = text ? decode(text) : undefined; }
    catch { throw new ApiError('The server response could not be read safely. Refresh the outcome.', 0, 'INVALID_RESPONSE'); }
    if (!response.ok) { if (response.status === 401 && !options.public) expire(); throw problem(response.status, body, response.headers.get('X-Correlation-ID') || ''); }
    return body as T;
  }
  throw new ApiError('Please sign in again.', 401);
}
export function query(params: Record<string, string | number | undefined>): string {
  const values = new URLSearchParams(); Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== '') values.set(key, String(value)); });
  return values.size ? '?' + values.toString() : '';
}
