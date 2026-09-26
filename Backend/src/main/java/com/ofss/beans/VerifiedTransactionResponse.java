package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** No credential, idempotency key or entity relationship is returned to the browser. */
public record VerifiedTransactionResponse(Long transactionId, String transactionRef, BigDecimal amount,
        String purpose, Long fromAccountId, Long beneficiaryId, String beneficiaryName,
        String beneficiaryBankAccountNumber, String beneficiaryIfsc, String state, String riskTier,
        String riskReason, int protectionSeconds, LocalDateTime protectionExpiresAt,
        LocalDateTime createdAt, LocalDateTime settledAt, LocalDateTime cancelledAt, LocalDateTime verifiedAt,
        Long protectionRemainingMillis, boolean canCancel, PaymentCategory category) {
    public static VerifiedTransactionResponse from(TransactionDb transaction) {
        var beneficiary = transaction.getBeneficiary();
        return new VerifiedTransactionResponse(transaction.getTransactionId(), transaction.getTransactionRef(),
                transaction.getAmount(), transaction.getPurpose(), transaction.getFromAccount().getAccountId(),
                beneficiary.getBeneficiaryId(), beneficiary.getBeneficiaryName(), beneficiary.getBankAccountNumber(),
                beneficiary.getIfsc(), transaction.getState().name(), transaction.getRiskTier().name(),
                transaction.getRiskReason(), transaction.getProtectionSeconds(), transaction.getProtectionExpiresAt(),
                transaction.getCreatedAt(), transaction.getSettledAt(), transaction.getCancelledAt(), transaction.getVerifiedAt(),
                null, false, transaction.getCategory());
    }
}
