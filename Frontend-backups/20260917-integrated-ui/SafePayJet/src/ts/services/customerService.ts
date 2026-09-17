import { apiClient } from "./apiClient";
import { requireText } from "./apiError";
import { CustomerProfile, ProfileUpdate } from "./types";

export const customerService = {
  getCurrent(): Promise<CustomerProfile> { return apiClient.request("/api/users/current"); },
  async updateCurrent(input: ProfileUpdate): Promise<CustomerProfile> {
    requireText(input.name, "Name"); requireText(input.email, "Email"); requireText(input.phone, "Phone");
    const { name, email, phone } = input;
    return apiClient.request("/api/users/current", {
      method: "PUT", csrf: true,
      body: { name, email, phone, ...(input.password ? { password: input.password } : {}) }
    });
  }
};
