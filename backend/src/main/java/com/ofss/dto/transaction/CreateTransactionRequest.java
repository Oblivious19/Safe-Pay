package com.ofss.dto.transaction;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ofss.beans.PaymentCategory;
import com.ofss.common.MoneyUtility;
import jakarta.validation.constraints.AssertTrue;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransactionRequest(

        @NotNull(message = "sourceAccountId is required")
        @Positive(message = "sourceAccountId must be positive")
        Long sourceAccountId,

        @NotNull(message = "beneficiaryId is required")
        @Positive(message = "beneficiaryId must be positive")
        Long beneficiaryId,

        @NotNull(message = "amount is required")
        @DecimalMin(
                value = "1.00",
                message = "amount must be at least 1.00")
        @Digits(
                integer = 16,
                fraction = 2,
                message = "amount must fit NUMBER(18,2)")
        BigDecimal amount,

        @Size(
                max = 280,
                message = "purpose must not exceed 280 characters")
        String purpose,

        @Size(
                max = 100,
                message = "customerReference must not exceed 100 characters")
        String customerReference,

        PaymentCategory category) {

    public CreateTransactionRequest(Long sourceAccountId, Long beneficiaryId, BigDecimal amount,
            String purpose, String customerReference) {
        this(sourceAccountId, beneficiaryId, amount, purpose, customerReference, null);
    }

    public CreateTransactionRequest {
        purpose = normalizeOptionalText(purpose);
        customerReference = normalizeOptionalText(
                customerReference);
    }

    public void validateCategory() {
        PaymentCategory.requireForNewPayment(amount, category, purpose);
    }

    @JsonIgnore
    @AssertTrue(message = "Above INR 100000.00 category is required; OTHERS needs a 1-140 character purpose; lower payments must omit category")
    public boolean isCategoryValid() {
        // Field constraints report invalid amounts independently.
        if (amount == null) {
            return true;
        }
        try {
            MoneyUtility.requireValidTransactionAmount(amount);
        } catch (IllegalArgumentException exception) {
            return true;
        }
        try {
            validateCategory();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
