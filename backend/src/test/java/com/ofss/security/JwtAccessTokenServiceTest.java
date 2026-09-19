package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.ofss.beans.UserStatus;

class JwtAccessTokenServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-09-17T09:00:00Z");

    private final List<JwtEncoderParameters> encoded =
            new ArrayList<>();
    private AccessTokenService service;

    @BeforeEach
    void setUp() {
        JwtEncoder encoder = parameters -> {
            encoded.add(parameters);
            return new Jwt(
                    "signed-token-" + encoded.size(),
                    parameters.getClaims().getIssuedAt(),
                    parameters.getClaims().getExpiresAt(),
                    parameters.getJwsHeader().getHeaders(),
                    parameters.getClaims().getClaims());
        };
        JwtSecurityProperties properties = new JwtSecurityProperties(
                "safepay-backend",
                "safepay-pwa",
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(15));
        service = new JwtAccessTokenService(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuesExactlyFifteenMinuteBearerToken() {
        AccessToken token = service.issue(principal());
        assertThat(token.value()).isEqualTo("signed-token-1");
        assertThat(token.expiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void includesCanonicalIdentityAndSecurityVersionClaims() {
        service.issue(principal());
        Map<String, Object> claims = encoded.getFirst()
                .getClaims().getClaims();
        assertThat(claims)
                .containsEntry("sub", "customer@example.com")
                .containsEntry("user_id", "101")
                .containsEntry("security_version", 7L)
                .containsEntry("iss", "safepay-backend");
        assertThat(strings(claims.get("aud")))
                .containsExactly("safepay-pwa");
    }

    @Test
    void writesAuthoritiesInStableSortedOrder() {
        service.issue(principal());
        assertThat(strings(encoded.getFirst()
                .getClaims().getClaims().get("authorities")))
                .containsExactly("AUDITOR", "SYSTEM_ADMIN");
    }

    @Test
    void givesEveryAccessTokenAUniqueIdentifier() {
        service.issue(principal());
        service.issue(principal());
        assertThat(encoded.get(0).getClaims().getId())
                .isNotEqualTo(encoded.get(1).getClaims().getId());
    }

    private static SafePayPrincipal principal() {
        return new SafePayPrincipal(
                101L,
                "customer@example.com",
                "password-hash",
                UserStatus.ACTIVE,
                7L,
                Set.of(
                        new SimpleGrantedAuthority("SYSTEM_ADMIN"),
                        new SimpleGrantedAuthority("AUDITOR")));
    }

    private static List<String> strings(Object value) {
        return ((List<?>) value).stream()
                .map(String.class::cast)
                .toList();
    }
}
