package com.ofss.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.TransactionState;
import com.ofss.beans.UserStatus;
import com.ofss.common.api.PagedResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.AuditEvidenceService;

class AuditEvidenceControllerTest {
    @Test
    void reportingListsBindDefaultsAndDetailsUsePrincipal() throws Exception {
        for (String path : List.of("ledger-postings", "reconciliation/ledger", "reconciliation/reservations",
                "exceptions", "risk-policies", "ledger-postings/7", "exceptions/8", "risk-policies/OLD")) {
            mvc.perform(get("/api/v1/audit/" + path).principal(auth)).andExpect(status().isOk());
        }
        verify(service).searchLedgerPostings(10L, null, null, null, null, 0, 20);
        verify(service).ledgerReconciliation(10L, null, null, 0, 20);
        verify(service).reservationReconciliation(10L, null, null, 0, 20);
        verify(service).searchExceptions(10L, null, null, null, null, null, 0, 20);
        verify(service).listPolicies(10L, null, 0, 20);
        verify(service).getLedgerPosting(10L, 7L);
        verify(service).getException(10L, 8L);
        verify(service).getPolicy(10L, "OLD");
    }

    @Test
    void reportingFiltersAreTypedAndInvalidEnumsReturn400() throws Exception {
        mvc.perform(get("/api/v1/audit/exceptions").principal(auth).param("processingStage", "SETTLEMENT")
                .param("status", "RESOLVED").param("transactionId", "77")).andExpect(status().isOk());
        verify(service).searchExceptions(10L, 77L, com.ofss.beans.TransactionProcessingStage.SETTLEMENT,
                com.ofss.beans.TransactionExceptionStatus.RESOLVED, null, null, 0, 20);
        mvc.perform(get("/api/v1/audit/ledger-postings").principal(auth).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit/risk-policies").principal(auth).param("status", "RETIRED"))
                .andExpect(status().isOk());
        verify(service).listPolicies(10L, com.ofss.beans.RiskPolicyStatus.RETIRED, 0, 20);
    }
    private final AuditEvidenceService service = mock(AuditEvidenceService.class);
    private MockMvc mvc;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        var principal = new SafePayPrincipal(10L, "auditor@example.invalid", "test-hash", UserStatus.ACTIVE,
                0L, List.of(new SimpleGrantedAuthority("AUDITOR")));
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        mvc = MockMvcBuilders.standaloneSetup(new AuditEvidenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC())).build();
    }

    @Test
    void defaultReviewStatusAndHistoricalFilterUseAuthenticatedAuditor() throws Exception {
        when(service.listReviews(10L, RiskReviewStatus.PENDING, 0, 20))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true, true));
        mvc.perform(get("/api/v1/audit/risk-reviews").principal(auth).param("auditorId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(service).listReviews(10L, RiskReviewStatus.PENDING, 0, 20);
        mvc.perform(get("/api/v1/audit/risk-reviews").principal(auth).param("status", "REJECTED"))
                .andExpect(status().isOk());
        verify(service).listReviews(10L, RiskReviewStatus.REJECTED, 0, 20);
    }

    @Test
    void transactionFiltersAndDetailIdsAreBoundWithoutRoleSubstitution() throws Exception {
        var from = OffsetDateTime.parse("2026-09-19T10:00:00Z");
        mvc.perform(get("/api/v1/audit/transactions").principal(auth).param("customerId", "99")
                        .param("state", "CREATED").param("from", from.toString()).param("to", from.plusHours(1).toString()))
                .andExpect(status().isOk());
        verify(service).searchTransactions(10L, 99L, TransactionState.CREATED, from, from.plusHours(1), 0, 20);
        mvc.perform(get("/api/v1/audit/risk-reviews/501").principal(auth)).andExpect(status().isOk());
        verify(service).getReview(10L, 501L);
        mvc.perform(get("/api/v1/audit/transactions/101").principal(auth)).andExpect(status().isOk());
        verify(service).getTransactionEvidence(10L, 101L);
    }

    @Test
    void invalidEnumsDatesAndIdsReturn400BeforeService() throws Exception {
        mvc.perform(get("/api/v1/audit/risk-reviews").principal(auth).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit/transactions").principal(auth).param("from", "yesterday"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit/transactions/invalid").principal(auth)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
