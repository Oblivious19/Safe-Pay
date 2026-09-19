package com.ofss.dto.beneficiary;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ofss.beans.BeneficiaryPaymentMethod;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBeneficiaryRequest(

        @NotBlank(message = "beneficiaryName is required")
        @Size(
                min = 2,
                max = 120,
                message = "beneficiaryName must contain between 2 and 120 characters")
        String beneficiaryName,

        @Size(
                max = 60,
                message = "nickname must not exceed 60 characters")
        String nickname,

        @NotNull(message = "paymentMethod is required")
        BeneficiaryPaymentMethod paymentMethod,

        @Size(
                max = 120,
                message = "bankName must not exceed 120 characters")
        String bankName,

        @Size(
                max = 34,
                message = "bankAccountNumber must not exceed 34 characters")
        String bankAccountNumber,

        @Pattern(
                regexp = "^[A-Z]{4}0[A-Z0-9]{6}$",
                message = "ifscCode has an invalid format")
        String ifscCode,

        @Size(
                max = 255,
                message = "upiId must not exceed 255 characters")
        String upiId,

        @Size(
                max = 50,
                message = "relationshipLabel must not exceed 50 characters")
        String relationshipLabel,

        @Size(
                max = 140,
                message = "purposeNote must not exceed 140 characters")
        String purposeNote) {

    public CreateBeneficiaryRequest {
        beneficiaryName = normalizeRequiredText(
                beneficiaryName);

        nickname = normalizeOptionalText(nickname);
        bankName = normalizeOptionalText(bankName);
        bankAccountNumber = normalizeOptionalText(
                bankAccountNumber);

        ifscCode = normalizeIfsc(ifscCode);
        upiId = normalizeUpiId(upiId);

        relationshipLabel = normalizeOptionalText(
                relationshipLabel);

        purposeNote = normalizeOptionalText(purposeNote);
    }

    @JsonIgnore
    @AssertTrue(
            message = "payment details must match paymentMethod")
    public boolean isPaymentDetailsValid() {
        if (paymentMethod == null) {
            return true;
        }

        return switch (paymentMethod) {
            case BANK_ACCOUNT ->
                    bankName != null
                            && bankAccountNumber != null
                            && ifscCode != null
                            && upiId == null;

            case UPI ->
                    upiId != null
                            && bankName == null
                            && bankAccountNumber == null
                            && ifscCode == null;
        };
    }

    private static String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }

    private static String normalizeIfsc(String value) {
        String normalizedValue = normalizeOptionalText(value);

        return normalizedValue == null
                ? null
                : normalizedValue.toUpperCase(Locale.ROOT);
    }

    private static String normalizeUpiId(String value) {
        String normalizedValue = normalizeOptionalText(value);

        return normalizedValue == null
                ? null
                : normalizedValue.toLowerCase(Locale.ROOT);
    }
}
