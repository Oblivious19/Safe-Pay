package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.ofss.beans.UserStatus;

@ExtendWith(MockitoExtension.class)
class SafePayJwtAuthenticationConverterTest {

    @Mock private SafePayUserDetailsService userDetailsService;

    private SafePayJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SafePayJwtAuthenticationConverter(
                userDetailsService);
    }

    @Test
    void authenticatesOnlyAgainstCurrentPrincipalState() {
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(UserStatus.ACTIVE, 3L));
        AbstractAuthenticationToken authentication =
                converter.convert(jwt("101", "customer@example.com", 3L,
                        List.of("CUSTOMER")));
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal())
                .isInstanceOf(SafePayPrincipal.class);
    }

    @Test
    void rejectsSecurityVersionMismatch() {
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(UserStatus.ACTIVE, 4L));
        assertInvalid(jwt("101", "customer@example.com", 3L,
                List.of("CUSTOMER")));
    }

    @Test
    void rejectsAuthoritySetMismatch() {
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(UserStatus.ACTIVE, 3L));
        assertInvalid(jwt("101", "customer@example.com", 3L,
                List.of("CUSTOMER", "AUDITOR")));
    }

    @Test
    void rejectsSubjectMismatch() {
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(UserStatus.ACTIVE, 3L));
        assertInvalid(jwt("101", "attacker@example.com", 3L,
                List.of("CUSTOMER")));
    }

    @Test
    void rejectsLockedCurrentUser() {
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(UserStatus.LOCKED, 3L));
        assertInvalid(jwt("101", "customer@example.com", 3L,
                List.of("CUSTOMER")));
    }

    @Test
    void rejectsMalformedUserIdentifierClaimBeforeLookup() {
        assertInvalid(jwt("not-a-number", "customer@example.com", 3L,
                List.of("CUSTOMER")));
    }

    private void assertInvalid(Jwt jwt) {
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid access token");
    }

    private static Jwt jwt(
            String userId,
            String subject,
            long securityVersion,
            List<String> authorities) {
        Instant issuedAt = Instant.parse("2026-09-17T09:00:00Z");
        return new Jwt(
                "encoded",
                issuedAt,
                issuedAt.plusSeconds(900),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", subject,
                        "user_id", userId,
                        "security_version", securityVersion,
                        "authorities", authorities));
    }

    private static SafePayPrincipal principal(
            UserStatus status,
            long securityVersion) {
        return new SafePayPrincipal(
                101L,
                "customer@example.com",
                "password-hash",
                status,
                securityVersion,
                Set.of(new SimpleGrantedAuthority("CUSTOMER")));
    }
}
