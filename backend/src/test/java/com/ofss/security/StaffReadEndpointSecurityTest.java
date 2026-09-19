package com.ofss.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.UserStatus;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.controller.AdminAccountController;
import com.ofss.controller.AdminUserSecurityController;
import com.ofss.controller.CustomerProfileController;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.services.AdminAccountService;
import com.ofss.services.AdminUserSecurityService;
import com.ofss.services.UserService;
import com.ofss.services.SecurityIncidentAuditService;

@SpringJUnitWebConfig(StaffReadEndpointSecurityTest.WebConfiguration.class)
class StaffReadEndpointSecurityTest {
    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final List<String> PATHS = List.of(
            "/api/v1/admin/accounts", "/api/v1/admin/accounts/501",
            "/api/v1/admin/accounts/501/balance", "/api/v1/admin/users",
            "/api/v1/admin/users/101", "/api/v1/users/me",
            "/api/v1/audit/risk-reviews", "/api/v1/audit/risk-reviews/501",
            "/api/v1/audit/transactions", "/api/v1/audit/transactions/101",
            "/api/v1/audit/ledger-postings", "/api/v1/audit/ledger-postings/501",
            "/api/v1/audit/reconciliation/ledger", "/api/v1/audit/reconciliation/reservations",
            "/api/v1/audit/exceptions", "/api/v1/audit/exceptions/501",
            "/api/v1/audit/risk-policies", "/api/v1/audit/risk-policies/AMOUNT_ONLY_V1",
            "/api/v1/admin/operations/stats", "/api/v1/admin/operations/failures");

    @Autowired private WebApplicationContext context;
    @Autowired private AdminAccountController accountController;
    @Autowired private AdminUserSecurityController userController;
    @Autowired private CustomerProfileController profileController;
    @Autowired private com.ofss.controller.AuditEvidenceController auditController;
    @MockitoBean private com.ofss.services.AuditEvidenceService auditService;
    @MockitoBean private com.ofss.services.AdminOperationsService operationsService;
    @MockitoBean private AdminAccountService accountService;
    @MockitoBean private AdminUserSecurityService adminUserService;
    @MockitoBean private UserService userService;
    @MockitoBean private SafePayUserDetailsService userDetailsService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SecurityIncidentAuditService incidentAuditService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CorrelationIdFilter()).apply(springSecurity()).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("paths")
    void requiresAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService, adminUserService, userService, auditService);
    }

    @ParameterizedTest
    @MethodSource("roleMatrix")
    void enforcesExactRoleForEveryNewRoute(String path, String roles, boolean allowed) throws Exception {
        token(UserStatus.ACTIVE, 0L, roles.split(","));
        mvc.perform(get(path).header("Authorization", "Bearer valid-token"))
                .andExpect(allowed ? status().isOk() : status().isForbidden());
        if (!allowed) {
            verifyNoInteractions(operationsService);
            verifyNoInteractions(accountService, adminUserService, userService, auditService);
        } else if (path.equals("/api/v1/admin/operations/stats")) {
            verify(operationsService).getStatistics(101L);
        } else if (path.equals("/api/v1/admin/operations/failures")) {
            verify(operationsService).searchFailures(101L, null, null, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/ledger-postings")) {
            verify(auditService).searchLedgerPostings(101L, null, null, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/ledger-postings/501")) {
            verify(auditService).getLedgerPosting(101L, 501L);
        } else if (path.equals("/api/v1/audit/reconciliation/ledger")) {
            verify(auditService).ledgerReconciliation(101L, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/reconciliation/reservations")) {
            verify(auditService).reservationReconciliation(101L, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/exceptions")) {
            verify(auditService).searchExceptions(101L, null, null, null, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/exceptions/501")) {
            verify(auditService).getException(101L, 501L);
        } else if (path.equals("/api/v1/audit/risk-policies")) {
            verify(auditService).listPolicies(101L, null, 0, 20);
        } else if (path.equals("/api/v1/audit/risk-policies/AMOUNT_ONLY_V1")) {
            verify(auditService).getPolicy(101L, "AMOUNT_ONLY_V1");
        } else if (path.equals("/api/v1/audit/risk-reviews")) {
            verify(auditService).listReviews(101L, com.ofss.beans.RiskReviewStatus.PENDING, 0, 20);
        } else if (path.equals("/api/v1/audit/risk-reviews/501")) {
            verify(auditService).getReview(101L, 501L);
        } else if (path.equals("/api/v1/audit/transactions")) {
            verify(auditService).searchTransactions(101L, null, null, null, null, 0, 20);
        } else if (path.equals("/api/v1/audit/transactions/101")) {
            verify(auditService).getTransactionEvidence(101L, 101L);
        } else if (path.equals("/api/v1/users/me")) {
            verify(userService).getOwnProfile(101L);
        } else if (path.equals("/api/v1/admin/users")) {
            verify(adminUserService).searchUsers(101L, null, null, null, 0, 20);
        } else if (path.equals("/api/v1/admin/users/101")) {
            verify(adminUserService).getUserDetails(101L, 101L);
        } else if (path.endsWith("/balance")) {
            verify(accountService).getBalance(101L, 501L);
        } else if (path.endsWith("/501")) {
            verify(accountService).getAccount(101L, 501L);
        } else {
            verify(accountService).searchAccounts(101L, null, null, null, 0, 20);
        }
    }

    @ParameterizedTest
    @MethodSource("invalidLiveUsers")
    void liveStatusAndVersionInvalidateAccess(UserStatus state, long version) throws Exception {
        token(state, version, "SYSTEM_ADMIN");
        mvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountService);
    }

    @Test
    void methodSecurityProtectsDirectControllerCalls() {
        var principal = principal(UserStatus.ACTIVE, 0, "AUDITOR");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatThrownBy(() -> accountController.getAccount(auth, 501L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userController.getUserDetails(auth, 101L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> profileController.getOwnProfile(auth)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(accountService, adminUserService, userService);
    }

    private void token(UserStatus state, long liveVersion, String... roles) {
        Jwt token = Jwt.withTokenValue("valid-token").header("alg", "HS256")
                .subject("reader@example.invalid").issuedAt(NOW).expiresAt(NOW.plusSeconds(900))
                .claim("user_id", "101").claim("security_version", 0L)
                .claim("authorities", List.of(roles)).build();
        when(jwtDecoder.decode("valid-token")).thenReturn(token);
        when(userDetailsService.loadByUserId(101L)).thenReturn(principal(state, liveVersion, roles));
    }

    @Test
    void auditorCannotPerformOfficerDecisionOrNoteMutations() throws Exception {
        token(UserStatus.ACTIVE, 0L, "AUDITOR");
        for (String action : List.of("approve", "reject", "request-verification", "notes")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/admin/risk-reviews/501/" + action)
                            .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(auditService);
    }

    @Test
    void officerCannotCallAuditorControllerDirectly() {
        var principal = principal(UserStatus.ACTIVE, 0, "RISK_OFFICER");
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThatThrownBy(() -> auditController.getReview(auth, 501L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(auditService);
    }

    private static SafePayPrincipal principal(UserStatus state, long version, String... roles) {
        return new SafePayPrincipal(101L, "reader@example.invalid", "test-hash", state, version,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
    }

    static Stream<String> paths() { return PATHS.stream(); }

    static Stream<Arguments> roleMatrix() {
        return PATHS.stream().flatMap(path -> Stream.of("CUSTOMER", "SYSTEM_ADMIN", "RISK_OFFICER",
                "AUDITOR", "SYSTEM_ADMIN,RISK_OFFICER,AUDITOR").map(roles -> Arguments.of(path, roles,
                        path.equals("/api/v1/users/me") ? roles.equals("CUSTOMER")
                                : path.startsWith("/api/v1/audit/") ? roles.contains("AUDITOR")
                                : roles.contains("SYSTEM_ADMIN"))));
    }

    static Stream<Arguments> invalidLiveUsers() {
        return Stream.of(Arguments.of(UserStatus.LOCKED, 0L), Arguments.of(UserStatus.DISABLED, 0L),
                Arguments.of(UserStatus.ACTIVE, 1L));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AdminAccountController.class, AdminUserSecurityController.class, CustomerProfileController.class,
            com.ofss.controller.AuditEvidenceController.class,
            com.ofss.controller.AdminOperationsController.class,
            SecurityConfig.class, SafePayJwtAuthenticationConverter.class,
            SafePayAuthenticationEntryPoint.class, SafePayAccessDeniedHandler.class,
            SecurityProblemWriter.class, RefreshCookieOriginFilter.class, GlobalExceptionHandler.class})
    static class WebConfiguration {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean ObjectMapper objectMapper() {
            return Jackson2ObjectMapperBuilder.json().mixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        }
        @Bean BrowserSecurityProperties browserProperties() {
            return new BrowserSecurityProperties(List.of("http://localhost:8000"));
        }
        @Bean RefreshCookieProperties refreshCookieProperties() {
            return new RefreshCookieProperties("SAFEPAY_REFRESH", "/api/v1/auth", "Strict", false);
        }
    }
}
