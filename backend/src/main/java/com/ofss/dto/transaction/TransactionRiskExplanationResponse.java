package com.ofss.dto.transaction;

import java.time.OffsetDateTime;
import java.util.Objects;

import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionRiskFactor;
import com.ofss.beans.TransactionRiskFactorCode;
import com.ofss.beans.TransactionState;

public record TransactionRiskExplanationResponse(
        String transactionId,
        String transactionReference,
        TransactionState state,
        RiskTier riskTier,
        String policyVersion,
        Long protectionSeconds,
        String explanation,
        OffsetDateTime riskAssessedAt,
        OffsetDateTime protectedUntil) {

    public static TransactionRiskExplanationResponse from(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }

        if (transaction.getRiskTier() == null
                || transaction.getPolicyVersion() == null
                || transaction.getRiskExplanation() == null
                || transaction.getRiskExplanation().isBlank()
                || transaction.getRiskAssessedAt() == null) {

            throw new IllegalStateException(
                    "transaction has not been risk assessed");
        }

        return new TransactionRiskExplanationResponse(
                transaction.getTransactionId().toString(),
                transaction.getTransactionReference(),
                transaction.getState(),
                transaction.getRiskTier(),
                transaction.getPolicyVersion(),
                transaction.getProtectionSeconds(),
                transaction.getRiskExplanation(),
                transaction.getRiskAssessedAt(),
                transaction.getProtectedUntil());
    }

    public static TransactionRiskExplanationResponse from(
            TransactionDb transaction,
            TransactionRiskFactor evidence) {

        Objects.requireNonNull(evidence, "evidence is required");

        TransactionRiskExplanationResponse response = from(transaction);

        if (evidence.getTransaction() == null
                || !Objects.equals(
                        evidence.getTransaction().getTransactionId(),
                        transaction.getTransactionId())
                || evidence.getFactorCode()
                        != TransactionRiskFactorCode.PAYMENT_AMOUNT
                || evidence.getRiskPolicyBand() == null
                || transaction.getRiskPolicyBand() == null
                || !Objects.equals(
                        evidence.getRiskPolicyBand()
                                .getRiskPolicyBandId(),
                        transaction.getRiskPolicyBand()
                                .getRiskPolicyBandId())
                || evidence.getResultingTier()
                        != transaction.getRiskTier()
                || !Objects.equals(
                        evidence.getExplanation(),
                        transaction.getRiskExplanation())
                || !Objects.equals(
                        evidence.getEvaluatedAt(),
                        transaction.getRiskAssessedAt())) {

            throw new IllegalStateException(
                    "Persisted risk evidence does not match the transaction snapshot");
        }

        return response;
    }
}
