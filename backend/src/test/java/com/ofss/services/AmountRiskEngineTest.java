package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ofss.beans.ProtectionPolicy;
import com.ofss.beans.ProtectionReleaseMode;
import com.ofss.beans.RiskPolicy;
import com.ofss.beans.RiskPolicyBand;
import com.ofss.beans.RiskTier;
import com.ofss.dto.risk.RiskEvaluationResult;

@ExtendWith(MockitoExtension.class)
class AmountRiskEngineTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T10:15:30.123456789Z"),
            ZoneOffset.UTC);

    private static final OffsetDateTime EVALUATED_AT =
            OffsetDateTime.parse(
                    "2026-09-15T10:15:30.123456Z");

    @Mock
    private RiskPolicyService riskPolicyService;

    private AmountRiskEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AmountRiskEngine(
                riskPolicyService,
                FIXED_CLOCK);
    }

    @ParameterizedTest
    @MethodSource("validBoundaryCases")
    void returnsCompleteDeterministicResultAtEveryBoundary(
            String amountText,
            RiskTier expectedTier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds,
            boolean customerCanCancel,
            boolean autoRelease,
            boolean otpRequired,
            boolean riskReviewRequired) {

        BigDecimal amount = new BigDecimal(amountText);

        RiskPolicyBand band = matchedBand(
                expectedTier,
                releaseMode,
                protectionSeconds,
                customerCanCancel,
                autoRelease,
                otpRequired,
                riskReviewRequired);

        when(riskPolicyService.resolveRequiredBand(
                amount,
                EVALUATED_AT))
                .thenReturn(band);

        RiskEvaluationResult result = engine.evaluate(amount);

        assertThat(result.riskTier()).isEqualTo(expectedTier);
        assertThat(result.riskScore()).isNull();
        assertThat(result.protectionSeconds())
                .isEqualTo(protectionSeconds);
        assertThat(result.releaseMode()).isEqualTo(releaseMode);
        assertThat(result.customerCanCancel())
                .isEqualTo(customerCanCancel);
        assertThat(result.autoRelease()).isEqualTo(autoRelease);
        assertThat(result.otpRequired()).isEqualTo(otpRequired);
        assertThat(result.riskReviewRequired())
                .isEqualTo(riskReviewRequired);
        assertThat(result.policyVersion())
                .isEqualTo("AMOUNT_ONLY_V1");
        assertThat(result.matchedBandCode())
                .isEqualTo("AMOUNT_" + expectedTier.name() + "_V1");
        assertThat(result.explanation())
                .isEqualTo(
                        "The payment amount matched the SafePay V1 "
                                + expectedTier.name()
                                + " band.");
        assertThat(result.evaluatedAt()).isEqualTo(EVALUATED_AT);
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
    void rejectsInvalidAmountsBeforePolicyAccess(String value) {
        assertThatThrownBy(() -> engine.evaluate(
                new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(riskPolicyService);
    }

    @Test
    void rejectsMissingAmountBeforePolicyAccess() {
        assertThatThrownBy(() -> engine.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount is required");

        verifyNoInteractions(riskPolicyService);
    }

    @Test
    void repeatsTheSameResultForTheSameAmountAndPolicy() {
        BigDecimal amount = new BigDecimal("5000.01");

        RiskPolicyBand band = matchedBand(
                RiskTier.MEDIUM,
                ProtectionReleaseMode.AFTER_TIMER,
                10L,
                true,
                true,
                false,
                false);

        when(riskPolicyService.resolveRequiredBand(
                amount,
                EVALUATED_AT))
                .thenReturn(band);

        assertThat(engine.evaluate(amount))
                .isEqualTo(engine.evaluate(amount));
    }

    @Test
    void propagatesPolicyConfigurationFailureWithoutInventingResult() {
        BigDecimal amount = new BigDecimal("1.00");

        when(riskPolicyService.resolveRequiredBand(
                amount,
                EVALUATED_AT))
                .thenThrow(new IllegalStateException(
                        "No eligible V1 risk policy is available"));

        assertThatThrownBy(() -> engine.evaluate(amount))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "No eligible V1 risk policy is available");
    }

    private static Stream<Arguments> validBoundaryCases() {
        return Stream.of(
                Arguments.of(
                        "1.00",
                        RiskTier.LOW,
                        ProtectionReleaseMode.IMMEDIATE,
                        0L,
                        false,
                        true,
                        false,
                        false),
                Arguments.of(
                        "5000.00",
                        RiskTier.LOW,
                        ProtectionReleaseMode.IMMEDIATE,
                        0L,
                        false,
                        true,
                        false,
                        false),
                Arguments.of(
                        "5000.01",
                        RiskTier.MEDIUM,
                        ProtectionReleaseMode.AFTER_TIMER,
                        10L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "25000.00",
                        RiskTier.MEDIUM,
                        ProtectionReleaseMode.AFTER_TIMER,
                        10L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "25000.01",
                        RiskTier.HIGH,
                        ProtectionReleaseMode.AFTER_TIMER,
                        60L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "100000.00",
                        RiskTier.HIGH,
                        ProtectionReleaseMode.AFTER_TIMER,
                        60L,
                        true,
                        true,
                        false,
                        false),
                Arguments.of(
                        "100000.01",
                        RiskTier.VERY_HIGH,
                        ProtectionReleaseMode.AFTER_REVIEW,
                        null,
                        true,
                        false,
                        true,
                        true),
                Arguments.of(
                        "9999999999999999.99",
                        RiskTier.VERY_HIGH,
                        ProtectionReleaseMode.AFTER_REVIEW,
                        null,
                        true,
                        false,
                        true,
                        true));
    }

    private static RiskPolicyBand matchedBand(
            RiskTier tier,
            ProtectionReleaseMode releaseMode,
            Long protectionSeconds,
            boolean customerCanCancel,
            boolean autoRelease,
            boolean otpRequired,
            boolean riskReviewRequired) {

        RiskPolicy policy = mock(RiskPolicy.class);
        ProtectionPolicy protection =
                mock(ProtectionPolicy.class);
        RiskPolicyBand band = mock(RiskPolicyBand.class);

        when(policy.getRiskPolicyId()).thenReturn(11L);
        when(policy.getPolicyVersion())
                .thenReturn("AMOUNT_ONLY_V1");

        when(protection.getProtectionPolicyId())
                .thenReturn(33L);
        when(protection.getProtectionSeconds())
                .thenReturn(protectionSeconds);
        when(protection.getReleaseMode()).thenReturn(releaseMode);
        when(protection.canCustomerCancel())
                .thenReturn(customerCanCancel);
        when(protection.isAutoRelease()).thenReturn(autoRelease);
        when(protection.isOtpRequired()).thenReturn(otpRequired);
        when(protection.isRiskReviewRequired())
                .thenReturn(riskReviewRequired);

        when(band.getRiskPolicyBandId()).thenReturn(22L);
        when(band.getRiskPolicy()).thenReturn(policy);
        when(band.getProtectionPolicy()).thenReturn(protection);
        when(band.getRiskTier()).thenReturn(tier);
        when(band.getBandCode())
                .thenReturn("AMOUNT_" + tier.name() + "_V1");
        when(band.getExplanationTemplate())
                .thenReturn(
                        "The payment amount matched the SafePay V1 "
                                + tier.name()
                                + " band.");

        return band;
    }
}
