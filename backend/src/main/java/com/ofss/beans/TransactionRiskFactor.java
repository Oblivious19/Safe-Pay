package com.ofss.beans;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.hibernate.annotations.Immutable;

import com.ofss.common.MoneyUtility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(
        name = "TRANSACTION_RISK_FACTOR",
        schema = "SAFEPAY_OWNER")
@SequenceGenerator(
        name = "transactionRiskFactorSequence",
        sequenceName = "SAFEPAY_OWNER.SEQ_TX_RISK_FACTOR_ID",
        allocationSize = 1)
public class TransactionRiskFactor {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "transactionRiskFactorSequence")
    @Column(
            name = "TRANSACTION_RISK_FACTOR_ID",
            nullable = false,
            updatable = false)
    private Long transactionRiskFactorId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "TRANSACTION_ID",
            nullable = false,
            updatable = false)
    private TransactionDb transaction;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(
            name = "RISK_POLICY_BAND_ID",
            nullable = false,
            updatable = false)
    private RiskPolicyBand riskPolicyBand;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "FACTOR_CODE",
            nullable = false,
            updatable = false,
            length = 50)
    private TransactionRiskFactorCode factorCode;

    @Column(
            name = "RAW_VALUE",
            nullable = false,
            updatable = false,
            length = 500)
    private String rawValue;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "RESULTING_TIER",
            nullable = false,
            updatable = false,
            length = 20)
    private RiskTier resultingTier;

    @Column(
            name = "EXPLANATION",
            nullable = false,
            updatable = false,
            length = 1000)
    private String explanation;

    @Column(
            name = "EVALUATED_AT",
            nullable = false,
            updatable = false)
    private OffsetDateTime evaluatedAt;

    protected TransactionRiskFactor() {
        // Required by JPA.
    }

    public static TransactionRiskFactor createPaymentAmountEvidence(
            TransactionDb transaction,
            RiskPolicyBand matchedBand) {

        requirePersistedTransaction(transaction);
        requirePersistedBand(matchedBand);

        RiskTier resultingTier = Objects.requireNonNull(
                transaction.getRiskTier(),
                "transaction riskTier is required");

        if (matchedBand.getRiskTier() != resultingTier) {
            throw new IllegalArgumentException(
                    "transaction riskTier must match riskPolicyBand");
        }

        if (transaction.getRiskPolicyBand() == null
                || !Objects.equals(
                        transaction.getRiskPolicyBand()
                                .getRiskPolicyBandId(),
                        matchedBand.getRiskPolicyBandId())) {

            throw new IllegalArgumentException(
                    "riskPolicyBand must match transaction snapshot");
        }

        TransactionRiskFactor factor =
                new TransactionRiskFactor();

        BigDecimal assessedAmount =
                MoneyUtility.requireValidTransactionAmount(
                        transaction.getAmount());

        factor.transaction = transaction;
        factor.riskPolicyBand = matchedBand;
        factor.factorCode =
                TransactionRiskFactorCode.PAYMENT_AMOUNT;
        factor.rawValue = assessedAmount.toPlainString();
        factor.resultingTier = resultingTier;
        factor.explanation = requireText(
                transaction.getRiskExplanation(),
                "explanation",
                1000);
        factor.evaluatedAt = requireUtcTimestamp(
                transaction.getRiskAssessedAt(),
                "evaluatedAt");

        return factor;
    }

    private static void requirePersistedTransaction(
            TransactionDb transaction) {

        Objects.requireNonNull(
                transaction,
                "transaction is required");

        if (transaction.getTransactionId() == null
                || transaction.getTransactionId() <= 0L) {
            throw new IllegalArgumentException(
                    "transaction must already be persisted");
        }
    }

    private static void requirePersistedBand(
            RiskPolicyBand matchedBand) {

        Objects.requireNonNull(
                matchedBand,
                "matchedBand is required");

        if (matchedBand.getRiskPolicyBandId() == null
                || matchedBand.getRiskPolicyBandId() <= 0L) {
            throw new IllegalArgumentException(
                    "matchedBand must already be persisted");
        }
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }

        return normalized;
    }

    private static OffsetDateTime requireUtcTimestamp(
            OffsetDateTime value,
            String fieldName) {

        return Objects.requireNonNull(
                        value,
                        fieldName + " is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Long getTransactionRiskFactorId() {
        return transactionRiskFactorId;
    }

    public TransactionDb getTransaction() {
        return transaction;
    }

    public RiskPolicyBand getRiskPolicyBand() {
        return riskPolicyBand;
    }

    public TransactionRiskFactorCode getFactorCode() {
        return factorCode;
    }

    public String getRawValue() {
        return rawValue;
    }

    public RiskTier getResultingTier() {
        return resultingTier;
    }

    public String getExplanation() {
        return explanation;
    }

    public OffsetDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
