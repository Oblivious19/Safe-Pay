package com.ofss.beans;

public enum RiskTier {
    LOW, MEDIUM, HIGH, VERY_HIGH,
    // Legacy compatibility only: remove when AmountRiskEngine is disconnected.
    HARD_HOLD
}
