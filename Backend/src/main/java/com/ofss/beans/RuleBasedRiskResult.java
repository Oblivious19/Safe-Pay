package com.ofss.beans;

/** VERY_HIGH has no timed release: duration 0 plus authenticationRequired=true. */
public record RuleBasedRiskResult(AssessmentRiskTier riskTier, boolean protectionRequired,
        int protectionDurationSeconds, String reason, boolean authenticationRequired) {}
