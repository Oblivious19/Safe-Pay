package com.ofss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.OtpChallengeStatus;
import com.ofss.beans.TransactionState;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.dto.otp.OtpChallengeResponse;
import com.ofss.dto.otp.OtpVerificationResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.OtpChallengeResult;
import com.ofss.services.OtpService;
import com.ofss.services.OtpVerificationResult;
import com.ofss.services.OperationContext;
import com.ofss.services.RequestFingerprintService;

@ExtendWith(MockitoExtension.class)
class VerificationControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long TRANSACTION_ID = 1001L;
    private static final Long CHALLENGE_ID = 501L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-16T15:00:00Z");

    @Mock private OtpService otpService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RequestFingerprintService fingerprintService;
    @Mock private Authentication authentication;
    @Mock private SafePayPrincipal principal;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(fingerprintService.fingerprint(
                any(IdempotencyOperation.class),
                anyString(),
                any()))
                .thenReturn("a".repeat(64));

        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(2);
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "idempotencyKey is required");
            }

            @SuppressWarnings("unchecked")
            Supplier<IdempotencyExecutionResult<Object>> action =
                    invocation.getArgument(6);
            return action.get();
        }).when(idempotencyService).execute(
                anyLong(),
                any(IdempotencyOperation.class),
                any(),
                anyString(),
                anyString(),
                any(),
                any());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new VerificationController(
                        otpService,
                        idempotencyService,
                        fingerprintService))
                .setControllerAdvice(new GlobalExceptionHandler(
                        Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void issuesChallengeWithOnlySafeDeliveryMetadata()
            throws Exception {

        when(otpService.issue(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenReturn(OtpChallengeResult.accepted(
                        challengeResponse()));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").value("501"))
                .andExpect(jsonPath("$.maskedDestination")
                        .value("c*******@example.com"))
                .andExpect(jsonPath("$.remainingIssues").value(2))
                .andExpect(jsonPath("$.otp").doesNotExist())
                .andExpect(jsonPath("$.recipientEmail").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist());
    }

    @Test
    void resendUsesItsOwnCanonicalIdempotencyScope()
            throws Exception {

        when(otpService.resend(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenReturn(OtpChallengeResult.accepted(
                        challengeResponse()));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp/resend")))
                .andExpect(status().isAccepted());

        verify(fingerprintService).fingerprint(
                IdempotencyOperation.OTP_RESEND,
                "/api/v1/transactions/1001/otp/resend",
                null);
    }

    @Test
    void verifiesChallengeAndReturnsPendingReviewState()
            throws Exception {

        when(otpService.verify(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                eq(CHALLENGE_ID),
                eq("123456"),
                any(OperationContext.class)))
                .thenReturn(OtpVerificationResult.verified(
                        verificationResponse(true, 3)));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": 501,
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.transactionState")
                        .value("PENDING_RISK_REVIEW"))
                .andExpect(jsonPath("$.otp").doesNotExist());
    }

    @Test
    void returnsSafeUnprocessableResponseForIncorrectOtp()
            throws Exception {

        when(otpService.verify(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                eq(CHALLENGE_ID),
                eq("654321"),
                any(OperationContext.class)))
                .thenReturn(OtpVerificationResult.rejected(
                        verificationResponse(false, 2),
                        "OTP_INCORRECT",
                        "The verification code is incorrect"));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp/verify"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": 501,
                                  "otp": "654321"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value("OTP_INCORRECT"))
                .andExpect(jsonPath("$.remainingAttempts").value(2))
                .andExpect(jsonPath("$.otp").doesNotExist());
    }

    @Test
    void exposesIssueLimitExhaustionAsSafeBusinessFailure()
            throws Exception {

        when(otpService.resend(
                eq(USER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenReturn(OtpChallengeResult.rejected(
                        cancelledChallengeResponse(),
                        "OTP_ISSUE_LIMIT_EXHAUSTED",
                        "OTP issue limit has been exhausted"));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp/resend")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value("OTP_ISSUE_LIMIT_EXHAUSTED"));
    }

    @Test
    void rejectsMultipleIdempotencyKeysBeforeOtpEffect()
            throws Exception {

        mockMvc.perform(asAuthenticatedCustomer(post(
                        "/api/v1/transactions/1001/otp"))
                        .header(
                                IdempotencyHeaders.IDEMPOTENCY_KEY,
                                "first",
                                "second"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        verifyNoInteractions(otpService);
    }

    @Test
    void rejectsMalformedOtpBeforeCallingService()
            throws Exception {

        mockMvc.perform(post(
                        "/api/v1/transactions/1001/otp/verify")
                        .header(
                                IdempotencyHeaders.IDEMPOTENCY_KEY,
                                "otp-idempotency-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "challengeId": 501,
                                  "otp": "12A456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(otpService);
    }

    @Test
    void returnsReplayHeaderWithoutRepeatingOtpEffect()
            throws Exception {

        doAnswer(invocation -> IdempotencyExecutionResult.replayed(
                202,
                OtpChallengeResult.accepted(challengeResponse()),
                TRANSACTION_ID))
                .when(idempotencyService)
                .execute(
                        eq(USER_ID),
                        eq(IdempotencyOperation.OTP_ISSUE),
                        eq("otp-idempotency-key"),
                        anyString(),
                        anyString(),
                        eq(OtpChallengeResult.class),
                        any());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/otp")))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                        "true"));

        verifyNoInteractions(otpService);
    }

    @Test
    void failsClosedWithoutSafePayPrincipal()
            throws Exception {

        mockMvc.perform(post(
                        "/api/v1/transactions/1001/otp")
                        .header(
                                IdempotencyHeaders.IDEMPOTENCY_KEY,
                                "otp-idempotency-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(otpService);
    }

    private MockHttpServletRequestBuilder asCustomer(
            MockHttpServletRequestBuilder request) {

        return asAuthenticatedCustomer(request)
                .header(
                        IdempotencyHeaders.IDEMPOTENCY_KEY,
                        "otp-idempotency-key");
    }

    private MockHttpServletRequestBuilder asAuthenticatedCustomer(
            MockHttpServletRequestBuilder request) {

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(USER_ID);
        return request.principal(authentication);
    }

    private static OtpChallengeResponse challengeResponse() {
        return new OtpChallengeResponse(
                "501",
                "1001",
                OtpChallengeStatus.PENDING,
                "c*******@example.com",
                NOW.plusMinutes(5),
                NOW.plusSeconds(30),
                2,
                NOW);
    }

    private static OtpChallengeResponse cancelledChallengeResponse() {
        return new OtpChallengeResponse(
                "501",
                "1001",
                OtpChallengeStatus.CANCELLED,
                "c*******@example.com",
                NOW.plusMinutes(5),
                NOW.plusSeconds(30),
                0,
                NOW);
    }

    private static OtpVerificationResponse verificationResponse(
            boolean verified,
            int remainingAttempts) {

        return new OtpVerificationResponse(
                "501",
                "1001",
                verified
                        ? OtpChallengeStatus.VERIFIED
                        : OtpChallengeStatus.PENDING,
                verified
                        ? TransactionState.PENDING_RISK_REVIEW
                        : TransactionState.VERIFICATION_REQUIRED,
                verified,
                remainingAttempts,
                verified ? NOW : null,
                NOW);
    }
}
