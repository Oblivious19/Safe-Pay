import { request } from './apiClient';
import { Beneficiary, BeneficiaryDraft } from './types';
import { validId } from '../utils/money';
export const beneficiaries = {
  list: () => request<Beneficiary[]>('/beneficiaries'),
  get: (id: string) => request<Beneficiary>('/beneficiaries/' + validId(id)),
  create: (body: BeneficiaryDraft) => request<Beneficiary>('/beneficiaries', {method: 'POST', body}),
  status: (id: string, status: 'ACTIVE' | 'DISABLED') => request<Beneficiary>('/beneficiaries/' + validId(id) + '/status', {method: 'PATCH', body: {status}})
};
