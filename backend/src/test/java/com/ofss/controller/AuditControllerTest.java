package com.ofss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.AuditOutcome;
import com.ofss.beans.RoleName;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.audit.AuditLogResponse;
import com.ofss.dto.audit.TransactionAuditResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AuditQueryService;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long TRANSACTION_ID = 101L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-17T05:30:00Z");

    @Mock private AuditQueryService auditQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuditController(auditQueryService))
                .setControllerAdvice(new GlobalExceptionHandler(
                        Clock.fixed(
                                NOW.toInstant(),
                                ZoneOffset.UTC)))
                .build();
    }

    @Test
    void auditorCanSearchGlobalAuditWithSafeDefaults()
            throws Exception {
        when(auditQueryService.searchGlobalAudit(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20))
                .thenReturn(globalPage());

        mockMvc.perform(as(RoleName.AUDITOR)
                        .with(get("/api/v1/audit-logs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionCode")
                        .value("PAYMENT_RELEASED"))
                .andExpect(jsonPath("$.items[0].idempotencyKey")
                        .doesNotExist());
    }

    @Test
    void globalSearchForwardsTheApprovedFilterSet()
            throws Exception {
        OffsetDateTime from = NOW.minusHours(1);
        OffsetDateTime to = NOW.plusHours(1);
        when(auditQueryService.searchGlobalAudit(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                eq("PAYMENT_RELEASED"),
                eq("SUCCESS"),
                eq("SYSTEM"),
                eq("CORR-101"),
                eq(from),
                eq(to),
                eq(2),
                eq(10)))
                .thenReturn(new PagedResponse<>(
                        List.of(),
                        2,
                        10,
                        21,
                        3,
                        false,
                        true));

        mockMvc.perform(as(RoleName.AUDITOR).with(
                        get("/api/v1/audit-logs")
                                .param("transactionId", "101")
                                .param("actionCode", "PAYMENT_RELEASED")
                                .param("outcome", "SUCCESS")
                                .param("actorType", "SYSTEM")
                                .param("correlationId", "CORR-101")
                                .param("from", from.toString())
                                .param("to", to.toString())
                                .param("page", "2")
                                .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(auditQueryService).searchGlobalAudit(
                USER_ID,
                TRANSACTION_ID,
                "PAYMENT_RELEASED",
                "SUCCESS",
                "SYSTEM",
                "CORR-101",
                from,
                to,
                2,
                10);
    }

    @Test
    void systemAdministratorAloneCannotSearchGlobalAudit()
            throws Exception {
        mockMvc.perform(as(RoleName.SYSTEM_ADMIN)
                        .with(get("/api/v1/audit-logs")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(auditQueryService);
    }

    @Test
    void customerAuthorityIsPassedToOwnedTimelineService()
            throws Exception {
        when(auditQueryService.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.CUSTOMER),
                TRANSACTION_ID,
                0,
                20))
                .thenReturn(timelinePage());

        mockMvc.perform(as(RoleName.CUSTOMER).with(get(
                        "/api/v1/transactions/101/audit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actionCode")
                        .value("PAYMENT_RELEASED"));
    }

    @Test
    void riskOfficerAuthorityIsPassedToReviewTimelineService()
            throws Exception {
        when(auditQueryService.getTransactionTimeline(
                USER_ID,
                Set.of(RoleName.RISK_OFFICER),
                TRANSACTION_ID,
                0,
                20))
                .thenReturn(timelinePage());

        mockMvc.perform(as(RoleName.RISK_OFFICER).with(get(
                        "/api/v1/transactions/101/audit")))
                .andExpect(status().isOk());
    }

    @Test
    void combinedAdministratorUsesExactAuditorAuthority()
            throws Exception {
        when(auditQueryService.searchGlobalAudit(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20))
                .thenReturn(globalPage());

        mockMvc.perform(as(
                        RoleName.RISK_OFFICER,
                        RoleName.SYSTEM_ADMIN,
                        RoleName.AUDITOR)
                        .with(get("/api/v1/audit-logs")))
                .andExpect(status().isOk());
    }

    @Test
    void missingSafePayPrincipalFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(auditQueryService);
    }

    @Test
    void unknownAuthorityCannotGrantAuditAccess() throws Exception {
        SafePayPrincipal principal = principal("UNKNOWN_ROLE");
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .principal(authentication))
                .andExpect(status().isForbidden());

        verifyNoInteractions(auditQueryService);
    }

    private RequestBuilderWithPrincipal as(RoleName... roles) {
        String[] authorities = java.util.Arrays.stream(roles)
                .map(RoleName::name)
                .toArray(String[]::new);
        return new RequestBuilderWithPrincipal(
                principal(authorities));
    }

    private static SafePayPrincipal principal(String... authorities) {
        return new SafePayPrincipal(
                USER_ID,
                "user@example.com",
                "stored-password-hash",
                UserStatus.ACTIVE,
                0L,
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList());
    }

    private static PagedResponse<AuditLogResponse> globalPage() {
        return new PagedResponse<>(
                List.of(new AuditLogResponse(
                        "1",
                        "EVENT-1",
                        null,
                        com.ofss.beans.AuditActorType.SYSTEM,
                        null,
                        "PAYMENT_RELEASED",
                        "PAYMENT_TRANSACTION",
                        "101",
                        "101",
                        "PROTECTED",
                        "RELEASED",
                        AuditOutcome.SUCCESS,
                        null,
                        "CORR-101",
                        null,
                        NOW)),
                0,
                20,
                1,
                1,
                true,
                true);
    }

    private static PagedResponse<TransactionAuditResponse>
            timelinePage() {
        return new PagedResponse<>(
                List.of(new TransactionAuditResponse(
                        "1",
                        null,
                        "PAYMENT_RELEASED",
                        "PROTECTED",
                        "RELEASED",
                        AuditOutcome.SUCCESS,
                        null,
                        null,
                        null,
                        null,
                        NOW)),
                0,
                20,
                1,
                1,
                true,
                true);
    }

    private record RequestBuilderWithPrincipal(
            SafePayPrincipal principal) {

        MockHttpServletRequestBuilder with(
                MockHttpServletRequestBuilder request) {
            TestingAuthenticationToken authentication =
                    new TestingAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities());
            return request.principal(authentication);
        }
    }
}
