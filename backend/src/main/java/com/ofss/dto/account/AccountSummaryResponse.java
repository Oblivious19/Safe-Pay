package com.ofss.dto.account;

import java.util.Objects;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.common.SensitiveDataMasker;

public record AccountSummaryResponse(
        String accountId,
        String maskedAccountNumber,
        AccountType accountType,
        String bankName,
        String ifscCode,
        CurrencyCode currency,
        AccountStatus status) {

    public static AccountSummaryResponse from(
            Account account) {

        Objects.requireNonNull(
                account,
                "account is required");

        if (account.getAccountId() == null) {
            throw new IllegalArgumentException(
                    "account must already be persisted");
        }

        return new AccountSummaryResponse(
                account.getAccountId().toString(),
                SensitiveDataMasker.maskAccountNumber(
                        account.getAccountNumber()),
                account.getAccountType(),
                account.getBankName(),
                account.getIfscCode(),
                account.getCurrencyCode(),
                account.getStatus());
    }
}