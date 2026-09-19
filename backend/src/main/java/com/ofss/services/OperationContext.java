package com.ofss.services;

import java.util.Objects;
import java.util.UUID;

public record OperationContext(
        String correlationId,
        String idempotencyKey) {

    public OperationContext {
        correlationId = requireText(
                correlationId,
                "correlationId",
                64);
        idempotencyKey = normalizeText(
                idempotencyKey,
                "idempotencyKey",
                128);
    }

    public static OperationContext request(
            String correlationId,
            String idempotencyKey) {
        return new OperationContext(
                correlationId,
                idempotencyKey);
    }

    public static OperationContext internal() {
        return new OperationContext(
                "SYSTEM-" + UUID.randomUUID(),
                null);
    }

    private static String requireText(
            String value,
            String fieldName,
            int maximumLength) {
        return Objects.requireNonNull(
                normalizeText(value, fieldName, maximumLength),
                fieldName + " is required");
    }

    private static String normalizeText(
            String value,
            String fieldName,
            int maximumLength) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " has an invalid length");
        }
        return normalized;
    }
}
