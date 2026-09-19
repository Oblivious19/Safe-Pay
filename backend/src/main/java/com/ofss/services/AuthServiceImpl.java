package com.ofss.services;

import java.util.Objects;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.ofss.dto.auth.AuthTokenResponse;
import com.ofss.dto.auth.LoginRequest;
import com.ofss.security.AccessToken;
import com.ofss.security.AccessTokenService;
import com.ofss.security.RefreshRotationResult;
import com.ofss.security.RefreshRotationStatus;
import com.ofss.security.RefreshSessionResult;
import com.ofss.security.RefreshSessionService;
import com.ofss.security.SafePayPrincipal;
import com.ofss.security.SafePayUserDetailsService;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS =
            "Invalid credentials";

    private final CredentialAuthenticationService credentialService;
    private final LoginSecurityService loginSecurityService;
    private final RefreshSessionService refreshSessionService;
    private final SafePayUserDetailsService userDetailsService;
    private final AccessTokenService accessTokenService;

    public AuthServiceImpl(
            CredentialAuthenticationService credentialService,
            LoginSecurityService loginSecurityService,
            RefreshSessionService refreshSessionService,
            SafePayUserDetailsService userDetailsService,
            AccessTokenService accessTokenService) {
        this.credentialService = credentialService;
        this.loginSecurityService = loginSecurityService;
        this.refreshSessionService = refreshSessionService;
        this.userDetailsService = userDetailsService;
        this.accessTokenService = accessTokenService;
    }

    @Override
    public AuthenticationResult login(
            LoginRequest request,
            String correlationId) {
        Objects.requireNonNull(request, "request is required");

        loginSecurityService.prepareForAuthentication(
                request.loginIdentifier());

        SafePayPrincipal authenticated;
        try {
            authenticated = credentialService.authenticate(request);
        } catch (BadCredentialsException exception) {
            loginSecurityService.recordFailure(
                    request.loginIdentifier(),
                    correlationId);
            throw new BadCredentialsException(
                    INVALID_CREDENTIALS);
        }

        RefreshSessionResult refresh =
                refreshSessionService.issueForLogin(
                        authenticated.getUserId(),
                        correlationId);

        return createResult(refresh);
    }

    @Override
    public AuthenticationResult refresh(
            String refreshToken,
            String correlationId) {
        RefreshRotationResult rotation =
                refreshSessionService.rotate(
                        refreshToken,
                        correlationId);

        if (rotation.status() != RefreshRotationStatus.ROTATED) {
            throw new BadCredentialsException(
                    INVALID_CREDENTIALS);
        }

        return createResult(rotation.session());
    }

    @Override
    public void logout(
            Long userId,
            String refreshToken,
            String correlationId) {
        refreshSessionService.logout(
                userId,
                refreshToken,
                correlationId);
    }

    private AuthenticationResult createResult(
            RefreshSessionResult refresh) {
        SafePayPrincipal principal = userDetailsService.loadByUserId(
                refresh.userId());
        AccessToken accessToken = accessTokenService.issue(principal);
        return new AuthenticationResult(
                AuthTokenResponse.from(accessToken, principal),
                refresh.rawRefreshToken(),
                refresh.expiresAt());
    }
}
