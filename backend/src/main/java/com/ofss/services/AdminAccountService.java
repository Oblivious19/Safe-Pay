package com.ofss.services;

import java.math.BigDecimal;

import com.ofss.beans.AccountType;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.admin.AdminAccountDetailResponse;
import com.ofss.dto.admin.AdminAccountSummaryResponse;

public interface AdminAccountService {
    PagedResponse<AdminAccountSummaryResponse> searchAccounts(
            Long administratorId, Long customerId, BigDecimal minCurrentBalance,
            AccountType accountType, int page, int size);

    AdminAccountDetailResponse getAccount(Long administratorId, Long accountId);

    AccountBalanceResponse getBalance(Long administratorId, Long accountId);
}
