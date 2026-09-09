package com.safepay.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskEngineTest {
    private final RiskEngine engine = new RiskEngine();

    @Test void lowRiskHasNoProtection() {
        RiskAssessment result = engine.assess(new RiskContext(new BigDecimal("500.00"), 48, false, null));
        assertEquals(0, result.score()); assertEquals(RiskTier.LOW, result.tier()); assertEquals(0, result.protectionWindowSeconds());
    }
    @Test void newBeneficiaryIsMediumRisk() {
        RiskAssessment result = engine.assess(new RiskContext(new BigDecimal("500.00"), 1, false, null));
        assertEquals(25, result.score()); assertEquals(RiskTier.MEDIUM, result.tier()); assertEquals(10, result.protectionWindowSeconds());
    }
    @Test void combinedSignalsAreHighRisk() {
        RiskAssessment result = engine.assess(new RiskContext(new BigDecimal("150000.00"), 1, true, new BigDecimal("40000.00")));
        assertEquals(80, result.score()); assertEquals(RiskTier.HIGH, result.tier()); assertEquals(60, result.protectionWindowSeconds());
    }
}
