package com.ofss.beans;

import java.time.LocalDateTime;

/** Review data only: never expose a user entity, credential, or operation key. */
public record AdminApprovalRequestView(Long transactionId, String transactionRef, String amount,
        Long userId, String customerName, Long fromAccountId, String sourceAccountNumber,
        String beneficiaryName, String beneficiaryBankAccountNumber, String beneficiaryIfsc,
        String purpose, String riskReason, LocalDateTime createdAt) {
    public static AdminApprovalRequestView from(TransactionDb t) {
        return new AdminApprovalRequestView(t.getTransactionId(), t.getTransactionRef(),
                t.getAmount().toPlainString(), t.getFromAccount().getUserId(), t.getFromAccount().getUser().getName(),
                t.getFromAccount().getAccountId(), t.getFromAccount().getAccountNumber(),
                t.getBeneficiary().getBeneficiaryName(), t.getBeneficiary().getBankAccountNumber(),
                t.getBeneficiary().getIfsc(), t.getPurpose(), t.getRiskReason(), t.getCreatedAt());
    }
}
