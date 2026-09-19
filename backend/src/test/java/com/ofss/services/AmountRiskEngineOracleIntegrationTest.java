package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskTier;
import com.ofss.dto.risk.RiskEvaluationResult;

@SpringBootTest
class AmountRiskEngineOracleIntegrationTest {

    @Autowired
    private AmountRiskEngine engine;

    @ParameterizedTest
    @MethodSource("validBoundaryCases")
    void evaluatesEveryMandatoryBoundaryFromCanonicalOraclePolicy(
            String amountText,
            RiskTier expectedTier,
            String expectedBandCode,
            ProtectionReleaseMode expectedReleaseMode,
            Long expectedSeconds,
            boolean customerCanCancel,
            boolean autoRelease,
            boolean otpRequired,
            boolean riskReviewRequired) {

        RiskEvaluationResult result = engine.evaluate(
                new BigDecimal(amountText));

        assertThat(result.riskPolicyId()).isPositive();
        assertThat(result.policyVersion())
                .isEqualTo("AMOUNT_ONLY_V1");
        assertThat(result.riskPolicyBandId()).isPositive();
        assertThat(result.protectionPolicyId()).isPositive();
        assertThat(result.riskTier()).isEqualTo(expectedTier);
        assertThat(result.matchedBandCode())
                .isEqualTo(expectedBandCode);
        assertThat(result.riskScore()).isNull();
        assertThat(result.protectionSeconds())
                .isEqualTo(expectedSeconds);
        assertThat(result.releaseMode())
                .isEqualTo(expectedReleaseMode);
        assertThat(result.customerCanCancel())
                .isEqualTo(customerCanCancel);
        assertThat(result.autoRelease()).isEqualTo(autoRelease);
        assertThat(result.otpRequired()).isEqualTo(otpRequired);
        assertThat(result.riskReviewRequired())
                .isEqualTo(riskReviewRequired);
        assertThat(result.explanation())
                .contains(expectedTier.name());
        assertThat(result.evaluatedAt().getOffset())
                .isEqualTo(ZoneOffset.UTC);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-1.00",
            "0.00",
            "0.01",
            "0.99",
            "1.000",
            "5000.001",
            "10000000000000000.00"
    })
    void rejectsInvalidAmountsBeforeOraclePolicyLookup(
            String amountText) {

        assertThatThrownBy(() -> engine.evaluate(
                new BigDecimal(amountText)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAmount() {
        assertThatThrownBy(() -> engine.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount is required");
    }

    @Test
    void sameAmountKeepsSamePolicyBandAndProtectionAction() {
        BigDecimal amount = new BigDecimal("5000.01");

        RiskEvaluationResult first = engine.evaluate(amount);
        RiskEvaluationResult second = engine.evaluate(amount);

        assertThat(second.policySnapshot())
                .isEqualTo(first.policySnapshot());
        assertThat(second.riskScore()).isEqualTo(first.riskScore());
        assertThat(second.protectionSeconds())
                .isEqualTo(first.protectionSeconds());
        assertThat(second.releaseMode())
                .isEqualTo(first.releaseMode());
        assertThat(second.customerCanCancel())
                .isEqualTo(first.customerCanCancel());
        assertThat(second.autoRelease())
                .isEqualTo(first.autoRelease());
        assertThat(second.otpRequired())
                .isEqualTo(first.otpRequired());
        assertThat(second.riskReviewRequired())
                .isEqualTo(first.riskReviewRequired());
        assertThat(second.explanation())
                .isEqualTo(first.explanation());
    }

    private static Stream<Arguments> validBoundaryCases() {
        return Stream.of(
                Arguments.of(
                        "1.00",
                        RiskTier.LOW,
                        "AMOUNT_LOW_V1",
                        ProtectionReleaseMode.IMMEDIATE,
                        0L,
                        false,
                        true,
                        false,
                        false),
                Arguments.of(
                        "5000.00",
                        RiskTier.LOW,
                        "AMOUNT_LOW_V1",
                        ProtectionReleaseMode.IMMEDIATE,
                        0L,
                        false,
                        true,
                        false,
                        false),
                Arguments.of(
                        "5000.01",
                        RiskTier.MEDIUM,
                        "AMOUNT_MEDIUM_V1",
                        ProtectionReleaseMode.AFTER_TIMER,
                        10L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "25000.00",
                        RiskTier.MEDIUM,
                        "AMOUNT_MEDIUM_V1",
                        ProtectionReleaseMode.AFTER_TIMER,
                        10L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "25000.01",
                        RiskTier.HIGH,
                        "AMOUNT_HIGH_V1",
                        ProtectionReleaseMode.AFTER_TIMER,
                        60L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "100000.00",
                        RiskTier.HIGH,
                        "AMOUNT_HIGH_V1",
                        ProtectionReleaseMode.AFTER_TIMER,
                        60L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "100000.01",
                        RiskTier.VERY_HIGH,
                        "AMOUNT_VERY_HIGH_V1",
                        ProtectionReleaseMode.AFTER_REVIEW,
                        null,
                        true,
                        false,
                        true,
                        true),
                Arguments.of(
                        "9999999999999999.99",
                        RiskTier.VERY_HIGH,
                        "AMOUNT_VERY_HIGH_V1",
                        ProtectionReleaseMode.AFTER_REVIEW,
                        null,
                        true,
                        false,
                        true,
                        true));
    }
}
