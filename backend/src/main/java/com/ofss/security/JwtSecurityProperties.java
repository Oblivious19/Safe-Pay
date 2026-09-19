package com.ofss.security;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.security.jwt")
public final class JwtSecurityProperties {

    public static final Duration APPROVED_ACCESS_TOKEN_VALIDITY =
            Duration.ofMinutes(15);

    private final String issuer;
    private final String audience;
    private final SecretKey signingKey;
    private final Duration accessTokenValidity;

    public JwtSecurityProperties(
            String issuer,
            String audience,
            String secretBase64,
            Duration accessTokenValidity) {

        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.accessTokenValidity = Objects.requireNonNull(
                accessTokenValidity,
                "accessTokenValidity is required");

        if (!APPROVED_ACCESS_TOKEN_VALIDITY.equals(
                accessTokenValidity)) {
            throw new IllegalArgumentException(
                    "accessTokenValidity must be PT15M for SafePay V1");
        }

        String encodedSecret = requireText(
                secretBase64,
                "secretBase64");

        byte[] decodedSecret;
        try {
            decodedSecret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "secretBase64 must contain valid Base64",
                    exception);
        }

        if (decodedSecret.length < 32) {
            throw new IllegalArgumentException(
                    "secretBase64 must decode to at least 256 bits");
        }

        this.signingKey = new SecretKeySpec(
                decodedSecret,
                "HmacSHA256");
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public SecretKey signingKey() {
        return signingKey;
    }

    public Duration accessTokenValidity() {
        return accessTokenValidity;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required");
        }
        return value.trim();
    }
}
