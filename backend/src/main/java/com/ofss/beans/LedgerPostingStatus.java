package com.ofss.beans;

public enum LedgerPostingStatus {

    PENDING,
    POSTED,
    FAILED;

    public boolean isTerminal() {
        return this == POSTED || this == FAILED;
    }
}
