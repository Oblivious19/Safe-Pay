package com.ofss.dto.riskreview;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ofss.beans.PaymentCategory;
import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskReview;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.common.SensitiveDataMasker;

public record RiskReviewSummaryResponse(
        String reviewId,
        int reviewRound,
        RiskReviewStatus status,
        String transactionId,
        String transactionReference,
        TransactionState transactionState,
        String customerId,
        String customerName,
        String maskedCustomerEmail,
        String maskedSourceAccountNumber,
        String beneficiaryId,
        String beneficiaryName,
        BigDecimal amount,
        CurrencyCode currencyCode,
        RiskTier riskTier,
        String policyVersion,
        String riskExplanation,
        OffsetDateTime verificationCompletedAt,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) PaymentCategory category,
        String purpose) {

    // Retain Java/cached-response compatibility; historical category may be null.
    public RiskReviewSummaryResponse(
        String reviewId,
        int reviewRound,
        RiskReviewStatus status,
        String transactionId,
        String transactionReference,
        TransactionState transactionState,
        String customerId,
        String customerName,
        String maskedCustomerEmail,
        String maskedSourceAccountNumber,
        String beneficiaryId,
        String beneficiaryName,
        BigDecimal amount,
        CurrencyCode currencyCode,
        RiskTier riskTier,
        String policyVersion,
        String riskExplanation,
        OffsetDateTime verificationCompletedAt,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt) {
        this(reviewId, reviewRound, status, transactionId, transactionReference, transactionState, customerId, customerName, maskedCustomerEmail, maskedSourceAccountNumber, beneficiaryId, beneficiaryName, amount, currencyCode, riskTier, policyVersion, riskExplanation, verificationCompletedAt, requestedAt, updatedAt, null, null);
    }

    @JsonAnyGetter
    public java.util.Map<String, PaymentCategory> categoryProperties() {
        return PaymentCategory.appliesTo(amount)
                ? java.util.Collections.singletonMap("category", category) : java.util.Map.of();
    }

    public static RiskReviewSummaryResponse from(
            RiskReview review) {

        Objects.requireNonNull(review, "review is required");
        requirePositiveId(review.getApprovalId(), "review");

        TransactionDb transaction = Objects.requireNonNull(
                review.getTransaction(),
                "review transaction is required");
        requirePositiveId(
                transaction.getTransactionId(),
                "transaction");

        var customer = Objects.requireNonNull(
                review.getCustomer(),
                "review customer is required");
        requirePositiveId(customer.getUserId(), "customer");

        var sourceAccount = Objects.requireNonNull(
                transaction.getSourceAccount(),
                "transaction sourceAccount is required");
        requirePositiveId(sourceAccount.getAccountId(), "sourceAccount");

        var beneficiary = Objects.requireNonNull(
                transaction.getBeneficiary(),
                "transaction beneficiary is required");
        requirePositiveId(beneficiary.getBeneficiaryId(), "beneficiary");

        return new RiskReviewSummaryResponse(
                review.getApprovalId().toString(),
                review.getReviewRound(),
                review.getStatus(),
                transaction.getTransactionId().toString(),
                transaction.getTransactionReference(),
                transaction.getState(),
                customer.getUserId().toString(),
                customer.getFullName(),
                SensitiveDataMasker.maskEmail(customer.getEmail()),
                SensitiveDataMasker.maskAccountNumber(
                        sourceAccount.getAccountNumber()),
                beneficiary.getBeneficiaryId().toString(),
                beneficiary.getBeneficiaryName(),
                transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getRiskTier(),
                transaction.getPolicyVersion(),
                transaction.getRiskExplanation(),
                transaction.getVerificationCompletedAt(),
                review.getRequestedAt(),
                review.getUpdatedAt(),
                transaction.getCategory(),
                transaction.getPurpose());
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
