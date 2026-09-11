package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ofss.beans.RiskAssessment;
import com.ofss.beans.RiskTier;

class AmountRiskEngineTest {

    private final AmountRiskEngine riskEngine = new AmountRiskEngine();

    @Test
    void amountUpToTenThousandIsLowRisk() {
        RiskAssessment result = riskEngine.assess(new BigDecimal("10000"));
        assertEquals(RiskTier.LOW, result.riskTier());
        assertEquals(0, result.protectionSeconds());
        assertFalse(result.authenticationRequired());
    }

    @Test
    void amountFromTenThousandOneToFiftyThousandIsMediumRisk() {
        RiskAssessment result = riskEngine.assess(new BigDecimal("10001"));
        assertEquals(RiskTier.MEDIUM, result.riskTier());
        assertEquals(10, result.protectionSeconds());
    }

    @Test
    void amountFromFiftyThousandOneToOneLakhIsHighRisk() {
        RiskAssessment result = riskEngine.assess(new BigDecimal("50001"));
        assertEquals(RiskTier.HIGH, result.riskTier());
        assertEquals(60, result.protectionSeconds());
    }

    @Test
    void amountAboveOneLakhRequiresHardHoldAuthentication() {
        RiskAssessment result = riskEngine.assess(new BigDecimal("100001"));
        assertEquals(RiskTier.HARD_HOLD, result.riskTier());
        assertTrue(result.authenticationRequired());
    }

    @ParameterizedTest
    @CsvSource({
            "0.01, LOW, 0, false",
            "1.00, LOW, 0, false",
            "10000.00, LOW, 0, false",
            "10000.01, MEDIUM, 10, false",
            "50000.00, MEDIUM, 10, false",
            "50000.01, HIGH, 60, false",
            "100000.00, HIGH, 60, false",
            "100000.01, HARD_HOLD, 0, true"
    })
    void evaluatesExactPaiseBoundariesWithAnExplanation(String amount, RiskTier tier,
            int protectionSeconds, boolean authenticationRequired) {
        RiskAssessment result = riskEngine.assess(new BigDecimal(amount));
        assertEquals(tier, result.riskTier());
        assertEquals(protectionSeconds, result.protectionSeconds());
        assertEquals(authenticationRequired, result.authenticationRequired());
        assertFalse(result.reason().isBlank());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01"})
    void invalidAmountNeverDefaultsToLowRisk(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> riskEngine.assess(amount == null ? null : new BigDecimal(amount)));
    }
}
