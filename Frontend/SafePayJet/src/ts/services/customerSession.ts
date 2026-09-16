import { ApiError } from "./apiError";

/** Keep transient request failures visible, but discard customer data on a lost session. */
export function endExpiredSession(error: unknown, clear: () => void): boolean {
  if (!(error instanceof ApiError) || error.status !== 401) return false;
  clear();
  window.location.replace("/login?reason=session-expired");
  return true;
}
