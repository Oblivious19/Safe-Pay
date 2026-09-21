export class ApiError extends Error {
  constructor(message: string, readonly status = 0, readonly code = 'NETWORK_UNCERTAIN', readonly trace = '', readonly fields: {field: string; message: string}[] = [], readonly remainingAttempts?: number) { super(message); }
  get uncertain(): boolean { return this.status === 0 || this.status >= 500 || this.status === 408 || this.code.startsWith('IDEMPOTENCY_'); }
}
export function problem(status: number, body: unknown, trace: string): ApiError {
  const p = body && typeof body === 'object' ? body as Record<string, unknown> : {};
  const safe = status >= 500 ? 'The service could not confirm this request. Check the outcome before trying again.' : typeof p.detail === 'string' ? p.detail : 'The request was not accepted.';
  const fields = Array.isArray(p.fieldErrors) ? p.fieldErrors.filter((x): x is {field: string; message: string} => !!x && typeof x.field === 'string' && typeof x.message === 'string') : [];
  return new ApiError(safe, status, typeof p.errorCode === 'string' ? p.errorCode : 'REQUEST_REJECTED', trace, fields, typeof p.remainingAttempts === 'number' ? p.remainingAttempts : undefined);
}
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message + (error.fields.length ? ' ' + error.fields.map(f => f.field + ': ' + f.message).join(' ') : '') + (error.remainingAttempts !== undefined ? ' Remaining attempts: ' + error.remainingAttempts + '.' : '') + (error.trace ? ' Reference: ' + error.trace : '');
  return error instanceof Error ? error.message : 'Unable to complete this action.';
}
