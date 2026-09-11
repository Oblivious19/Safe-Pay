package com.ofss.beans;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(
        @NotNull @Positive Long accountId,
        @NotBlank @Email @Size(max = 150) String userEmail,
        @NotBlank @Size(max = 100) String beneficiaryName,
        @NotBlank @Pattern(regexp = "[0-9]{9,30}", message = "must contain 9 to 30 digits")
        String bankAccountNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}", message = "must be a valid 11-character IFSC")
        String ifsc) {
}
