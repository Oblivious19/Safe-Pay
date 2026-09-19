package com.ofss.controller;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.common.CorrelationIdFilter;
import com.ofss.dto.auth.AuthTokenResponse;
import com.ofss.dto.auth.CsrfTokenResponse;
import com.ofss.dto.auth.LoginRequest;
import com.ofss.dto.auth.RegisterUserRequest;
import com.ofss.dto.auth.RegisterUserResponse;
import com.ofss.security.RefreshCookieFactory;
import com.ofss.security.AuthenticatedUser;
import org.springframework.security.web.csrf.CsrfToken;
import com.ofss.services.AuthService;
import com.ofss.services.AuthenticationResult;
import com.ofss.services.UserRegistrationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRegistrationService registrationService;
    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final Clock clock;

    @Autowired
    public AuthController(
            UserRegistrationService registrationService,
            AuthService authService,
            RefreshCookieFactory refreshCookieFactory,
            Clock clock) {

        this.registrationService = registrationService;
        this.authService = authService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.clock = clock;
    }

    AuthController(UserRegistrationService registrationService) {
        this(registrationService, null, null, null);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request) {

        RegisterUserResponse response =
                registrationService.registerCustomer(request);

        URI location = URI.create(
                "/api/v1/users/" + response.userId());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthenticationResult result = authService.login(
                request,
                correlationId(servletRequest));
        return withRefreshCookie(result);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(
                token.getHeaderName(),
                token.getParameterName(),
                token.getToken());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(
                    name = "SAFEPAY_REFRESH",
                    required = false)
                    String refreshToken,
            HttpServletRequest servletRequest) {
        AuthenticationResult result = authService.refresh(
                refreshToken,
                correlationId(servletRequest));
        return withRefreshCookie(result);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            @CookieValue(
                    name = "SAFEPAY_REFRESH",
                    required = false)
                    String refreshToken,
            HttpServletRequest servletRequest) {
        authService.logout(
                authenticatedUserId(authentication),
                refreshToken,
                correlationId(servletRequest));
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.clear().toString())
                .build();
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(
            AuthenticationResult result) {
        OffsetDateTime now = OffsetDateTime
                .now(clock)
                .withOffsetSameInstant(ZoneOffset.UTC);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.create(
                                result.refreshToken(),
                                result.refreshExpiresAt(),
                                now).toString())
                .body(result.response());
    }

    private static String correlationId(
            HttpServletRequest request) {
        Object value = request.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String correlationId
                && !correlationId.isBlank()) {
            return correlationId;
        }
        throw new IllegalStateException(
                "Request correlation ID is unavailable");
    }

    private static Long authenticatedUserId(
            Authentication authentication) {
        return AuthenticatedUser.userId(authentication);
    }
}
