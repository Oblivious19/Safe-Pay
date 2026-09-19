package com.ofss.dto.auth;

import java.time.Instant;
import java.util.List;

import com.ofss.security.AccessToken;
import com.ofss.security.SafePayPrincipal;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String userId,
        List<String> authorities) {

    public static AuthTokenResponse from(
            AccessToken token,
            SafePayPrincipal principal) {
        return new AuthTokenResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                principal.getUserId().toString(),
                principal.getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .sorted()
                        .toList());
    }
}
