package com.ofss.services;

import java.math.BigDecimal;
import com.ofss.beans.AssessmentRiskTier;
import com.ofss.beans.RiskAssessmentInput;
import com.ofss.beans.RuleBasedRiskResult;

/** Amount-only policy. No framework, repository, clock, authentication or payment side effects. */
public final class RiskAssessmentEngine {
    private static final BigDecimal LOW_LIMIT = new BigDecimal("10000");
    private static final BigDecimal MEDIUM_LIMIT = new BigDecimal("50000");
    private static final BigDecimal HIGH_LIMIT = new BigDecimal("100000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    public RuleBasedRiskResult assessAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("A positive NUMBER(18,2)-compatible amount is required");
        }
        String explanation = "Amount INR " + amount.setScale(2).toPlainString();
        if (amount.compareTo(LOW_LIMIT) <= 0) {
            return new RuleBasedRiskResult(AssessmentRiskTier.LOW, false, 0,
                    explanation + " is above INR 0 and at most INR 10,000: LOW, immediate settlement.", false);
        }
        if (amount.compareTo(MEDIUM_LIMIT) <= 0) {
            return new RuleBasedRiskResult(AssessmentRiskTier.MEDIUM, true, 10,
                    explanation + " is above INR 10,000 and at most INR 50,000: MEDIUM, 10-second protection window.", false);
        }
        if (amount.compareTo(HIGH_LIMIT) <= 0) {
            return new RuleBasedRiskResult(AssessmentRiskTier.HIGH, true, 60,
                    explanation + " is above INR 50,000 and at most INR 100,000: HIGH, 60-second protection window.", false);
        }
        return new RuleBasedRiskResult(AssessmentRiskTier.VERY_HIGH, true, 0,
                explanation + " is above INR 100,000: VERY_HIGH, authentication required before settlement.", true);
    }

    /** Compatibility adapter: beneficiary, history, device and context no longer affect this policy. */
    public RuleBasedRiskResult assess(RiskAssessmentInput input) {
        if (input == null) throw new IllegalArgumentException("Risk input is required");
        return assessAmount(input.amount());
    }
}
