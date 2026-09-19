package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.AccountStatus;
import com.ofss.beans.AccountType;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.controller.AccountController;
import com.ofss.dto.account.AccountBalanceResponse;
import com.ofss.dto.account.AccountSummaryResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.services.AccountService;
import com.ofss.services.SecurityIncidentAuditService;

/**
 * Real HTTP filter chain, JWT principal conversion and method security.
 * Only token decoding, user lookup, account reads and audit persistence are
 * mocked. This isolated web context starts no Oracle, Flyway or schedulers.
 */
@SpringJUnitWebConfig(AccountEndpointSecurityTest.WebConfiguration.class)
class AccountEndpointSecurityTest {

    private static final List<String> PATHS = List.of(
            "/api/v1/accounts", "/api/v1/accounts/501", "/api/v1/accounts/501/balance");
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Autowired private WebApplicationContext context;
    @Autowired private AccountController controller;
    @MockitoBean private AccountService accountService;
    @MockitoBean private SafePayUserDetailsService userDetailsService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SecurityIncidentAuditService incidentAuditService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CorrelationIdFilter())
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("accountPaths")
    void missingBearerTokenReturns401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(accountService, jwtDecoder, userDetailsService);
    }

    @ParameterizedTest
    @MethodSource("accountPaths")
    void rejectedBearerTokenReturns401(String path) throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid test token"));
        mockMvc.perform(get(path).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(accountService, userDetailsService);
    }

    @ParameterizedTest
    @MethodSource("staffRequests")
    void staffWithoutCustomerAuthorityCannotReadAccounts(
            String path, String roles) throws Exception {
        configureToken(UserStatus.ACTIVE, 0L, roles.split(","));
        mockMvc.perform(get(path).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        verifyNoInteractions(accountService);
    }

    @ParameterizedTest
    @MethodSource("accountPaths")
    void customerCanReadWithoutCsrfOrIdempotencyHeaders(String path) throws Exception {
        configureToken(UserStatus.ACTIVE, 0L, "CUSTOMER");
        AccountSummaryResponse summary = new AccountSummaryResponse(
                "501", "********9012", AccountType.SAVINGS,
                "SafePay Test Bank", "ABCD0123456", CurrencyCode.INR, AccountStatus.ACTIVE);
        if (path.endsWith("/balance")) {
            when(accountService.getOwnedBalance(101L, 501L)).thenReturn(
                    new AccountBalanceResponse("501", "********9012", CurrencyCode.INR,
                            "10000.00", "2500.00", "7500.00"));
        } else if (path.endsWith("/501")) {
            when(accountService.getOwnedAccount(101L, 501L)).thenReturn(summary);
        } else {
            when(accountService.listOwnedAccounts(101L)).thenReturn(List.of(summary));
        }

        mockMvc.perform(get(path)
                        .param("ownerUserId", "999")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        if (path.endsWith("/balance")) {
            verify(accountService).getOwnedBalance(101L, 501L);
        } else if (path.endsWith("/501")) {
            verify(accountService).getOwnedAccount(101L, 501L);
        } else {
            verify(accountService).listOwnedAccounts(101L);
        }
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"LOCKED", "DISABLED"})
    void currentUserStatusInvalidatesAccountAccess(UserStatus status) throws Exception {
        configureToken(status, 0L, "CUSTOMER");
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void staleSecurityVersionCannotReadAccounts() throws Exception {
        configureToken(UserStatus.ACTIVE, 1L, "CUSTOMER");
        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void methodSecurityAlsoRejectsStaffOutsideHttpRouting() {
        SafePayPrincipal principal = principal(UserStatus.ACTIVE, 0L, "SYSTEM_ADMIN");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> controller.list(authentication))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.get(authentication, 501L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getBalance(authentication, 501L))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(accountService);
    }

    private void configureToken(UserStatus status, long liveVersion, String... roles) {
        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("customer@example.com")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
                .claim("user_id", "101")
                .claim("security_version", 0L)
                .claim("authorities", List.of(roles))
                .build();
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
        when(userDetailsService.loadByUserId(101L))
                .thenReturn(principal(status, liveVersion, roles));
    }

    private static SafePayPrincipal principal(
            UserStatus status, long version, String... roles) {
        return new SafePayPrincipal(101L, "customer@example.com", "unused-test-hash",
                status, version,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    static Stream<String> accountPaths() {
        return PATHS.stream();
    }

    static Stream<Arguments> staffRequests() {
        return PATHS.stream().flatMap(path -> Stream.of(
                        "SYSTEM_ADMIN", "RISK_OFFICER", "AUDITOR",
                        "SYSTEM_ADMIN,RISK_OFFICER,AUDITOR")
                .map(roles -> Arguments.of(path, roles)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AccountController.class, SecurityConfig.class,
            SafePayJwtAuthenticationConverter.class,
            SafePayAuthenticationEntryPoint.class, SafePayAccessDeniedHandler.class,
            SecurityProblemWriter.class, RefreshCookieOriginFilter.class,
            GlobalExceptionHandler.class})
    static class WebConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json()
                    .mixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                    .build();
        }

        @Bean
        BrowserSecurityProperties browserProperties() {
            return new BrowserSecurityProperties(List.of("http://localhost:8000"));
        }

        @Bean
        RefreshCookieProperties refreshCookieProperties() {
            return new RefreshCookieProperties(
                    "SAFEPAY_REFRESH", "/api/v1/auth", "Strict", false);
        }
    }
}
