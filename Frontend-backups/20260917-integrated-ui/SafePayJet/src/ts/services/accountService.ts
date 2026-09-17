import { apiClient } from "./apiClient";
import { Account } from "./types";
import { resourceId } from "./apiError";
export const accountService = {
  list(): Promise<Account[]> { return apiClient.request("/api/accounts"); },
  getCurrent(accountId?: number): Promise<Account> {
    return apiClient.request(`/api/accounts/current${accountId === undefined ? "" : "?accountId=" + resourceId(accountId)}`);
  }
};
