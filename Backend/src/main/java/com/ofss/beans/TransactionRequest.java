package com.ofss.beans;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

public record TransactionRequest(
        @NotNull @Positive Long fromAccountId,
        @NotNull @Positive Long beneficiaryId,
        @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 255, message = "Purpose must not exceed 255 characters") String purpose,
        PaymentCategory category) {
    public TransactionRequest(Long fromAccountId, Long beneficiaryId, BigDecimal amount, String purpose) {
        this(fromAccountId, beneficiaryId, amount, purpose, null);
    }
    @JsonAnySetter
    public void rejectExtraField(String name, Object value) {
        throw new IllegalArgumentException("Only fromAccountId, beneficiaryId, amount, purpose and category may be supplied");
    }
}
