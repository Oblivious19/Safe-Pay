package com.ofss.security;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieFactory {

    private final RefreshCookieProperties properties;

    public RefreshCookieFactory(RefreshCookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(
            String refreshToken,
            OffsetDateTime expiresAt,
            OffsetDateTime now) {
        Duration maxAge = Duration.between(now, expiresAt);
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException(
                    "expiresAt must be after now");
        }
        return base(refreshToken)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clear() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public String cookieName() {
        return properties.name();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path());
    }
}
