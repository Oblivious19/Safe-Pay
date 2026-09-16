// Payment operation keys survive a reload in the same browser tab. No password,
// payment payload, identity, cookie or authentication token is stored here.
const storageKey = "safepay.paymentOperationKeys.v1";
const operationPattern = /^(cancel|verify):[1-9]\d*$/;
function read(): Record<string, string> {
  try {
    if (typeof sessionStorage === "undefined") return {};
    const parsed: unknown = JSON.parse(sessionStorage.getItem(storageKey) || "{}");
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).filter(([operation, key]) =>
      operationPattern.test(operation) && typeof key === "string" && /^[A-Za-z0-9-]{1,100}$/.test(key)));
  } catch { return {}; }
}
export function readRetryKey(operation: string): string | undefined { return read()[operation]; }
export function saveRetryKey(operation: string, key?: string): void {
  if (!operationPattern.test(operation)) return;
  try {
    if (typeof sessionStorage === "undefined") return;
    const saved = read();
    if (key) saved[operation] = key; else delete saved[operation];
    sessionStorage.setItem(storageKey, JSON.stringify(saved));
  } catch { /* The in-memory key remains available when browser storage is disabled. */ }
}
export function clearRetryKeys(): void {
  try { if (typeof sessionStorage !== "undefined") sessionStorage.removeItem(storageKey); } catch { /* Storage is optional. */ }
}
