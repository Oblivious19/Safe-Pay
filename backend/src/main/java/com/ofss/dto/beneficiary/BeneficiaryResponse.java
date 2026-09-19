package com.ofss.dto.beneficiary;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.BeneficiaryStatus;
import com.ofss.common.SensitiveDataMasker;

public record BeneficiaryResponse(
        String beneficiaryId,
        String beneficiaryName,
        String nickname,
        BeneficiaryPaymentMethod paymentMethod,
        String bankName,
        String maskedDestinationIdentifier,
        String ifscCode,
        String relationshipLabel,
        String purposeNote,
        BeneficiaryStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static BeneficiaryResponse from(
            Beneficiary beneficiary) {

        Objects.requireNonNull(
                beneficiary,
                "beneficiary is required");

        Long beneficiaryId = beneficiary.getBeneficiaryId();

        if (beneficiaryId == null || beneficiaryId <= 0L) {
            throw new IllegalArgumentException(
                    "beneficiary must already be persisted");
        }

        String maskedIdentifier = switch (
                beneficiary.getPaymentMethod()) {

            case BANK_ACCOUNT ->
                    SensitiveDataMasker.maskAccountNumber(
                            beneficiary.getBankAccountNumber());

            case UPI -> SensitiveDataMasker.maskUpiId(
                    beneficiary.getUpiId());
        };

        return new BeneficiaryResponse(
                beneficiaryId.toString(),
                beneficiary.getBeneficiaryName(),
                beneficiary.getNickname(),
                beneficiary.getPaymentMethod(),
                beneficiary.getBankName(),
                maskedIdentifier,
                beneficiary.getIfscCode(),
                beneficiary.getRelationshipLabel(),
                beneficiary.getPurposeNote(),
                beneficiary.getStatus(),
                beneficiary.getCreatedAt(),
                beneficiary.getUpdatedAt());
    }
}
