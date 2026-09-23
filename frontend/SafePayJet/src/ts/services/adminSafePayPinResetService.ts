import {request} from './apiClient';
export type PinResetRequest={userId:string;fullName:string;email:string|null;mobileNumber:string|null;status:string};
export const adminSafePayPinResets={
  list:()=>request<PinResetRequest[]>('/admin/safe-pay-pin-resets'),
  approve:(userId:string)=>request<PinResetRequest>('/admin/safe-pay-pin-resets/'+encodeURIComponent(userId)+'/approve',{method:'POST'}),
  reject:(userId:string)=>request<PinResetRequest>('/admin/safe-pay-pin-resets/'+encodeURIComponent(userId)+'/reject',{method:'POST'})
};
