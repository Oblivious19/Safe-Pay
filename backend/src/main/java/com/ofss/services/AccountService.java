package com.ofss.services;

import java.util.List;

import com.ofss.beans.Account;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.account.AccountSummaryResponse;

public interface AccountService {

    /*
     * AccountController obtains ownerUserId from the authenticated
     * SafePay principal; internal payment callers preserve that identity.
     * Controllers must never accept it from request JSON.
     */
    List<AccountSummaryResponse> listOwnedAccounts(
            Long ownerUserId);

    AccountSummaryResponse getOwnedAccount(
            Long ownerUserId,
            Long accountId);

    AccountBalanceResponse getOwnedBalance(
            Long ownerUserId,
            Long accountId);

    Account getRequiredActiveOwnedAccount(
            Long ownerUserId,
            Long accountId);
}
