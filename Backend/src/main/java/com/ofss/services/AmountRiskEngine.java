package com.ofss.services;

import java.math.BigDecimal;

import com.ofss.beans.RiskAssessment;
import com.ofss.beans.RiskTier;

public class AmountRiskEngine {

    private static final BigDecimal LOW_LIMIT = new BigDecimal("10000");
    private static final BigDecimal MEDIUM_LIMIT = new BigDecimal("50000");
    private static final BigDecimal HIGH_LIMIT = new BigDecimal("100000");

    public RiskAssessment assess(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (amount.compareTo(LOW_LIMIT) <= 0) {
            return new RiskAssessment(RiskTier.LOW, 0, false, "Amount is within the Low-risk range.");
        }
        if (amount.compareTo(MEDIUM_LIMIT) <= 0) {
            return new RiskAssessment(RiskTier.MEDIUM, 10, false, "Amount is within the Medium-risk range.");
        }
        if (amount.compareTo(HIGH_LIMIT) <= 0) {
            return new RiskAssessment(RiskTier.HIGH, 30, false, "Amount is within the High-risk range.");
        }
        return new RiskAssessment(RiskTier.HARD_HOLD, 0, true,
                "Amount is above the High-risk limit and requires administrator approval.");
    }
}
