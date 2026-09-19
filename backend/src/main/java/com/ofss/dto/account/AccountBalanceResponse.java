package com.ofss.dto.account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.ofss.beans.Account;
import com.ofss.beans.CurrencyCode;
import com.ofss.common.SensitiveDataMasker;

public record AccountBalanceResponse(
        String accountId,
        String maskedAccountNumber,
        CurrencyCode currency,
        String currentBalance,
        String reservedAmount,
        String availableBalance) {

    public static AccountBalanceResponse from(
            Account account) {

        Objects.requireNonNull(
                account,
                "account is required");

        if (account.getAccountId() == null) {
            throw new IllegalArgumentException(
                    "account must already be persisted");
        }

        return new AccountBalanceResponse(
                account.getAccountId().toString(),
                SensitiveDataMasker.maskAccountNumber(
                        account.getAccountNumber()),
                account.getCurrencyCode(),
                formatMoney(account.getCurrentBalance()),
                formatMoney(account.getReservedAmount()),
                formatMoney(account.getAvailableBalance()));
    }

    private static String formatMoney(BigDecimal value) {
        Objects.requireNonNull(
                value,
                "money value is required");

        return value
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();
    }
}