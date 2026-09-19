package com.ofss.dto.admin;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.common.SensitiveDataMasker;

public record AdminAccountSummaryResponse(
        String accountId,
        String ownerId,
        String ownerName,
        String maskedAccountNumber,
        AccountType accountType,
        String bankName,
        String ifscCode,
        AccountStatus status) {

    public static AdminAccountSummaryResponse from(Account account) {
        return new AdminAccountSummaryResponse(
                account.getAccountId().toString(),
                account.getOwner().getUserId().toString(),
                account.getOwner().getFullName(),
                SensitiveDataMasker.maskAccountNumber(account.getAccountNumber()),
                account.getAccountType(), account.getBankName(),
                account.getIfscCode(), account.getStatus());
    }
}
