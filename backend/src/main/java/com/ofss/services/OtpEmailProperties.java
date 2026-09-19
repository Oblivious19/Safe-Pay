package com.ofss.services;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.otp.email")
public record OtpEmailProperties(
        boolean enabled,
        String from,
        OtpEmailRoutingMode routingMode,
        String recipientOverride) {

    public String requireFromAddress() {
        return requireEmail(from, "from address");
    }

    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }

        requireFromAddress();
        OtpEmailRoutingMode mode = requireRoutingMode();

        if (mode == OtpEmailRoutingMode.FIXED_OVERRIDE) {
            requireEmail(recipientOverride, "recipient override");
        } else if (recipientOverride != null
                && !recipientOverride.isBlank()) {
            throw new IllegalStateException(
                    "OTP email recipient override must be empty "
                            + "when routing mode is STORED_USER");
        }
    }

    public String resolveRecipient(String storedUserEmail) {
        String storedRecipient = requireEmail(
                storedUserEmail,
                "stored recipient");

        if (!enabled) {
            return storedRecipient;
        }

        return switch (requireRoutingMode()) {
            case FIXED_OVERRIDE -> requireEmail(
                    recipientOverride,
                    "recipient override");
            case STORED_USER -> storedRecipient;
        };
    }

    private OtpEmailRoutingMode requireRoutingMode() {
        if (routingMode == null) {
            throw new IllegalStateException(
                    "OTP email routing mode is not configured");
        }
        return routingMode;
    }

    static String requireEmail(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "OTP email " + fieldName + " is not configured");
        }

        String normalized = value.trim();
        int separator = normalized.indexOf('@');

        if (normalized.length() > 254
                || separator <= 0
                || separator != normalized.lastIndexOf('@')
                || separator == normalized.length() - 1) {
            throw new IllegalStateException(
                    "OTP email " + fieldName + " is invalid");
        }

        return normalized;
    }
}
