import { apiClient } from "./apiClient";
import { resourceId } from "./apiError";

export interface AdminUser { userId: number; name: string; email: string; phone: string; role: string; status: string; }
export interface AdminAccount { accountId: number; userId: number; accountNumber: string; accountType: string; status: string; balance: string; createdAt: string; }

export const adminUserService = {
  users(): Promise<AdminUser[]> {
    apiClient.useAdminCsrf(true);
    return apiClient.request("/api/admin/users");
  },
  searchUsers(by: string, query: string): Promise<AdminUser[]> {
    apiClient.useAdminCsrf(true);
    const value = by === "email" ? query.trim().toLowerCase() : query.trim();
    return apiClient.request("/api/admin/users/search?" + new URLSearchParams({by, query: value}).toString());
  },
  accounts(id: number): Promise<AdminAccount[]> {
    apiClient.useAdminCsrf(true);
    return apiClient.request(`/api/admin/users/${resourceId(id)}/accounts`);
  }
};
