package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ofss.beans.UserStatus;
import com.ofss.dto.auth.LoginRequest;
import com.ofss.security.AccessToken;
import com.ofss.security.AccessTokenService;
import com.ofss.security.RefreshRotationResult;
import com.ofss.security.RefreshSessionResult;
import com.ofss.security.RefreshSessionService;
import com.ofss.security.SafePayPrincipal;
import com.ofss.security.SafePayUserDetailsService;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String CORRELATION = "auth-service-test";
    private static final OffsetDateTime REFRESH_EXPIRY =
            OffsetDateTime.parse("2026-09-24T10:00:00Z");

    @Mock private CredentialAuthenticationService credentialService;
    @Mock private LoginSecurityService loginSecurityService;
    @Mock private RefreshSessionService refreshSessionService;
    @Mock private SafePayUserDetailsService userDetailsService;
    @Mock private AccessTokenService accessTokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                credentialService,
                loginSecurityService,
                refreshSessionService,
                userDetailsService,
                accessTokenService);
    }

    @Test
    void loginPreparesStateAndIssuesBothTokenTypes() {
        LoginRequest request = request();
        SafePayPrincipal principal = principal();
        prepareSuccessfulResult(principal);
        when(refreshSessionService.issueForLogin(101L, CORRELATION))
                .thenReturn(refreshResult());
        when(credentialService.authenticate(request)).thenReturn(principal);

        AuthenticationResult result = service.login(request, CORRELATION);

        verify(loginSecurityService)
                .prepareForAuthentication("customer@example.com");
        verify(refreshSessionService)
                .issueForLogin(101L, CORRELATION);
        assertThat(result.response().accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void failedLoginIsRecordedBeforeGenericFailureReturns() {
        LoginRequest request = request();
        when(credentialService.authenticate(request))
                .thenThrow(new BadCredentialsException("internal detail"));

        assertThatThrownBy(() -> service.login(request, CORRELATION))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginSecurityService).recordFailure(
                "customer@example.com",
                CORRELATION);
        verify(refreshSessionService, never())
                .issueForLogin(101L, CORRELATION);
    }

    @Test
    void refreshRotatesSessionBeforeIssuingAccessToken() {
        SafePayPrincipal principal = principal();
        prepareSuccessfulResult(principal);
        when(refreshSessionService.rotate("old-refresh", CORRELATION))
                .thenReturn(RefreshRotationResult.rotated(
                        refreshResult()));

        AuthenticationResult result = service.refresh(
                "old-refresh",
                CORRELATION);

        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void invalidRefreshNeverIssuesAccessToken() {
        when(refreshSessionService.rotate("invalid", CORRELATION))
                .thenReturn(RefreshRotationResult.invalid());

        assertThatThrownBy(() -> service.refresh("invalid", CORRELATION))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(accessTokenService, never()).issue(any());
    }

    @Test
    void replayDetectionReturnsSameGenericClientFailure() {
        when(refreshSessionService.rotate("replayed", CORRELATION))
                .thenReturn(RefreshRotationResult.replayDetected());

        assertThatThrownBy(() -> service.refresh("replayed", CORRELATION))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void logoutDelegatesOwnedSessionRevocation() {
        service.logout(101L, "refresh", CORRELATION);
        verify(refreshSessionService).logout(
                101L,
                "refresh",
                CORRELATION);
    }

    private void prepareSuccessfulResult(SafePayPrincipal principal) {
        when(userDetailsService.loadByUserId(101L)).thenReturn(principal);
        when(accessTokenService.issue(principal)).thenReturn(
                new AccessToken(
                        "access",
                        Instant.parse("2026-09-17T10:15:00Z")));
    }

    private static RefreshSessionResult refreshResult() {
        return new RefreshSessionResult(
                101L,
                "refresh",
                REFRESH_EXPIRY);
    }

    private static LoginRequest request() {
        return new LoginRequest(
                "customer@example.com",
                "SafePay@2026");
    }

    private static SafePayPrincipal principal() {
        return new SafePayPrincipal(
                101L,
                "customer@example.com",
                "password-hash",
                UserStatus.ACTIVE,
                0L,
                Set.of(new SimpleGrantedAuthority("CUSTOMER")));
    }
}
