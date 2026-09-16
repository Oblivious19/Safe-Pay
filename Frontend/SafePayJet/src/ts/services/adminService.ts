import { apiClient } from "./apiClient";
import { validCredit } from "./adminCreditDraft";
import { ApiError, resourceId } from "./apiError";
import { AccountStatus, AccountType, UserRole, UserStatus } from "./types";

export interface AdminUser {
  userId: number; name: string; email: string; phone: string; role: UserRole;
  status: UserStatus; createdAt: string; updatedAt: string;
}
export interface AdminAccount {
  accountId: number; userId: number; accountNumber: string; accountType: AccountType;
  balance: number | string; status: AccountStatus; updatedAt: string;
}
export interface UserAccounts { accountId: number; accountNumber: string; accountType: AccountType; status: AccountStatus; balance: string; }
export interface CreditReceipt { accountId: number; amount: string; balanceBefore: string; balanceAfter: string; createdAt: string; description: string; }
export interface ProvisionUser { name: string; email: string; phone: string; initialPassword: string; }
export const adminService = {
  user(id: number): Promise<AdminUser> { return apiClient.request("/api/admin/users/" + resourceId(id)); },
  userAccounts(id: number): Promise<UserAccounts[]> { return apiClient.request("/api/admin/users/" + resourceId(id) + "/accounts"); },
  createUser(input: ProvisionUser): Promise<AdminUser> {
    const { name, email, phone, initialPassword } = input;
    if (!name.trim() || new TextEncoder().encode(name.trim()).length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
      || new TextEncoder().encode(email.trim()).length > 150 || !/^[6-9][0-9]{9}$/.test(phone)
      || initialPassword.length < 8 || new TextEncoder().encode(initialPassword).length > 72) {
      throw new ApiError(400, "Enter a valid name, email, mobile number and an initial password of 8 characters through 72 UTF-8 bytes.", "validation");
    }
    return apiClient.request("/api/admin/users", { method: "POST", csrf: true, body: { name: name.trim(), email: email.trim(), phone, initialPassword } });
  },
  credit(id: number, amount: string, key: string): Promise<CreditReceipt> {
    if (!validCredit(amount) || !/^[A-Za-z0-9-]{1,100}$/.test(key)) throw new ApiError(400, "Enter a positive credit amount and a valid retry key.", "validation");
    return apiClient.request("/api/admin/accounts/" + resourceId(id) + "/interest-credits", { method: "POST", csrf: true, idempotencyKey: key, body: { amount } });
  },
  users(): Promise<AdminUser[]> { return apiClient.request("/api/admin/users"); },
  accounts(): Promise<AdminAccount[]> { return apiClient.request("/api/admin/accounts"); },
  setUserStatus(id: number, status: UserStatus): Promise<AdminUser> {
    if (!["ACTIVE", "LOCKED", "SUSPENDED", "INACTIVE"].includes(status)) throw new ApiError(400, "Choose a valid user status.", "validation");
    return apiClient.request(`/api/admin/users/${resourceId(id)}/status`, { method: "PATCH", csrf: true, body: { status } });
  },
  setAccountStatus(id: number, status: "ACTIVE" | "BLOCKED"): Promise<Omit<AdminAccount, "balance">> {
    if (!["ACTIVE", "BLOCKED"].includes(status)) throw new ApiError(400, "Choose ACTIVE or BLOCKED.", "validation");
    return apiClient.request(`/api/admin/accounts/${resourceId(id)}/status`, { method: "PATCH", csrf: true, body: { status } });
  },
  updateAccount(id: number, balance: string, accountType: AccountType): Promise<AdminAccount> {
    if (!/^[0-9]{1,16}(\.[0-9]{1,2})?$/.test(balance) || !["SAVINGS", "CURRENT"].includes(accountType)) {
      throw new ApiError(400, "Enter a valid balance with up to two decimal places and an account type.", "validation");
    }
    // Send exact decimal text to BigDecimal; the server checks the minimum plus reserved payments.
    return apiClient.request(`/api/admin/accounts/${resourceId(id)}`, { method: "PUT", csrf: true, body: { balance, accountType } });
  }
};
