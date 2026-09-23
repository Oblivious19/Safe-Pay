package com.ofss.dto.transaction;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ofss.beans.PaymentCategory;
import java.time.OffsetDateTime;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;
import com.ofss.common.SensitiveDataMasker;

public record TransactionSummaryResponse(
        String transactionId,
        String transactionReference,
        String beneficiaryId,
        String beneficiaryName,
        String maskedDestinationIdentifier,
        BigDecimal amount,
        CurrencyCode currencyCode,
        TransactionState state,
        RiskTier riskTier,
        OffsetDateTime protectedUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String direction,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) PaymentCategory category) {

    // Retain Java/cached-response compatibility; historical category may be null.
    public TransactionSummaryResponse(
        String transactionId,
        String transactionReference,
        String beneficiaryId,
        String beneficiaryName,
        String maskedDestinationIdentifier,
        BigDecimal amount,
        CurrencyCode currencyCode,
        TransactionState state,
        RiskTier riskTier,
        OffsetDateTime protectedUntil,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
        this(transactionId, transactionReference, beneficiaryId, beneficiaryName, maskedDestinationIdentifier, amount, currencyCode, state, riskTier, protectedUntil, createdAt, updatedAt, "SENT", null);
    }

    @JsonAnyGetter
    public java.util.Map<String, PaymentCategory> categoryProperties() {
        return PaymentCategory.appliesTo(amount)
                ? java.util.Collections.singletonMap("category", category) : java.util.Map.of();
    }

    public static TransactionSummaryResponse from(
            TransactionDb transaction) {

        TransactionResponse detail =
                TransactionResponse.from(transaction);

        return new TransactionSummaryResponse(
                detail.transactionId(),
                detail.transactionReference(),
                detail.beneficiaryId(),
                detail.beneficiaryName(),
                detail.maskedDestinationIdentifier(),
                detail.amount(),
                detail.currencyCode(),
                detail.state(),
                detail.riskTier(),
                detail.protectedUntil(),
                detail.createdAt(),
                detail.updatedAt(),
                "SENT",
                detail.category());
    }

    public static TransactionSummaryResponse from(TransactionDb transaction, Long viewerUserId) {
        TransactionSummaryResponse summary = from(transaction);
        boolean received = transaction.getDestinationAccount() != null
                && transaction.getDestinationAccount().getOwner() != null
                && java.util.Objects.equals(transaction.getDestinationAccount().getOwner().getUserId(), viewerUserId)
                && !java.util.Objects.equals(transaction.getCustomer().getUserId(), viewerUserId);
        return new TransactionSummaryResponse(summary.transactionId(), summary.transactionReference(),
                summary.beneficiaryId(), received ? "Money received" : summary.beneficiaryName(),
                received ? detailMaskedSource(transaction) : summary.maskedDestinationIdentifier(), summary.amount(),
                summary.currencyCode(), summary.state(), summary.riskTier(), summary.protectedUntil(),
                summary.createdAt(), summary.updatedAt(), received ? "RECEIVED" : "SENT", summary.category());
    }

    private static String detailMaskedSource(TransactionDb transaction) {
        return SensitiveDataMasker.maskAccountNumber(transaction.getSourceAccount().getAccountNumber());
    }
}
