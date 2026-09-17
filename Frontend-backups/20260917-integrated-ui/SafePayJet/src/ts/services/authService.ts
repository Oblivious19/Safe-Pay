import { apiClient } from "./apiClient";
import { ApiError, requireText } from "./apiError";
import { LoginRequest, PhonePasswordRequest, LoginResponse, RegistrationRequest, RegistrationResponse, MessageResponse } from "./types";

export const authService = {
  async register(input: RegistrationRequest): Promise<RegistrationResponse> {
    requireText(input.name, "Name"); requireText(input.email, "Email");
    requireText(input.phone, "Phone"); requireText(input.password, "Password");
    const { name, email, phone, password } = input;
    return apiClient.request("/api/auth/register", { method: "POST", body: { name, email, phone, password } });
  },
  async login(input: LoginRequest | PhonePasswordRequest): Promise<LoginResponse> {
    requireText("phone" in input ? input.phone : input.email, "phone" in input ? "Mobile number" : "Email");
    requireText(input.password, "Password");
    apiClient.clearSession();
    const result = await apiClient.request<LoginResponse>("/api/auth/login", {
      method: "POST", body: "phone" in input ? { phone: input.phone, password: input.password } : { email: input.email, password: input.password }
    });
    apiClient.useAdminCsrf(result.role === "ADMIN");
    return result;
  },
  async logout(): Promise<MessageResponse> {
    // A restored browser session may not yet have a token in memory.
    let csrf = true;
    try { await apiClient.ensureCsrfToken(); }
    catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) throw error;
      csrf = false; // Anonymous logout is explicitly exempt in the server configuration.
    }
    const result = await apiClient.request<MessageResponse>("/api/auth/logout", { method: "POST", csrf });
    apiClient.clearSession();
    return result;
  }
};
