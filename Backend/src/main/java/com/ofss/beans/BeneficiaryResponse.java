package com.ofss.beans;

import java.time.LocalDateTime;

public record BeneficiaryResponse(Long beneficiaryId, String beneficiaryName, String bankAccountNumber,
        String ifsc, String bankName, String status, LocalDateTime createdAt, Long accountId) {
    public static BeneficiaryResponse from(Beneficiary beneficiary) {
        return new BeneficiaryResponse(beneficiary.getBeneficiaryId(), beneficiary.getBeneficiaryName(),
                beneficiary.getBankAccountNumber(), beneficiary.getIfsc(), SimulatedBank.fromIfsc(beneficiary.getIfsc()).displayName(), beneficiary.getStatus(), beneficiary.getCreatedAt(),
                beneficiary.getAccount().getAccountId());
    }
}
