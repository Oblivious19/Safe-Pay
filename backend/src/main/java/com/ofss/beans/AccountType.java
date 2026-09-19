package com.ofss.beans;

public enum AccountType {

    SAVINGS,
    CURRENT,
    OUTBOUND_CLEARING,
    OPENING_BALANCE_CONTROL;

    public boolean isCustomerOwnedType() {
        return this == SAVINGS || this == CURRENT;
    }

    public boolean isSystemType() {
        return this == OUTBOUND_CLEARING
                || this == OPENING_BALANCE_CONTROL;
    }
}