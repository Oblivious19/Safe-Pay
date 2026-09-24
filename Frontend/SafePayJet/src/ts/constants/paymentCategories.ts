/** Matches the persisted backend enum and Aditya's review priority. */
export const PAYMENT_CATEGORIES = [
  { value: "MEDICAL", label: "Medical" },
  { value: "LOAN", label: "Loan" },
  { value: "FRIENDS_FAMILY", label: "Friends & Family" },
  { value: "INVESTMENTS", label: "Investments" },
  { value: "OTHERS", label: "Others" }
] as const;
export type PaymentCategory = typeof PAYMENT_CATEGORIES[number]["value"];
export type QueueOrder = "PRIORITY" | "OLDEST";
export function categoryApplies(amount: string): boolean {
  if (!/^\d{1,16}(\.\d{1,2})?$/.test(amount.trim())) return false;
  const [whole, fraction = ""] = amount.trim().split(".");
  const integer = whole.replace(/^0+(?=\d)/, "");
  return integer.length > 6 || (integer.length === 6 &&
    (integer > "100000" || (integer === "100000" && /[1-9]/.test(fraction))));
}
export function categoryLabel(value?: string | null): string {
  return PAYMENT_CATEGORIES.find(item => item.value === value)?.label || "Not specified";
}
export function categoryProblem(amount: string, category?: string | null): string {
  if (categoryApplies(amount)) return PAYMENT_CATEGORIES.some(item => item.value === category)
    ? "" : "Choose a payment category for amounts above ₹1,00,000.";
  return category != null && category !== "" ? "Category is allowed only above ₹1,00,000." : "";
}
type QueueRow = { category?: string | null; createdAt: string; transactionId: number };
export function orderedPayments<T extends QueueRow>(rows: readonly T[], order: QueueOrder = "PRIORITY"): T[] {
  const rank = (row: T): number => {
    const index = PAYMENT_CATEGORIES.findIndex(item => item.value === row.category);
    return index < 0 ? PAYMENT_CATEGORIES.length : index;
  };
  return rows.slice().sort((a, b) => (order === "PRIORITY" ? rank(a) - rank(b) : 0)
    || a.createdAt.localeCompare(b.createdAt) || a.transactionId - b.transactionId);
}
