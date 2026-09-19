package com.ofss.beans;

public enum TransactionExceptionStatus {

    OPEN,
    RETRY_PENDING,
    MANUAL_REVIEW,
    RESOLVED;

    public boolean isResolved() {
        return this == RESOLVED;
    }
}
