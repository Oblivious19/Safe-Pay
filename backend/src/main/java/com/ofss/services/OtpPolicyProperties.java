package com.ofss.services;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.otp")
public record OtpPolicyProperties(
        Duration validity,
        int maxAttempts,
        Duration resendCooldown,
        int maxIssuesPerCycle,
        int codeLength) {

    public static final Duration APPROVED_VALIDITY =
            Duration.ofMinutes(5);

    public static final int APPROVED_MAX_ATTEMPTS = 3;

    public static final Duration APPROVED_RESEND_COOLDOWN =
            Duration.ofSeconds(30);

    public static final int APPROVED_MAX_ISSUES_PER_CYCLE = 3;

    public static final int APPROVED_CODE_LENGTH = 6;

    public OtpPolicyProperties {
        Objects.requireNonNull(validity, "validity is required");
        Objects.requireNonNull(
                resendCooldown,
                "resendCooldown is required");

        if (!APPROVED_VALIDITY.equals(validity)) {
            throw new IllegalArgumentException(
                    "validity must be PT5M for SafePay V1");
        }

        if (maxAttempts != APPROVED_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "maxAttempts must be 3 for SafePay V1");
        }

        if (!APPROVED_RESEND_COOLDOWN.equals(
                resendCooldown)) {
            throw new IllegalArgumentException(
                    "resendCooldown must be PT30S for SafePay V1");
        }

        if (maxIssuesPerCycle
                != APPROVED_MAX_ISSUES_PER_CYCLE) {
            throw new IllegalArgumentException(
                    "maxIssuesPerCycle must be 3 for SafePay V1");
        }

        if (codeLength != APPROVED_CODE_LENGTH) {
            throw new IllegalArgumentException(
                    "codeLength must be 6 for SafePay V1");
        }
    }
}
