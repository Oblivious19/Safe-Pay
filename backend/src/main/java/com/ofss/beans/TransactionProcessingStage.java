package com.ofss.beans;

public enum TransactionProcessingStage {

    AUTHORIZATION,
    RISK_ASSESSMENT,
    RESERVATION,
    OTP_VERIFICATION,
    RISK_REVIEW,
    RELEASE,
    SETTLEMENT,
    ACCOUNT_UPDATE,
    STATE_TRANSITION,
    SCHEDULER
}
