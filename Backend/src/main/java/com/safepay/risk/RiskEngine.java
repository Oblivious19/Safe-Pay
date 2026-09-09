package com.safepay.risk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Framework-free Phase 1 scoring rules, intentionally kept separate from persistence and policy orchestration. */
public final class RiskEngine {
    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("100000.00");
    private static final BigDecimal HISTORY_MULTIPLIER = BigDecimal.valueOf(3);

    public RiskAssessment assess(RiskContext context) {
        if (context == null || context.amount() == null || context.amount().signum() <= 0) {
            throw new IllegalArgumentException("A positive payment amount is required for risk assessment");
        }
        List<RiskSignal> signals = new ArrayList<>();
        if (context.beneficiaryAgeHours() < 24) {
            signals.add(new RiskSignal("NEW_BENEFICIARY", 25, "Beneficiary was added less than 24 hours ago"));
        }
        if (context.firstPaymentToBeneficiary()) {
            signals.add(new RiskSignal("FIRST_PAYMENT", 15, "This is the first payment to this beneficiary"));
        }
        if (context.amount().compareTo(HIGH_AMOUNT) > 0) {
            signals.add(new RiskSignal("HIGH_ABSOLUTE_AMOUNT", 20, "Payment amount is above ₹1,00,000"));
        }
        if (context.averageMonthlySpend() != null
                && context.amount().compareTo(context.averageMonthlySpend().multiply(HISTORY_MULTIPLIER)) > 0) {
            signals.add(new RiskSignal("AMOUNT_VS_HISTORY", 20, "Payment amount is more than three times average monthly spend"));
        }
        int score = signals.stream().mapToInt(RiskSignal::weight).sum();
        RiskTier tier = score >= 50 ? RiskTier.HIGH : score >= 25 ? RiskTier.MEDIUM : RiskTier.LOW;
        int seconds = tier == RiskTier.HIGH ? 60 : tier == RiskTier.MEDIUM ? 10 : 0;
        return new RiskAssessment(score, tier, seconds, List.copyOf(signals));
    }
}
