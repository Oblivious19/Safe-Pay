package com.ofss.beans;

public enum RoleName {

    CUSTOMER,
    RISK_OFFICER,
    SYSTEM_ADMIN,
    AUDITOR;

    public String authority() {
        return name();
    }
}