package com.ofss.dto.risk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;

public record RiskEvaluationResult(
        RiskPolicySnapshot policySnapshot,
        BigDecimal riskScore,
        Long protectionSeconds,
        ProtectionReleaseMode releaseMode,
        boolean customerCanCancel,
        boolean autoRelease,
        boolean otpRequired,
        boolean riskReviewRequired,
        String explanation,
        OffsetDateTime evaluatedAt) {

    public RiskEvaluationResult {
        policySnapshot = Objects.requireNonNull(
                policySnapshot,
                "policySnapshot is required");

        if (riskScore != null) {
            throw new IllegalArgumentException(
                    "V1 riskScore must be null");
        }

        releaseMode = Objects.requireNonNull(
                releaseMode,
                "releaseMode is required");

        explanation = requireExplanation(explanation);

        evaluatedAt = Objects.requireNonNull(
                evaluatedAt,
                "evaluatedAt is required")
                .withOffsetSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        validateProtectionAction(
                protectionSeconds,
                releaseMode,
                customerCanCancel,
                autoRelease,
                otpRequired,
                riskReviewRequired);
    }

    public static RiskEvaluationResult fromMatchedBand(
            RiskPolicyBand matchedBand,
            OffsetDateTime evaluatedAt) {

        Objects.requireNonNull(
                matchedBand,
                "matchedBand is required");

        ProtectionPolicy protection = Objects.requireNonNull(
                matchedBand.getProtectionPolicy(),
                "matchedBand protectionPolicy is required");

        return new RiskEvaluationResult(
                RiskPolicySnapshot.from(matchedBand),
                null,
                protection.getProtectionSeconds(),
                protection.getReleaseMode(),
                protection.canCustomerCancel(),
                protection.isAutoRelease(),
                protection.isOtpRequired(),
                protection.isRiskReviewRequired(),
                matchedBand.getExplanationTemplate(),
                evaluatedAt);
    }

    public Long riskPolicyId() {
        return policySnapshot.riskPolicyId();
    }

    public String policyVersion() {
        return policySnapshot.policyVersion();
    }

    public Long riskPolicyBandId() {
        return policySnapshot.riskPolicyBandId();
    }

    public Long protectionPolicyId() {
        return policySnapshot.protectionPolicyId();
    }

    public RiskTier riskTier() {
        return policySnapshot.riskTier();
    }

    public String matchedBandCode() {
        return policySnapshot.matchedBandCode();
    }

    private static String requireExplanation(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "explanation is required");
        }

        String normalized = value.trim();

        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(
                    "explanation exceeds 1000 characters");
        }

        return normalized;
    }

    private static void validateProtectionAction(
            Long seconds,
            ProtectionReleaseMode mode,
            boolean customerCanCancel,
            boolean autoRelease,
            boolean otpRequired,
            boolean riskReviewRequired) {

        boolean valid = switch (mode) {
            case IMMEDIATE ->
                    seconds != null
                            && seconds == 0L
                            && !customerCanCancel
                            && autoRelease
                            && !otpRequired
                            && !riskReviewRequired;

            case AFTER_TIMER ->
                    seconds != null
                            && seconds > 0L
                            && customerCanCancel
                            && autoRelease
                            && !otpRequired
                            && !riskReviewRequired;

            case AFTER_REVIEW ->
                    seconds == null
                            && customerCanCancel
                            && !autoRelease
                            && otpRequired
                            && riskReviewRequired;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "protection action is inconsistent");
        }
    }
}
