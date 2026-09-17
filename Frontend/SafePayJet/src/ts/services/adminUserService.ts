import { apiClient } from "./apiClient";
import { resourceId } from "./apiError";

export interface AdminUser { userId: number; name: string; email: string; phone: string; role: string; status: string; }
export interface AdminAccount { accountId: number; accountNumber: string; accountType: string; status: string; balance: string; }

export const adminUserService = {
  users(): Promise<AdminUser[]> {
    apiClient.useAdminCsrf(true);
    return apiClient.request("/api/admin/users");
  },
  accounts(id: number): Promise<AdminAccount[]> {
    apiClient.useAdminCsrf(true);
    return apiClient.request(`/api/admin/users/${resourceId(id)}/accounts`);
  }
};
