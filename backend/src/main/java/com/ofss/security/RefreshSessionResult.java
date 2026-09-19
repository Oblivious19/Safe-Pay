package com.ofss.security;

import java.time.OffsetDateTime;
import java.util.Objects;

public record RefreshSessionResult(
        Long userId,
        String rawRefreshToken,
        OffsetDateTime expiresAt) {

    public RefreshSessionResult {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "userId must be positive");
        }
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException(
                    "rawRefreshToken is required");
        }
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
