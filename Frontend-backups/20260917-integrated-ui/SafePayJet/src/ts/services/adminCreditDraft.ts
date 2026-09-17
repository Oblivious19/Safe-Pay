// Retain only the explicit simulated credit request across a reload in this tab.
// Login, logout and an expired session clear it. Never store passwords or session tokens.
export interface CreditDraft { accountId: number; amount: string; key: string; }
const storageKey = "safepay.adminCreditDraft.v1";
let memory: CreditDraft | null = null;
export function validCredit(amount: string): boolean {
  return /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,2})?$/.test(amount) && /[1-9]/.test(amount);
}
export function readCreditDraft(): CreditDraft | null {
  if (memory) return { ...memory };
  try {
    const value = JSON.parse(sessionStorage.getItem(storageKey) || "null");
    if (value && Number.isSafeInteger(value.accountId) && value.accountId > 0 && typeof value.amount === "string" && validCredit(value.amount)
      && typeof value.key === "string" && /^[A-Za-z0-9-]{1,100}$/.test(value.key)) memory = { accountId: value.accountId, amount: value.amount, key: value.key };
  } catch { /* Browser storage is optional. */ }
  return memory ? { ...memory } : null;
}
export function saveCreditDraft(value: CreditDraft): void {
  memory = { ...value };
  try { sessionStorage.setItem(storageKey, JSON.stringify(memory)); } catch { /* Keep the in-memory request. */ }
}
export function clearCreditDraft(): void {
  memory = null;
  try { sessionStorage.removeItem(storageKey); } catch { /* Browser storage is optional. */ }
}
