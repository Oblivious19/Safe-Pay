import { apiClient } from "./apiClient";
import { ApiError } from "./apiError";
import { adminService } from "./adminService";
import { profileService, CustomerIdentity } from "./profileService";
import { UserRole } from "./types";

export interface VerifiedSession { role: UserRole; profile: CustomerIdentity | null; }
export const sessionService = {
  async restore(preferAdmin = false): Promise<VerifiedSession> {
    const customer = async (): Promise<VerifiedSession> => {
      const profile = await profileService.getCurrent();
      apiClient.useAdminCsrf(false);
      return { role: "CUSTOMER", profile };
    };
    const admin = async (): Promise<VerifiedSession> => {
      await adminService.users();
      apiClient.useAdminCsrf(true);
      return { role: "ADMIN", profile: null };
    };
    try { return await (preferAdmin ? admin() : customer()); }
    catch (error) {
      // Only a role-denied response permits the other protected probe. Network errors or
      // expired sessions never establish a role, and no browser-stored identity is trusted.
      if (!(error instanceof ApiError) || error.status !== 403) throw error;
      return preferAdmin ? customer() : admin();
    }
  }
};
