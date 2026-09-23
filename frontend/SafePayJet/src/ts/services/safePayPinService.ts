import {request} from './apiClient';

type SafePayPinStatus = { configured: boolean; resetStatus: 'NONE'|'PENDING'|'APPROVED'|'REJECTED' };

export const safePayPin = {
  status: () => request<SafePayPinStatus>('/profile/safe-pay-pin/status'),
  setup: (pin: string, confirmation: string) =>
    request<SafePayPinStatus>('/profile/safe-pay-pin', {method: 'POST', body: {pin, confirmation}}),
  requestReset: () => request<SafePayPinStatus>('/profile/safe-pay-pin/reset-request', {method: 'POST'})
};
