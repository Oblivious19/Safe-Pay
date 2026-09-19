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
        this(transactionId, transactionReference, beneficiaryId, beneficiaryName, maskedDestinationIdentifier, amount, currencyCode, state, riskTier, protectedUntil, createdAt, updatedAt, null);
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
                detail.category());
    }
}
