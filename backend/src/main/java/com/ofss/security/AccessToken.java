package com.ofss.security;

import java.time.Instant;
import java.util.Objects;

public record AccessToken(
        String value,
        Instant expiresAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "value is required");
        }
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
