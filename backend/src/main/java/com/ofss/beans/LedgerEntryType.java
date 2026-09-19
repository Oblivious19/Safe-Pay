package com.ofss.beans;

public enum LedgerEntryType {

    DEBIT(1),
    CREDIT(2);

    private final int lineNumber;

    LedgerEntryType(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
