import { apiClient } from "./apiClient";
import { Account } from "./types";
import { resourceId } from "./apiError";
export interface AccountFunds { accountId:number; balance:number; reservedBalance:number; minimumBalance:number; availableToTransfer:number; }
export const accountService = {
  funds(id:number): Promise<AccountFunds> { return apiClient.request(`/api/accounts/${resourceId(id)}/funds`); },
  list(): Promise<Account[]> { return apiClient.request("/api/accounts"); },
  getCurrent(accountId?: number): Promise<Account> {
    return apiClient.request(`/api/accounts/current${accountId === undefined ? "" : "?accountId=" + resourceId(accountId)}`);
  }
};
