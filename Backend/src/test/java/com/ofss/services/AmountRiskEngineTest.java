package com.ofss.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

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
}
