package com.ofss.beans;

public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }
}
