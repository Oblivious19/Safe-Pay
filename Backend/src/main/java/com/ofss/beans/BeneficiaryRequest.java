package com.ofss.beans;

import java.util.Locale;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BeneficiaryRequest(
        @NotBlank @Size(max = 100) String beneficiaryName,
        @NotBlank @Pattern(regexp = "[0-9]{1,30}") String bankAccountNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}") String ifsc,
        boolean externalConfirmed,
        @Positive Long accountId) {
    public BeneficiaryRequest {
        beneficiaryName = beneficiaryName == null ? null : beneficiaryName.strip();
        bankAccountNumber = bankAccountNumber == null ? null : bankAccountNumber.strip();
        ifsc = ifsc == null ? null : ifsc.strip().toUpperCase(Locale.ROOT);
    }

    @JsonAnySetter
    public void rejectExtraField(String name, Object value) {
        throw new IllegalArgumentException("Only beneficiaryName, bankAccountNumber, ifsc, externalConfirmed and accountId may be supplied");
    }
}
