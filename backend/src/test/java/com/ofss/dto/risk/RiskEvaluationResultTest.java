package com.ofss.dto.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;

class RiskEvaluationResultTest {

    private static final OffsetDateTime EVALUATED_AT =
            OffsetDateTime.parse(
                    "2026-09-15T10:15:30.123456789+05:30");

    @Test
    void buildsCoherentSnapshotFromOneMatchedBand() {
        RiskPolicyBand band = matchedBand(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L,
                true,
                true,
                false,
                false);

        RiskPolicySnapshot snapshot =
                RiskEvaluationResult.fromMatchedBand(
                        band,
                        EVALUATED_AT)
                        .policySnapshot();

        assertThat(snapshot.riskPolicyId()).isEqualTo(11L);
        assertThat(snapshot.policyVersion())
                .isEqualTo("AMOUNT_ONLY_V1");
        assertThat(snapshot.riskPolicyBandId())
                .isEqualTo(22L);
        assertThat(snapshot.protectionPolicyId())
                .isEqualTo(33L);
        assertThat(snapshot.riskTier())
                .isEqualTo(RiskTier.MEDIUM);
        assertThat(snapshot.matchedBandCode())
                .isEqualTo("AMOUNT_MEDIUM_V1");
    }

    @Test
    void createsLowImmediateResultWithNullScore() {
        RiskEvaluationResult result =
                RiskEvaluationResult.fromMatchedBand(
                        matchedBand(
                                RiskTier.LOW,
                                ProtectionReleaseMode.IMMEDIATE,
                                0L,
                                false,
                                true,
                                false,
                                false),
                        EVALUATED_AT);

        assertThat(result.riskTier()).isEqualTo(RiskTier.LOW);
        assertThat(result.riskScore()).isNull();
        assertThat(result.protectionSeconds()).isZero();
        assertThat(result.releaseMode())
                .isEqualTo(ProtectionReleaseMode.IMMEDIATE);
        assertThat(result.customerCanCancel()).isFalse();
        assertThat(result.autoRelease()).isTrue();
    }

    @Test
    void createsTimedResultWithCancellation() {
        RiskEvaluationResult result =
                RiskEvaluationResult.fromMatchedBand(
                        matchedBand(
                                RiskTier.HIGH,
                                ProtectionReleaseMode.AFTER_TIMER,
                                60L,
                                true,
                                true,
                                false,
                                false),
                        EVALUATED_AT);

        assertThat(result.riskTier()).isEqualTo(RiskTier.HIGH);
        assertThat(result.protectionSeconds())
                .isEqualTo(60L);
        assertThat(result.customerCanCancel()).isTrue();
        assertThat(result.otpRequired()).isFalse();
    }

    @Test
    void createsUntimedVeryHighReviewResult() {
        RiskEvaluationResult result =
                RiskEvaluationResult.fromMatchedBand(
                        matchedBand(
                                RiskTier.VERY_HIGH,
                                ProtectionReleaseMode.AFTER_REVIEW,
                                null,
                                true,
                                false,
                                true,
                                true),
                        EVALUATED_AT);

        assertThat(result.riskTier())
                .isEqualTo(RiskTier.VERY_HIGH);
        assertThat(result.protectionSeconds()).isNull();
        assertThat(result.autoRelease()).isFalse();
        assertThat(result.otpRequired()).isTrue();
        assertThat(result.riskReviewRequired()).isTrue();
    }

    @Test
    void normalizesEvaluationTimeToUtcMicroseconds() {
        RiskEvaluationResult result =
                RiskEvaluationResult.fromMatchedBand(
                        matchedBand(
                                RiskTier.MEDIUM,
                                ProtectionReleaseMode.AFTER_TIMER,
                                10L,
                                true,
                                true,
                                false,
                                false),
                        EVALUATED_AT);

        assertThat(result.evaluatedAt().toString())
                .isEqualTo("2026-09-15T04:45:30.123456Z");
    }

    @Test
    void rejectsInventedV1RiskScore() {
        assertThatThrownBy(() -> new RiskEvaluationResult(
                validSnapshot(),
                new BigDecimal("42.00"),
                0L,
                ProtectionReleaseMode.IMMEDIATE,
                false,
                true,
                false,
                false,
                "Safe explanation",
                EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("V1 riskScore must be null");
    }

    @Test
    void rejectsInconsistentProtectionAction() {
        assertThatThrownBy(() -> new RiskEvaluationResult(
                validSnapshot(),
                null,
                10L,
                ProtectionReleaseMode.AFTER_TIMER,
                false,
                true,
                false,
                false,
                "Safe explanation",
                EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("protection action is inconsistent");
    }

    @Test
    void rejectsIncompleteOrNonCanonicalSnapshot() {
        assertThatThrownBy(() -> new RiskPolicySnapshot(
                11L,
                "amount_only_v1",
                0L,
                33L,
                RiskTier.LOW,
                "AMOUNT_LOW_V1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "policyVersion has an invalid format");
    }

    private RiskPolicySnapshot validSnapshot() {
        return new RiskPolicySnapshot(
                11L,
                "AMOUNT_ONLY_V1",
                22L,
                33L,
                RiskTier.LOW,
                "AMOUNT_LOW_V1");
    }

    private RiskPolicyBand matchedBand(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long seconds,
            boolean customerCanCancel,
            boolean autoRelease,
            boolean otpRequired,
            boolean riskReviewRequired) {

        RiskPolicy policy = mock(RiskPolicy.class);
        when(policy.getRiskPolicyId()).thenReturn(11L);
        when(policy.getPolicyVersion())
                .thenReturn("AMOUNT_ONLY_V1");

        ProtectionPolicy protection =
                mock(ProtectionPolicy.class);
        when(protection.getProtectionPolicyId())
                .thenReturn(33L);
        when(protection.getReleaseMode())
                .thenReturn(releaseMode);
        when(protection.getProtectionSeconds())
                .thenReturn(seconds);
        when(protection.canCustomerCancel())
                .thenReturn(customerCanCancel);
        when(protection.isAutoRelease())
                .thenReturn(autoRelease);
        when(protection.isOtpRequired())
                .thenReturn(otpRequired);
        when(protection.isRiskReviewRequired())
                .thenReturn(riskReviewRequired);

        RiskPolicyBand band = mock(RiskPolicyBand.class);
        when(band.getRiskPolicy()).thenReturn(policy);
        when(band.getProtectionPolicy())
                .thenReturn(protection);
        when(band.getRiskPolicyBandId()).thenReturn(22L);
        when(band.getRiskTier()).thenReturn(tier);
        when(band.getBandCode()).thenReturn(
                "AMOUNT_" + tier.name() + "_V1");
        when(band.getExplanationTemplate()).thenReturn(
                "The payment amount matched the SafePay V1 "
                        + tier.name()
                        + " band.");

        return band;
    }
}
