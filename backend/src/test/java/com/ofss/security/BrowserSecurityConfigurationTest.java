package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class BrowserSecurityConfigurationTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void corsAllowsCredentialsOnlyForConfiguredOrigin() {
        CorsConfigurationSource source = config.corsConfigurationSource(
                new BrowserSecurityProperties(List.of(
                        "http://localhost:8000")));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS",
                "/api/v1/transactions");
        CorsConfiguration cors = source.getCorsConfiguration(request);
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:8000")
                .doesNotContain("*");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getAllowedHeaders())
                .contains("Authorization", "X-XSRF-TOKEN", "Idempotency-Key");
    }

    @Test
    void csrfCookieUsesApprovedBrowserShape() {
        var repository = config.csrfTokenRepository(
                new RefreshCookieProperties(
                        "SAFEPAY_REFRESH",
                        "/api/v1/auth",
                        "Strict",
                        true));
        assertThat(repository).isNotNull();
    }
}
