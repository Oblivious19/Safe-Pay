package com.ofss.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.security.refresh-cookie")
public record RefreshCookieProperties(
        String name,
        String path,
        String sameSite,
        boolean secure) {

    public RefreshCookieProperties {
        name = requireExact(name, "SAFEPAY_REFRESH", "name");
        path = requireExact(path, "/api/v1/auth", "path");
        sameSite = requireExact(sameSite, "Strict", "sameSite");
    }

    private static String requireExact(
            String value,
            String expected,
            String fieldName) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(
                    fieldName + " must be " + expected
                            + " for SafePay V1");
        }
        return value;
    }
}
