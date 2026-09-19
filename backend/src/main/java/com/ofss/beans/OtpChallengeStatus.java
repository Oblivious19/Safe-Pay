package com.ofss.beans;

public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    LOCKED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
