import { apiClient } from "./apiClient";
export interface CustomerIdentity { userId?: number; name: string; email: string; phone?: string; status?: string; }
export const profileService = {
  async getCurrent(): Promise<CustomerIdentity> {
    const {userId,name,email,phone,status}=await apiClient.request<CustomerIdentity>("/api/users/current");
    return {userId,name,email,phone,status};
  },
  update(input: {name: string; email: string; phone: string}): Promise<CustomerIdentity> {
    const {name,email,phone}=input;
    return apiClient.request("/api/users/current", {method:"PUT",csrf:true,body:{name,email,phone}});
  }
};
