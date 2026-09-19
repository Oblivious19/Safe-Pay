package com.ofss.security;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.security.authentication")
public record AuthenticationPolicyProperties(
        int failedLoginThreshold,
        Duration temporaryLockDuration,
        Duration refreshSessionValidity) {

    public static final int APPROVED_FAILED_LOGIN_THRESHOLD = 5;
    public static final Duration APPROVED_TEMPORARY_LOCK_DURATION =
            Duration.ofMinutes(15);
    public static final Duration APPROVED_REFRESH_SESSION_VALIDITY =
            Duration.ofDays(7);

    public AuthenticationPolicyProperties {
        Objects.requireNonNull(
                temporaryLockDuration,
                "temporaryLockDuration is required");
        Objects.requireNonNull(
                refreshSessionValidity,
                "refreshSessionValidity is required");

        if (failedLoginThreshold
                != APPROVED_FAILED_LOGIN_THRESHOLD) {
            throw new IllegalArgumentException(
                    "failedLoginThreshold must be 5 for SafePay V1");
        }
        if (!APPROVED_TEMPORARY_LOCK_DURATION.equals(
                temporaryLockDuration)) {
            throw new IllegalArgumentException(
                    "temporaryLockDuration must be PT15M for SafePay V1");
        }
        if (!APPROVED_REFRESH_SESSION_VALIDITY.equals(
                refreshSessionValidity)) {
            throw new IllegalArgumentException(
                    "refreshSessionValidity must be P7D for SafePay V1");
        }
    }
}
