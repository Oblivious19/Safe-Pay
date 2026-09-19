package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtDecoderSecurityContractTest {

    private static final String ISSUER = "safepay-backend";
    private static final String AUDIENCE = "safepay-pwa";
    private JwtDecoder decoder;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() {
        JwtBeansConfiguration configuration = new JwtBeansConfiguration();
        JwtSecurityProperties properties = properties((byte) 7);
        encoder = configuration.jwtEncoder(properties);
        decoder = configuration.jwtDecoder(properties);
    }

    @Test
    void acceptsCorrectlySignedUnexpiredTokenWithIssuerAndAudience() {
        String token = encode(
                encoder,
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300));
        assertThat(decoder.decode(token).getSubject())
                .isEqualTo("customer@safepay.test");
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtEncoder attackerEncoder = new JwtBeansConfiguration()
                .jwtEncoder(properties((byte) 9));
        String token = encode(
                attackerEncoder,
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        String token = encode(
                encoder,
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(300),
                Instant.now().minusSeconds(120));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongAudience() {
        String token = encode(
                encoder,
                ISSUER,
                "another-client",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuer() {
        String token = encode(
                encoder,
                "another-issuer",
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private static String encode(
            JwtEncoder selectedEncoder,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("customer@safepay.test")
                .claim("user_id", "101")
                .claim("authorities", List.of("CUSTOMER"))
                .claim("security_version", 0L)
                .build();
        return selectedEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }

    private static JwtSecurityProperties properties(byte seed) {
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, seed);
        return new JwtSecurityProperties(
                ISSUER,
                AUDIENCE,
                Base64.getEncoder().encodeToString(secret),
                Duration.ofMinutes(15));
    }
}
