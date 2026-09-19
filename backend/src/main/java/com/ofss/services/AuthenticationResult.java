package com.ofss.services;

import java.time.OffsetDateTime;

import com.ofss.dto.auth.AuthTokenResponse;

public record AuthenticationResult(
        AuthTokenResponse response,
        String refreshToken,
        OffsetDateTime refreshExpiresAt) {
}
