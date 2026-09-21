import { request } from './apiClient';
import { Account, Balance } from './types';
import { decimal, validId } from '../utils/money';
export const accounts = {
  list: () => request<Account[]>('/accounts'),
  async get(id: string):Promise<Account>{const value=await request<Account>('/accounts/'+validId(id));if(value.accountId!==id)throw Error('Unexpected account response.');return value;},
  async balance(id: string): Promise<Balance> {
    const result = await request<Balance>('/accounts/' + validId(id) + '/balance');
    if (result.accountId !== id || result.currency !== 'INR') throw new Error('Unexpected account balance response.');
    [result.currentBalance, result.reservedAmount, result.availableBalance].forEach(decimal);
    return result;
  }
};
