package com.ofss.services;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.ofss.beans.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RiskAssessmentEngineTest {
    private final RiskAssessmentEngine engine = new RiskAssessmentEngine();
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    @ParameterizedTest
    @CsvSource({
            "0.01,LOW,0,false,false",
            "5000.00,LOW,0,false,false",
            "9999.99,LOW,0,false,false",
            "10000.00,LOW,0,false,false",
            "10000.01,MEDIUM,10,true,false",
            "49999.99,MEDIUM,10,true,false",
            "50000.00,MEDIUM,10,true,false",
            "50000.01,HIGH,30,true,false",
            "99999.99,HIGH,30,true,false",
            "100000.00,HIGH,30,true,false",
            "100000.01,VERY_HIGH,0,true,true",
            "9999999999999999.99,VERY_HIGH,0,true,true"
    })
    void exactMoneyBandsDetermineTierTimerAndAuthentication(String amount, AssessmentRiskTier tier,
            int duration, boolean protection, boolean authentication) {
        RuleBasedRiskResult result = engine.assessAmount(new BigDecimal(amount));
        assertEquals(tier, result.riskTier());
        assertEquals(duration, result.protectionDurationSeconds());
        assertEquals(protection, result.protectionRequired());
        assertEquals(authentication, result.authenticationRequired());
        assertTrue(result.reason().contains("Amount INR " + new BigDecimal(amount).setScale(2).toPlainString()));
        String band = switch (tier) {
            case LOW -> "above INR 0 and at most INR 10,000";
            case MEDIUM -> "above INR 10,000 and at most INR 50,000";
            case HIGH -> "above INR 50,000 and at most INR 100,000";
            case VERY_HIGH -> "above INR 100,000";
        };
        assertTrue(result.reason().contains(band), result.reason());
        assertTrue(result.reason().contains(tier.name()));
        assertTrue(result.reason().length() <= 500);
    }

    @Test
    void firstFiveThousandPaymentToNewBeneficiaryWithUnknownSignalsIsLow() {
        RiskAssessmentInput newBeneficiary = new RiskAssessmentInput(new BigDecimal("5000.00"),
                NOW, NOW, 0, 0, null, RiskContextSignal.UNKNOWN, RiskContextSignal.UNKNOWN);
        RuleBasedRiskResult result = engine.assess(newBeneficiary);
        assertEquals(AssessmentRiskTier.LOW, result.riskTier());
        assertEquals(0, result.protectionDurationSeconds());
        assertFalse(result.protectionRequired());
        assertFalse(result.authenticationRequired());
        assertEquals("Amount INR 5000.00 is above INR 0 and at most INR 10,000: LOW, immediate settlement.",
                result.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"5000.00", "10000.01", "50000.01", "100000.01"})
    void compatibilityAdapterIgnoresAllNonAmountEvidence(String amount) {
        BigDecimal money = new BigDecimal(amount);
        RuleBasedRiskResult expected = engine.assessAmount(money);
        List<RiskAssessmentInput> inputs = List.of(
                new RiskAssessmentInput(money, NOW.minus(Duration.ofDays(365)), NOW, 50, 50, money,
                        RiskContextSignal.NORMAL, RiskContextSignal.NORMAL),
                new RiskAssessmentInput(money, NOW, NOW, 0, 0, null,
                        RiskContextSignal.UNKNOWN, RiskContextSignal.UNKNOWN),
                new RiskAssessmentInput(money, NOW, NOW, 0, 50, new BigDecimal("0.01"),
                        RiskContextSignal.ELEVATED, RiskContextSignal.ELEVATED),
                new RiskAssessmentInput(money, null, null, -1, -1, new BigDecimal("-1"), null, null),
                new RiskAssessmentInput(money, NOW.plus(Duration.ofDays(1)), NOW, 0, 0, null,
                        RiskContextSignal.NORMAL, RiskContextSignal.UNKNOWN));
        for (RiskAssessmentInput input : inputs) assertEquals(expected, engine.assess(input));
    }

    @Test
    void authenticationHoldHasNoAutomaticReleaseTimer() {
        RuleBasedRiskResult result = engine.assessAmount(new BigDecimal("100000.01"));
        assertEquals(AssessmentRiskTier.VERY_HIGH, result.riskTier());
        assertTrue(result.authenticationRequired());
        assertTrue(result.protectionRequired());
        assertEquals(0, result.protectionDurationSeconds());
        assertTrue(result.reason().contains("administrator approval required before settlement"));
    }

    @Test
    void equivalentDecimalRepresentationsHaveTheSameDeterministicReason() {
        RuleBasedRiskResult expected = engine.assessAmount(new BigDecimal("5000.00"));
        assertEquals(expected, engine.assessAmount(new BigDecimal("5000")));
        assertEquals(expected, engine.assessAmount(new BigDecimal("5E+3")));
        assertEquals(expected, new RiskAssessmentEngine().assessAmount(new BigDecimal("5000.00")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-1", "0.001", "1.001", "1.000",
            "10000000000000000", "9999999999999999.991"})
    void invalidMoneyStopsBothEntryPointsInsteadOfDefaultingToLow(String amount) {
        BigDecimal invalid = new BigDecimal(amount);
        assertThrows(IllegalArgumentException.class, () -> engine.assessAmount(invalid));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(
                new RiskAssessmentInput(invalid, NOW, NOW, 0, 0, null,
                        RiskContextSignal.UNKNOWN, RiskContextSignal.UNKNOWN)));
    }

    @Test
    void missingAmountOrInputStopsAssessment() {
        assertThrows(IllegalArgumentException.class, () -> engine.assessAmount(null));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(null));
        assertThrows(IllegalArgumentException.class, () -> engine.assess(
                new RiskAssessmentInput(null, NOW, NOW, 0, 0, null,
                        RiskContextSignal.NORMAL, RiskContextSignal.NORMAL)));
    }
}
