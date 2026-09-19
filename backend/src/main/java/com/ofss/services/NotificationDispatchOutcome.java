package com.ofss.services;

public enum NotificationDispatchOutcome {
    DELIVERED,
    RETRY_SCHEDULED,
    FAILED,
    NOT_FOUND,
    NOT_DUE,
    TERMINAL
}
