package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

public final class AdminCreditDtos {
    private AdminCreditDtos() {}
    public record Request(@NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount) {
        @JsonAnySetter public void rejectExtra(String key, Object value) {
            throw new IllegalArgumentException("Only amount may be supplied");
        }
    }
    /** Decimal strings preserve the full NUMBER(18,2) range in browsers. */
    public record AccountView(Long accountId, String accountNumber, String accountType, String status, String balance,
            Long userId, LocalDateTime createdAt) {}
    public record Receipt(Long accountId, String amount, String balanceBefore, String balanceAfter,
            LocalDateTime createdAt, String description) {}
}
