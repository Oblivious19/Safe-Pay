import { apiClient } from "./apiClient";
import { ApiError } from "./apiError";
import { adminUserService, AdminUser } from "./adminUserService";
export interface AdminBookRow {riskTier:string;}
export interface AdminTransactionRow {transactionId:number;transactionRef:string;customerName:string;customerEmail:string;beneficiaryName:string;amount:number;state:string;riskTier:string;createdAt:string;}
export interface AdminTransactionPage {items:AdminTransactionRow[];page:number;size:number;totalItems:number;totalPages:number;}
export type AdminUserSnapshot = AdminUser;
import { TransactionSummary, DailyTransactionSummary } from "./types";



export const adminReportService = {
  summary(): Promise<TransactionSummary> {
    apiClient.useAdminCsrf(true);
    return apiClient.request("/api/admin/reports/transactions/summary");
  },
  async daily(from: string, to: string): Promise<DailyTransactionSummary[]> {
    const valid = (s: string): boolean => /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s)) && new Date(s).toISOString().slice(0, 10) === s;
    if (!valid(from) || !valid(to) || from > to || Date.parse(to) - Date.parse(from) > 365 * 86400000) {
      throw new ApiError(400, "Choose a valid date range of at most 366 days.", "validation");
    }
    return apiClient.request(`/api/admin/reports/transactions/daily?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
  },
  book(): Promise<{ payments: AdminBookRow[]; users: AdminUserSnapshot[] }> {
    return adminUserService.users().then(users=>({payments:[],users}));
  }
  ,transactions(state:string,risk:string,query:string,page:number): Promise<AdminTransactionPage> {
    const params=new URLSearchParams({page:String(page),size:'20',sort:'createdAt'}); if(state)params.set('state',state);if(risk)params.set('risk',risk);if(query.trim())params.set('query',query.trim());
    apiClient.useAdminCsrf(true);return apiClient.request(`/api/admin/reports/transactions?${params}`);
  }
};
