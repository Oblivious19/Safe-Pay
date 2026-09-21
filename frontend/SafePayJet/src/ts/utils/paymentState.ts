export const states = ['CREATED','AUTHORIZED','RISK_ASSESSED','PROTECTED','VERIFICATION_REQUIRED','PENDING_RISK_REVIEW','RELEASED','SETTLED','CANCELLED','FAILED'] as const;
export const descriptions: Record<string, string> = {
  CREATED: 'Instruction saved. No funds have been reserved. Review and authorize to continue.',
  AUTHORIZED: 'Authorization is processing. Refresh for the current outcome.',
  RISK_ASSESSED: 'Risk has been assessed. Refresh for the next step.',
  PROTECTED: 'Funds are reserved during protection. Undo remains subject to the server deadline.',
  VERIFICATION_REQUIRED: 'Verify by email code before this payment can enter officer review.',
  PENDING_RISK_REVIEW: 'Funds remain reserved while a risk officer reviews this payment.',
  RELEASED: 'Released for simulated settlement. Processing may still be pending.',
  SETTLED: 'Simulated settlement completed. This is not a real interbank transfer.',
  CANCELLED: 'Cancelled before settlement. Refresh the account balance to see the released reservation.',
  FAILED: 'This payment failed. Inspect its reason and balance before considering a new payment.'
};
export function label(value: string): string { return (value || '').split('_').map(x => x ? x[0] + x.slice(1).toLowerCase() : '').join(' '); }
export function description(value: string): string { return descriptions[value] || 'Unsupported state. Refresh; financial actions are disabled.'; }
export function knownState(value: string): boolean { return (states as readonly string[]).includes(value); }
export const categories = [{value:'MEDICAL', label:'Medical'}, {value:'LOAN',label:'Loan'}, {value:'FRIENDS_FAMILY',label:'Friends & Family'}, {value:'INVESTMENTS',label:'Investments'}, {value:'OTHERS',label:'Others'}];
