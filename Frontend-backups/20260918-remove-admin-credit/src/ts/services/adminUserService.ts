import { apiClient } from "./apiClient";
import { ApiError, resourceId, requireText } from "./apiError";
export interface AdminUser {userId:number;name:string;email:string;phone:string;role:string;status:string;}
export interface CreditAccount {accountId:number;accountNumber:string;accountType:string;status:string;balance:string;}
export interface CreditReceipt {accountId:number;amount:string;balanceBefore:string;balanceAfter:string;createdAt:string;description:string;}
export function validCredit(amount:string):boolean{return /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,2})?$/.test(amount) && /[1-9]/.test(amount);}
export const adminUserService={
 users:():Promise<AdminUser[]>=>{apiClient.useAdminCsrf(true);return apiClient.request("/api/admin/users");},
 accounts:(id:number):Promise<CreditAccount[]>=>{apiClient.useAdminCsrf(true);return apiClient.request(`/api/admin/users/${resourceId(id)}/accounts`);},
 credit:(id:number,amount:string,idempotencyKey:string):Promise<CreditReceipt>=>{
  resourceId(id);requireText(idempotencyKey,"Idempotency key");
  if(!validCredit(amount))return Promise.reject(new ApiError(400,"Enter a positive amount with at most two decimals."));
  apiClient.useAdminCsrf(true);
  return apiClient.request(`/api/admin/accounts/${id}/interest-credits`,{method:"POST",csrf:true,idempotencyKey,body:{amount}});
 }
};
