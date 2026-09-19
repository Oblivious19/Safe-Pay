package com.ofss.dto.risk;

import java.util.Locale;
import java.util.Objects;

import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;

public record RiskPolicySnapshot(
        Long riskPolicyId,
        String policyVersion,
        Long riskPolicyBandId,
        Long protectionPolicyId,
        RiskTier riskTier,
        String matchedBandCode) {

    public RiskPolicySnapshot {
        riskPolicyId = requirePositiveId(
                riskPolicyId,
                "riskPolicyId");

        policyVersion = requireCode(
                policyVersion,
                "policyVersion",
                50);

        riskPolicyBandId = requirePositiveId(
                riskPolicyBandId,
                "riskPolicyBandId");

        protectionPolicyId = requirePositiveId(
                protectionPolicyId,
                "protectionPolicyId");

        riskTier = Objects.requireNonNull(
                riskTier,
                "riskTier is required");

        matchedBandCode = requireCode(
                matchedBandCode,
                "matchedBandCode",
                50);
    }

    public static RiskPolicySnapshot from(
            RiskPolicyBand matchedBand) {

        Objects.requireNonNull(
                matchedBand,
                "matchedBand is required");

        RiskPolicy policy = Objects.requireNonNull(
                matchedBand.getRiskPolicy(),
                "matchedBand riskPolicy is required");

        ProtectionPolicy protection = Objects.requireNonNull(
                matchedBand.getProtectionPolicy(),
                "matchedBand protectionPolicy is required");

        return new RiskPolicySnapshot(
                policy.getRiskPolicyId(),
                policy.getPolicyVersion(),
                matchedBand.getRiskPolicyBandId(),
                protection.getProtectionPolicyId(),
                matchedBand.getRiskTier(),
                matchedBand.getBandCode());
    }

    private static Long requirePositiveId(
            Long value,
            String fieldName) {

        if (value == null || value <= 0L) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive");
        }

        return value;
    }

    private static String requireCode(
            String value,
            String fieldName,
            int maximumLength) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength
                || !normalized.equals(
                        normalized.toUpperCase(Locale.ROOT))) {

            throw new IllegalArgumentException(
                    fieldName + " has an invalid format");
        }

        return normalized;
    }
}
