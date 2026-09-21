package com.ofss.dto.transaction;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ofss.beans.PaymentCategory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.Beneficiary;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.common.SensitiveDataMasker;

public record TransactionResponse(
        String transactionId,
        String transactionReference,
        String sourceAccountId,
        String maskedSourceAccountNumber,
        String beneficiaryId,
        String beneficiaryName,
        String maskedDestinationIdentifier,
        BigDecimal amount,
        CurrencyCode currencyCode,
        String purpose,
        String customerReference,
        TransactionState state,
        String terminalReasonCode,
        BigDecimal reservedAmount,
        RiskTier riskTier,
        String policyVersion,
        Long protectionSeconds,
        String riskExplanation,
        OffsetDateTime createdAt,
        OffsetDateTime authorizedAt,
        OffsetDateTime riskAssessedAt,
        OffsetDateTime protectedUntil,
        OffsetDateTime verificationCompletedAt,
        OffsetDateTime releasedAt,
        OffsetDateTime settledAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime failedAt,
        OffsetDateTime updatedAt,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) PaymentCategory category,
        @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime serverTime,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long protectionRemainingMillis,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canCancel) {

    // Mutation and cached-response callers retain their original constructor/wire shape.
    public TransactionResponse(
        String transactionId, String transactionReference, String sourceAccountId,
        String maskedSourceAccountNumber, String beneficiaryId, String beneficiaryName,
        String maskedDestinationIdentifier, BigDecimal amount, CurrencyCode currencyCode,
        String purpose, String customerReference, TransactionState state, String terminalReasonCode,
        BigDecimal reservedAmount, RiskTier riskTier, String policyVersion, Long protectionSeconds,
        String riskExplanation, OffsetDateTime createdAt, OffsetDateTime authorizedAt,
        OffsetDateTime riskAssessedAt, OffsetDateTime protectedUntil,
        OffsetDateTime verificationCompletedAt, OffsetDateTime releasedAt, OffsetDateTime settledAt,
        OffsetDateTime cancelledAt, OffsetDateTime failedAt, OffsetDateTime updatedAt,
        PaymentCategory category) {
        this(transactionId, transactionReference, sourceAccountId, maskedSourceAccountNumber,
                beneficiaryId, beneficiaryName, maskedDestinationIdentifier, amount, currencyCode,
                purpose, customerReference, state, terminalReasonCode, reservedAmount, riskTier,
                policyVersion, protectionSeconds, riskExplanation, createdAt, authorizedAt,
                riskAssessedAt, protectedUntil, verificationCompletedAt, releasedAt, settledAt,
                cancelledAt, failedAt, updatedAt, category, null, null, null);
    }

    /** Read-time hints only; a subsequent cancellation still rechecks state and database time. */
    public TransactionResponse withObservation(OffsetDateTime observedAt) {
        Objects.requireNonNull(observedAt, "serverTime is required");
        Objects.requireNonNull(state, "transaction state is required");
        boolean protectedState = state == TransactionState.PROTECTED;
        Long remaining = protectedState && protectedUntil != null
                ? Math.max(0L, Duration.between(observedAt, protectedUntil).toMillis()) : null;
        boolean cancellable = state.allowsCustomerCancellation()
                && (!protectedState || (protectedUntil != null && observedAt.isBefore(protectedUntil)));
        return new TransactionResponse(transactionId, transactionReference, sourceAccountId,
                maskedSourceAccountNumber, beneficiaryId, beneficiaryName, maskedDestinationIdentifier,
                amount, currencyCode, purpose, customerReference, state, terminalReasonCode,
                reservedAmount, riskTier, policyVersion, protectionSeconds, riskExplanation, createdAt,
                authorizedAt, riskAssessedAt, protectedUntil, verificationCompletedAt, releasedAt,
                settledAt, cancelledAt, failedAt, updatedAt, category, observedAt, remaining, cancellable);
    }

    // Retain Java/cached-response compatibility; historical category may be null.
    public TransactionResponse(
        String transactionId,
        String transactionReference,
        String sourceAccountId,
        String maskedSourceAccountNumber,
        String beneficiaryId,
        String beneficiaryName,
        String maskedDestinationIdentifier,
        BigDecimal amount,
        CurrencyCode currencyCode,
        String purpose,
        String customerReference,
        TransactionState state,
        String terminalReasonCode,
        BigDecimal reservedAmount,
        RiskTier riskTier,
        String policyVersion,
        Long protectionSeconds,
        String riskExplanation,
        OffsetDateTime createdAt,
        OffsetDateTime authorizedAt,
        OffsetDateTime riskAssessedAt,
        OffsetDateTime protectedUntil,
        OffsetDateTime verificationCompletedAt,
        OffsetDateTime releasedAt,
        OffsetDateTime settledAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime failedAt,
        OffsetDateTime updatedAt) {
        this(transactionId, transactionReference, sourceAccountId, maskedSourceAccountNumber, beneficiaryId, beneficiaryName, maskedDestinationIdentifier, amount, currencyCode, purpose, customerReference, state, terminalReasonCode, reservedAmount, riskTier, policyVersion, protectionSeconds, riskExplanation, createdAt, authorizedAt, riskAssessedAt, protectedUntil, verificationCompletedAt, releasedAt, settledAt, cancelledAt, failedAt, updatedAt, null);
    }

    @JsonAnyGetter
    public java.util.Map<String, PaymentCategory> categoryProperties() {
        return PaymentCategory.appliesTo(amount)
                ? java.util.Collections.singletonMap("category", category) : java.util.Map.of();
    }

    public static TransactionResponse from(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        requirePositiveId(
                transaction.getTransactionId(),
                "transaction");

        var sourceAccount = Objects.requireNonNull(
                transaction.getSourceAccount(),
                "transaction sourceAccount is required");

        requirePositiveId(
                sourceAccount.getAccountId(),
                "sourceAccount");

        Beneficiary beneficiary = Objects.requireNonNull(
                transaction.getBeneficiary(),
                "transaction beneficiary is required");

        requirePositiveId(
                beneficiary.getBeneficiaryId(),
                "beneficiary");

        String maskedDestination = switch (
                beneficiary.getPaymentMethod()) {

            case BANK_ACCOUNT ->
                    SensitiveDataMasker.maskAccountNumber(
                            beneficiary.getBankAccountNumber());

            case UPI -> SensitiveDataMasker.maskUpiId(
                    beneficiary.getUpiId());
        };

        return new TransactionResponse(
                transaction.getTransactionId().toString(),
                transaction.getTransactionReference(),
                sourceAccount.getAccountId().toString(),
                SensitiveDataMasker.maskAccountNumber(
                        sourceAccount.getAccountNumber()),
                beneficiary.getBeneficiaryId().toString(),
                beneficiary.getBeneficiaryName(),
                maskedDestination,
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getPurpose(),
                transaction.getCustomerReference(),
                transaction.getState(),
                transaction.getTerminalReasonCode(),
                transaction.getReservedAmount(),
                transaction.getRiskTier(),
                transaction.getPolicyVersion(),
                transaction.getProtectionSeconds(),
                transaction.getRiskExplanation(),
                transaction.getCreatedAt(),
                transaction.getAuthorizedAt(),
                transaction.getRiskAssessedAt(),
                transaction.getProtectedUntil(),
                transaction.getVerificationCompletedAt(),
                transaction.getReleasedAt(),
                transaction.getSettledAt(),
                transaction.getCancelledAt(),
                transaction.getFailedAt(),
                transaction.getUpdatedAt(),
                transaction.getCategory());
    }

    private static void requirePositiveId(
            Long value,
            String entityName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    entityName + " must already be persisted");
        }
    }
}
