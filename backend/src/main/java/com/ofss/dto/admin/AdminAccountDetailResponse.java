package com.ofss.dto.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

import com.ofss.beans.Account;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;

public record AdminAccountDetailResponse(
        String accountId,
        String ownerId,
        String ownerName,
        String accountNumber,
        AccountType accountType,
        String bankName,
        String ifscCode,
        CurrencyCode currency,
        String currentBalance,
        String reservedAmount,
        String availableBalance,
        AccountStatus status,
        long versionNo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdminAccountDetailResponse from(Account account) {
        return new AdminAccountDetailResponse(
                account.getAccountId().toString(),
                account.getOwner().getUserId().toString(),
                account.getOwner().getFullName(), account.getAccountNumber(),
                account.getAccountType(), account.getBankName(), account.getIfscCode(),
                account.getCurrencyCode(), money(account.getCurrentBalance()),
                money(account.getReservedAmount()), money(account.getAvailableBalance()),
                account.getStatus(), account.getVersionNo(),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
