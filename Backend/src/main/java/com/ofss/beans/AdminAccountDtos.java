package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public final class AdminAccountDtos {
    private AdminAccountDtos() {}
    public record Update(@NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal balance,
            @NotNull AccountType accountType) {
        @JsonAnySetter
        public void rejectExtraField(String name, Object value) {
            throw new IllegalArgumentException("Only balance and accountType may be supplied");
        }
    }
    public record Response(Long accountId, Long userId, String accountNumber, AccountType accountType,
            BigDecimal balance, AccountStatus status, LocalDateTime updatedAt) {
        public static Response from(Account account) {
            return new Response(account.getAccountId(), account.getUserId(), account.getAccountNumber(),
                    account.getAccountType(), account.getBalance(), account.getStatus(), account.getUpdatedAt());
        }
    }
}
