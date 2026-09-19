package com.ofss.security;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "safepay.security.browser")
public final class BrowserSecurityProperties {

    private final List<String> allowedOrigins;
    private final Set<String> allowedOriginSet;

    public BrowserSecurityProperties(List<String> allowedOrigins) {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one browser origin is required");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : allowedOrigins) {
            normalized.add(requireOrigin(value));
        }
        this.allowedOrigins = List.copyOf(normalized);
        this.allowedOriginSet = Set.copyOf(normalized);
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    public boolean permits(String origin) {
        return origin != null
                && allowedOriginSet.contains(origin.trim());
    }

    private static String requireOrigin(String value) {
        if (value == null || value.isBlank() || value.contains("*")) {
            throw new IllegalArgumentException(
                    "Browser origins must be explicit");
        }

        String normalized = value.trim();
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Browser origin is invalid",
                    exception);
        }

        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty())) {
            throw new IllegalArgumentException(
                    "Browser origin must contain only scheme, host and optional port");
        }

        return normalized;
    }
}
