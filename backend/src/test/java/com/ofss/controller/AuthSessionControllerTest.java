package com.ofss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.dto.auth.AuthTokenResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.RefreshCookieFactory;
import com.ofss.security.RefreshCookieProperties;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AuthService;
import com.ofss.services.AuthenticationResult;
import com.ofss.services.UserRegistrationService;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class AuthSessionControllerTest {

    private static final Instant NOW =
            Instant.parse("2026-09-17T10:00:00Z");
    private static final String CORRELATION = "auth-controller-test";

    @Mock private UserRegistrationService registrationService;
    @Mock private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RefreshCookieFactory cookieFactory = new RefreshCookieFactory(
                new RefreshCookieProperties(
                        "SAFEPAY_REFRESH",
                        "/api/v1/auth",
                        "Strict",
                        false));
        AuthController controller = new AuthController(
                registrationService,
                authService,
                cookieFactory,
                clock);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(clock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie()
            throws Exception {
        when(authService.login(any(), eq(CORRELATION)))
                .thenReturn(result());

        mockMvc.perform(withCorrelation(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginIdentifier":"customer@example.com",
                                 "password":"SafePay@2026"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(
                                        "SAFEPAY_REFRESH=refresh-token"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString(
                                        "SameSite=Strict"))));
    }

    @Test
    void refreshRotatesCookieWithoutRequestBody() throws Exception {
        when(authService.refresh("old-refresh", CORRELATION))
                .thenReturn(result());

        mockMvc.perform(withCorrelation(post("/api/v1/auth/refresh"))
                        .cookie(new Cookie(
                                "SAFEPAY_REFRESH",
                                "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString(
                                "SAFEPAY_REFRESH=refresh-token")));
    }

    @Test
    void missingRefreshCookieReturnsSafeUnauthorized()
            throws Exception {
        when(authService.refresh(null, CORRELATION))
                .thenThrow(new BadCredentialsException(
                        "Invalid credentials"));

        mockMvc.perform(withCorrelation(post("/api/v1/auth/refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("AUTHENTICATION_FAILED"));
    }

    @Test
    void logoutRevokesOwnedSessionAndClearsCookie()
            throws Exception {
        mockMvc.perform(authenticated(
                        withCorrelation(post("/api/v1/auth/logout")))
                        .cookie(new Cookie(
                                "SAFEPAY_REFRESH",
                                "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString(
                                        "SAFEPAY_REFRESH="),
                                org.hamcrest.Matchers.containsString(
                                        "Max-Age=0"))));

        verify(authService).logout(
                101L,
                "refresh-token",
                CORRELATION);
    }

    @Test
    void logoutNeverAcceptsSubmittedUserIdentifier()
            throws Exception {
        mockMvc.perform(authenticated(
                        withCorrelation(post("/api/v1/auth/logout")))
                        .cookie(new Cookie(
                                "SAFEPAY_REFRESH",
                                "refresh-token"))
                        .param("userId", "999"))
                .andExpect(status().isNoContent());
        verify(authService).logout(
                101L,
                "refresh-token",
                CORRELATION);
    }

    private static MockHttpServletRequestBuilder withCorrelation(
            MockHttpServletRequestBuilder builder) {
        return builder.header(
                CorrelationIdFilter.HEADER_NAME,
                CORRELATION);
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder builder) {
        SafePayPrincipal principal = new SafePayPrincipal(
                101L,
                "customer@example.com",
                "hash",
                UserStatus.ACTIVE,
                0L,
                Set.of(new SimpleGrantedAuthority("CUSTOMER")));
        return builder.principal(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()));
    }

    private static AuthenticationResult result() {
        return new AuthenticationResult(
                new AuthTokenResponse(
                        "access-token",
                        "Bearer",
                        NOW.plusSeconds(900),
                        "101",
                        List.of("CUSTOMER")),
                "refresh-token",
                OffsetDateTime.ofInstant(
                        NOW.plusSeconds(7 * 24 * 60 * 60),
                        ZoneOffset.UTC));
    }
}
