import { parse, stringify, LosslessNumber } from 'lossless-json';
import { validId, paymentAmount } from '../utils/money';
const counters = new Set(['page', 'size', 'totalPages', 'protectionSeconds', 'protectionRemainingMillis', 'remainingAttempts', 'remainingIssues']);
export function decode(text: string): unknown {
  return parse(text, (key, value) => {
    if (counters.has(key) && typeof value === 'string' && /^\d+$/.test(value)) {
      const count = Number(value);
      if (!Number.isSafeInteger(count)) throw new Error('Unsafe response counter');
      return count;
    }
    return value;
  }, value => value);
}
export function encode(value: unknown): string { const text = stringify(value); if (text === undefined) throw new Error('Missing request body'); return text; }
export function wireId(value: string): LosslessNumber { return new LosslessNumber(validId(value)); }
export function wireAmount(value: string): LosslessNumber { return new LosslessNumber(paymentAmount(value)); }
