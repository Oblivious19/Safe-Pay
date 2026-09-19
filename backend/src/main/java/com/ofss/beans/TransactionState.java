package com.ofss.beans;

public enum TransactionState {
    CREATED,
    AUTHORIZED,
    RISK_ASSESSED,
    PROTECTED,
    VERIFICATION_REQUIRED,
    PENDING_RISK_REVIEW,
    RELEASED,
    SETTLED,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this == SETTLED
                || this == CANCELLED
                || this == FAILED;
    }

    public boolean holdsReservation() {
        return this == PROTECTED
                || this == VERIFICATION_REQUIRED
                || this == PENDING_RISK_REVIEW
                || this == RELEASED;
    }

    public boolean allowsCustomerCancellation() {
        return this == CREATED
                || this == PROTECTED
                || this == VERIFICATION_REQUIRED
                || this == PENDING_RISK_REVIEW;
    }
}
