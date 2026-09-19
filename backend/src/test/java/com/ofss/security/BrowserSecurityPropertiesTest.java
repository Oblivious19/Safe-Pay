package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class BrowserSecurityPropertiesTest {

    @Test
    void retainsDistinctExplicitOrigins() {
        BrowserSecurityProperties properties =
                new BrowserSecurityProperties(List.of(
                        "http://localhost:8000",
                        "http://localhost:8000",
                        "https://safepay.example"));
        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:8000",
                "https://safepay.example");
    }

    @Test
    void permitsOnlyAnExactConfiguredOrigin() {
        BrowserSecurityProperties properties =
                new BrowserSecurityProperties(List.of(
                        "http://localhost:8000"));
        assertThat(properties.permits("http://localhost:8000")).isTrue();
        assertThat(properties.permits("http://localhost:8001")).isFalse();
    }

    @Test
    void rejectsWildcardOrigins() {
        assertThatThrownBy(() -> new BrowserSecurityProperties(
                List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOriginsContainingPathsOrQueries() {
        assertThatThrownBy(() -> new BrowserSecurityProperties(
                List.of("http://localhost:8000/app")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BrowserSecurityProperties(
                List.of("http://localhost:8000?debug=true")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyConfiguration() {
        assertThatThrownBy(() -> new BrowserSecurityProperties(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
