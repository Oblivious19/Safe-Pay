package com.safepay.risk;

import java.util.List;

public record RiskAssessment(int score, RiskTier tier, int protectionWindowSeconds,
                             List<RiskSignal> signals) {
    public List<String> reasons() {
        return signals.stream().map(RiskSignal::reason).toList();
    }
}
