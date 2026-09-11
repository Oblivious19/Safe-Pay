package com.ofss.beans;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record InitiateTransactionRequest(
        @NotNull @Positive Long fromAccountId,
        @NotNull @Positive Long beneficiaryId,
        @NotNull @Positive @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @Size(max = 255) String purpose,
        @NotBlank @Email @Size(max = 150) String userEmail) {
}
