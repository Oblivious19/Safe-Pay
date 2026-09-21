import { request, query } from './apiClient';
import { Notice, Page } from './types';
import { validId } from '../utils/money';
import { checkedPage } from './transactionService';
export const notifications = {
  list: async (page = 0) => checkedPage(await request<Page<Notice>>('/notifications' + query({page, size: 10}))),
  read: (id: string) => request<Notice>('/notifications/' + validId(id) + '/read', {method: 'PATCH'})
};
