package com.ofss.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtAccessTokenService implements AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties properties;
    private final Clock clock;

    public JwtAccessTokenService(
            JwtEncoder jwtEncoder,
            JwtSecurityProperties properties,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(SafePayPrincipal principal) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(
                properties.accessTokenValidity());

        List<String> authorities = principal.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(principal.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("user_id", principal.getUserId().toString())
                .claim("authorities", authorities)
                .claim(
                        "security_version",
                        principal.getSecurityVersion())
                .build();

        JwsHeader headers = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String value = jwtEncoder.encode(
                JwtEncoderParameters.from(headers, claims))
                .getTokenValue();

        return new AccessToken(value, expiresAt);
    }
}
