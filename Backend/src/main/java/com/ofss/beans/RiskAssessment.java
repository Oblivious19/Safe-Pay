package com.ofss.beans;

public record RiskAssessment(RiskTier riskTier, int protectionSeconds,
        boolean authenticationRequired, String reason) {
}
