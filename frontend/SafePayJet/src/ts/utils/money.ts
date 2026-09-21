/** No money or identifier ever passes through binary floating point. */
export function decimal(value: string): string {
  if (!/^(0|[1-9]\d{0,15})(\.\d{1,2})?$/.test(value)) throw new Error('Enter an amount with up to 16 whole digits and two decimal places; no commas or exponent notation.');
  const [whole, fraction = ''] = value.split('.');
  return whole + '.' + fraction.padEnd(2, '0');
}
export function minor(value: string): bigint { return BigInt(decimal(value).replace('.', '')); }
export function paymentAmount(value: string): string {
  const normalized = decimal(value.trim());
  if (minor(normalized) < 100n) throw new Error('Minimum payment is ₹1.00.');
  return normalized;
}
export function rupees(value: string | null | undefined): string {
  if (value == null) return 'Unavailable';
  try { const [whole, cents] = decimal(value).split('.'); return '₹' + BigInt(whole).toLocaleString('en-IN') + '.' + cents; }
  catch { return 'Invalid amount — refresh required'; }
}
export function validId(value: unknown): string {
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n) throw new Error('Invalid identifier.');
  return value;
}
