package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SecurityPolicyPropertiesTest {

    private static final String VALID_SECRET = Base64.getEncoder()
            .encodeToString(new byte[32]);

    @Test
    void acceptsApprovedJwtPolicy() {
        JwtSecurityProperties properties = new JwtSecurityProperties(
                "safepay-backend",
                "safepay-pwa",
                VALID_SECRET,
                Duration.ofMinutes(15));

        assertThat(properties.signingKey().getEncoded()).hasSize(32);
        assertThat(properties.accessTokenValidity())
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void rejectsMalformedBase64Secret() {
        assertThatThrownBy(() -> new JwtSecurityProperties(
                "issuer", "audience", "%%%", Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Base64");
    }

    @Test
    void rejectsSigningSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder()
                .encodeToString(new byte[31]);
        assertThatThrownBy(() -> new JwtSecurityProperties(
                "issuer", "audience", shortSecret, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void rejectsUnapprovedAccessTokenValidity() {
        assertThatThrownBy(() -> new JwtSecurityProperties(
                "issuer", "audience", VALID_SECRET, Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PT15M");
    }

    @Test
    void acceptsApprovedAuthenticationPolicy() {
        AuthenticationPolicyProperties properties =
                approvedAuthenticationPolicy();
        assertThat(properties.failedLoginThreshold()).isEqualTo(5);
        assertThat(properties.refreshSessionValidity())
                .isEqualTo(Duration.ofDays(7));
    }

    @Test
    void rejectsUnapprovedFailureThreshold() {
        assertThatThrownBy(() -> new AuthenticationPolicyProperties(
                4, Duration.ofMinutes(15), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnapprovedTemporaryLockDuration() {
        assertThatThrownBy(() -> new AuthenticationPolicyProperties(
                5, Duration.ofMinutes(10), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnapprovedRefreshValidity() {
        assertThatThrownBy(() -> new AuthenticationPolicyProperties(
                5, Duration.ofMinutes(15), Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsStrictCookieContractForLocalHttpTesting() {
        RefreshCookieProperties properties =
                new RefreshCookieProperties(
                        "SAFEPAY_REFRESH",
                        "/api/v1/auth",
                        "Strict",
                        false);
        assertThat(properties.secure()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "WRONG,/api/v1/auth,Strict",
            "SAFEPAY_REFRESH,/wrong,Strict",
            "SAFEPAY_REFRESH,/api/v1/auth,Lax"
    })
    void rejectsCookieContractDrift(
            String name,
            String path,
            String sameSite) {
        assertThatThrownBy(() -> new RefreshCookieProperties(
                name, path, sameSite, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AuthenticationPolicyProperties
            approvedAuthenticationPolicy() {
        return new AuthenticationPolicyProperties(
                5,
                Duration.ofMinutes(15),
                Duration.ofDays(7));
    }
}
