import { apiClient } from "./apiClient";

export interface CustomerIdentity { name: string; email: string; }

export const profileService = {
  async getCurrent(): Promise<CustomerIdentity> {
    const profile = await apiClient.request<CustomerIdentity>("/api/users/current");
    return { name: profile.name, email: profile.email };
  }
};
