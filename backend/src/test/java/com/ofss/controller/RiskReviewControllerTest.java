package com.ofss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.RiskReviewStatus;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionState;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.riskreview.RequiredRiskReviewDecisionRequest;
import com.ofss.dto.riskreview.RiskReviewDecisionRequest;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.RequestFingerprintService;
import com.ofss.services.RiskReviewService;

@ExtendWith(MockitoExtension.class)
class RiskReviewControllerTest {

    private static final Long OFFICER_ID = 22L;
    private static final Long REVIEW_ID = 501L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T18:00:00Z");

    @Mock private RiskReviewService riskReviewService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RequestFingerprintService fingerprintService;
    @Mock private Authentication authentication;
    @Mock private SafePayPrincipal principal;

    private MockMvc mockMvc;
    private boolean replay;

    @BeforeEach
    void setUp() {
        replay = false;

        lenient().when(fingerprintService.fingerprint(
                any(IdempotencyOperation.class),
                anyString(),
                any()))
                .thenReturn("a".repeat(64));

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<IdempotencyExecutionResult<Object>> action =
                    invocation.getArgument(6);
            IdempotencyExecutionResult<Object> result = action.get();

            return replay
                    ? IdempotencyExecutionResult.replayed(
                            result.httpStatus(),
                            result.responseBody(),
                            result.transactionId())
                    : result;
        }).when(idempotencyService).execute(
                anyLong(),
                any(IdempotencyOperation.class),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiskReviewController(
                        riskReviewService,
                        idempotencyService,
                        fingerprintService))
                .setControllerAdvice(new GlobalExceptionHandler(
                        Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void categoryAndOldestSelectionUseExistingQueueRoute() throws Exception {
        when(riskReviewService.listPending(OFFICER_ID, com.ofss.beans.PaymentCategory.MEDICAL, "OLDEST", 0, 20))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true, true));
        mockMvc.perform(asOfficer(get("/api/v1/admin/risk-reviews"))
                        .param("category", "MEDICAL").param("sort", "OLDEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(riskReviewService).listPending(OFFICER_ID, com.ofss.beans.PaymentCategory.MEDICAL, "OLDEST", 0, 20);
    }

    @Test
    void listsPendingQueueWithFixedServerOrderingContract()
            throws Exception {

        when(riskReviewService.listPending(OFFICER_ID, 0, 20))
                .thenReturn(new PagedResponse<>(
                        List.of(summary()),
                        0,
                        20,
                        1,
                        1,
                        true,
                        true));

        mockMvc.perform(asOfficer(get(
                        "/api/v1/admin/risk-reviews")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reviewId")
                        .value("501"))
                .andExpect(jsonPath("$.items[0].maskedSourceAccountNumber")
                        .value("************3456"));
    }

    @Test
    void retrievesTerminalOrPendingDetailForOfficer()
            throws Exception {

        when(riskReviewService.getReview(OFFICER_ID, REVIEW_ID))
                .thenReturn(detail());

        mockMvc.perform(asOfficer(get(
                        "/api/v1/admin/risk-reviews/501")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.reviewId").value("501"));
    }

    @Test
    void approvesWithOptionalReasonAndIdempotentAuditContext()
            throws Exception {

        when(riskReviewService.approve(
                OFFICER_ID,
                REVIEW_ID,
                null,
                "CORR-REVIEW",
                "review-key"))
                .thenReturn(detail());

        mockMvc.perform(asOfficer(post(
                        "/api/v1/admin/risk-reviews/501/approve")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.review.reviewId").value("501"));

        verify(fingerprintService).fingerprint(
                IdempotencyOperation.RISK_REVIEW_APPROVE,
                "/api/v1/admin/risk-reviews/501/approve",
                new RiskReviewDecisionRequest(null));
    }

    @Test
    void rejectsWithMandatoryReasonAndDedicatedScope()
            throws Exception {

        when(riskReviewService.reject(
                OFFICER_ID,
                REVIEW_ID,
                "Confirmed mule pattern",
                "CORR-REVIEW",
                "review-key"))
                .thenReturn(detail());

        mockMvc.perform(asOfficer(post(
                        "/api/v1/admin/risk-reviews/501/reject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Confirmed mule pattern\"}"))
                .andExpect(status().isOk());

        verify(fingerprintService).fingerprint(
                IdempotencyOperation.RISK_REVIEW_REJECT,
                "/api/v1/admin/risk-reviews/501/reject",
                new RequiredRiskReviewDecisionRequest(
                        "Confirmed mule pattern"));
    }

    @Test
    void requestsFreshVerificationWithoutChangingEndpointVocabulary()
            throws Exception {

        when(riskReviewService.requestReverification(
                OFFICER_ID,
                REVIEW_ID,
                "Confirm beneficiary ownership",
                "CORR-REVIEW",
                "review-key"))
                .thenReturn(detail());

        mockMvc.perform(asOfficer(post(
                        "/api/v1/admin/risk-reviews/501/request-verification"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"reason\":\"Confirm beneficiary ownership\"}"))
                .andExpect(status().isOk());

        verify(riskReviewService).requestReverification(
                OFFICER_ID,
                REVIEW_ID,
                "Confirm beneficiary ownership",
                "CORR-REVIEW",
                "review-key");
    }

    @Test
    void addsInternalNoteButReturnsOnlySafeAcknowledgement()
            throws Exception {

        when(riskReviewService.addNote(
                OFFICER_ID,
                REVIEW_ID,
                "Check device history",
                "CORR-REVIEW",
                "review-key"))
                .thenReturn(new RiskReviewNoteResponse(
                        "501",
                        "101",
                        "RISK-REVIEW-NOTE-1",
                        NOW));

        mockMvc.perform(asOfficer(post(
                        "/api/v1/admin/risk-reviews/501/notes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Check device history\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value("501"))
                .andExpect(jsonPath("$.eventReference")
                        .value("RISK-REVIEW-NOTE-1"))
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    void replayResponseIsExplicitlyMarked()
            throws Exception {

        replay = true;
        when(riskReviewService.approve(
                eq(OFFICER_ID),
                eq(REVIEW_ID),
                any(),
                eq("CORR-REVIEW"),
                eq("review-key")))
                .thenReturn(detail());

        mockMvc.perform(asOfficer(post(
                        "/api/v1/admin/risk-reviews/501/approve")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                        "true"));
    }

    @Test
    void stateChangingCallRequiresExactlyOneIdempotencyKey()
            throws Exception {

        mockMvc.perform(asOfficerWithoutKey(post(
                        "/api/v1/admin/risk-reviews/501/approve")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        verifyNoInteractions(riskReviewService);
    }

    @Test
    void duplicateIdempotencyHeadersAreRejected()
            throws Exception {

        mockMvc.perform(asOfficerWithoutKey(post(
                        "/api/v1/admin/risk-reviews/501/approve"))
                        .header(
                                IdempotencyHeaders.IDEMPOTENCY_KEY,
                                "first",
                                "second"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(riskReviewService);
    }

    @Test
    void systemAdminWithoutRiskOfficerIsForbidden()
            throws Exception {

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAuthorities()).thenReturn(Set.of(
                new SimpleGrantedAuthority("SYSTEM_ADMIN")));

        mockMvc.perform(get("/api/v1/admin/risk-reviews")
                        .principal(authentication))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(riskReviewService);
    }

    @Test
    void blankRejectReasonFailsValidationBeforeService()
            throws Exception {

        mockMvc.perform(validationRequest(post(
                        "/api/v1/admin/risk-reviews/501/reject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(riskReviewService);
    }

    @Test
    void blankInternalNoteFailsValidationBeforeService()
            throws Exception {

        mockMvc.perform(validationRequest(post(
                        "/api/v1/admin/risk-reviews/501/notes"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(riskReviewService);
    }

    @Test
    void missingSafePayPrincipalFailsClosed()
            throws Exception {

        mockMvc.perform(get("/api/v1/admin/risk-reviews"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(riskReviewService);
    }

    private MockHttpServletRequestBuilder asOfficer(
            MockHttpServletRequestBuilder request) {

        return asOfficerWithoutKey(request)
                .header(
                        IdempotencyHeaders.IDEMPOTENCY_KEY,
                        "review-key");
    }

    private MockHttpServletRequestBuilder asOfficerWithoutKey(
            MockHttpServletRequestBuilder request) {

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(OFFICER_ID);
        when(principal.getAuthorities()).thenReturn(Set.of(
                new SimpleGrantedAuthority("RISK_OFFICER")));

        return request
                .principal(authentication)
                .header(
                        CorrelationIdFilter.HEADER_NAME,
                        "CORR-REVIEW");
    }

    private MockHttpServletRequestBuilder validationRequest(
            MockHttpServletRequestBuilder request) {

        return request
                .principal(authentication)
                .header(
                        CorrelationIdFilter.HEADER_NAME,
                        "CORR-REVIEW")
                .header(
                        IdempotencyHeaders.IDEMPOTENCY_KEY,
                        "review-key");
    }

    private static RiskReviewDetailResponse detail() {
        return new RiskReviewDetailResponse(
                summary(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L);
    }

    private static RiskReviewSummaryResponse summary() {
        return new RiskReviewSummaryResponse(
                "501",
                1,
                RiskReviewStatus.PENDING,
                "101",
                "SP-101",
                TransactionState.PENDING_RISK_REVIEW,
                "11",
                "Customer One",
                "c*******@example.com",
                "************3456",
                "81",
                "Vendor One",
                new BigDecimal("125000.00"),
                CurrencyCode.INR,
                RiskTier.VERY_HIGH,
                "V1",
                "Manual review required",
                NOW.minusMinutes(1),
                NOW.minusSeconds(30),
                NOW);
    }
}
