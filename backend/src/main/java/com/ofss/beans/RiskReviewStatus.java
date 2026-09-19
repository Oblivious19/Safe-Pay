package com.ofss.beans;

public enum RiskReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    REVERIFICATION_REQUESTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
