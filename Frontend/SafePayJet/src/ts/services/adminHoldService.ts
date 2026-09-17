import { apiClient } from "./apiClient";
import { requireText, resourceId } from "./apiError";
import { HeldPayment, PaymentTransaction } from "./types";
interface Pending { transactionId:number; transactionRef:string; amount:string; userId:number; customerName:string; fromAccountId:number; sourceAccountNumber:string; beneficiaryName:string; beneficiaryBankAccountNumber:string; beneficiaryIfsc:string; purpose:string; riskReason:string; createdAt:string; }
export const adminHoldService = {
  async list(): Promise<HeldPayment[]> {
    apiClient.useAdminCsrf(true);
    const rows=await apiClient.request<Pending[]>("/api/admin/transactions/hard-holds");
    return rows.map(row=>({...row,amount:Number(row.amount), customerEmail:"", riskTier:"VERY_HIGH",decision:"PENDING"}));
  },
  approve(id:number,idempotencyKey:string): Promise<PaymentTransaction> {
    resourceId(id);requireText(idempotencyKey,"Idempotency key");apiClient.useAdminCsrf(true);
    return apiClient.request(`/api/admin/transactions/${id}/approve`,{method:"POST",csrf:true,idempotencyKey});
  },
  decline(id:number,idempotencyKey:string): Promise<PaymentTransaction> {
    resourceId(id);requireText(idempotencyKey,"Idempotency key");apiClient.useAdminCsrf(true);
    return apiClient.request(`/api/admin/transactions/${id}/decline`,{method:"POST",csrf:true,idempotencyKey});
  }
};
