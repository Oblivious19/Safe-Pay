package com.ofss.excp;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.common.CorrelationIdFilter;
import com.ofss.services.OtpDeliveryException;

class GlobalExceptionHandlerTest {

    @Test
    void missingRoutesAndResourcesReturnSafe404ProblemDetails() throws Exception {
        for (String path : java.util.List.of("/test/unmapped", "/test/missing-static-resource")) {
            mockMvc.perform(get(path).header(CorrelationIdFilter.HEADER_NAME, "missing-resource-test"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                    .andExpect(jsonPath("$.traceId").value("missing-resource-test"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("internal-resource-location"))));
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-13T10:00:00Z"),
                ZoneOffset.UTC);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(fixedClock))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsSafeBusinessRuleProblemDetail()
            throws Exception {

        mockMvc.perform(
                get("/test/business-rule")
                        .header(
                                CorrelationIdFilter.HEADER_NAME,
                                "test-correlation-123"))
                .andExpect(
                        status().isUnprocessableEntity())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER_NAME,
                                "test-correlation-123"))
                .andExpect(
                        jsonPath("$.title")
                                .value("Business rule violation"))
                .andExpect(
                        jsonPath("$.status").value(422))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Insufficient available balance"))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "INSUFFICIENT_AVAILABLE_BALANCE"))
                .andExpect(
                        jsonPath("$.traceId")
                                .value("test-correlation-123"))
                .andExpect(
                        jsonPath("$.timestamp").exists());
    }

    @Test
    void hidesInvalidArgumentImplementationDetail()
            throws Exception {

        mockMvc.perform(get("/test/invalid-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Invalid request"))
                .andExpect(
                        jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The request contains an invalid value."))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("INVALID_REQUEST"))
                .andExpect(
                        jsonPath("$.traceId").isNotEmpty())
                .andExpect(
                        jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsConflictForInvalidTransactionTransition()
            throws Exception {

        mockMvc.perform(get("/test/state-conflict"))
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Transaction state conflict"))
                .andExpect(
                        jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "Transaction cannot move from RELEASED to CANCELLED"))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("STATE_TRANSITION_CONFLICT"));
    }

    @Test
    void returnsSafeConflictForReusedKeyWithDifferentRequest()
            throws Exception {

        mockMvc.perform(get("/test/idempotency-different"))
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Idempotency conflict"))
                .andExpect(
                        jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The idempotency key has already been used for a different request."))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void returnsSafeConflictForConcurrentInProgressRequest()
            throws Exception {

        mockMvc.perform(get("/test/idempotency-in-progress"))
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Idempotency conflict"))
                .andExpect(
                        jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "An equivalent request is already being processed."))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value(
                                        "IDEMPOTENCY_REQUEST_IN_PROGRESS"));
    }

    @Test
    void returnsSafeConflictForExpiredIdempotencyKey()
            throws Exception {

        mockMvc.perform(get("/test/idempotency-expired"))
                .andExpect(status().isConflict())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(
                        jsonPath("$.title")
                                .value("Idempotency conflict"))
                .andExpect(
                        jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.detail")
                                .value(
                                        "The idempotency key has expired; submit the request with a new key."))
                .andExpect(
                        jsonPath("$.errorCode")
                                .value("IDEMPOTENCY_KEY_EXPIRED"));
    }

    @Test
    void returnsSafeOtpFailureWithRemainingAttempts()
            throws Exception {

        mockMvc.perform(get("/test/otp-verification"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title")
                        .value("OTP verification failed"))
                .andExpect(jsonPath("$.detail")
                        .value("The OTP code is incorrect"))
                .andExpect(jsonPath("$.errorCode")
                        .value("OTP_INVALID"))
                .andExpect(jsonPath("$.remainingAttempts")
                        .value(2));
    }

    @Test
    void hidesOtpMailProviderFailureDetails()
            throws Exception {

        mockMvc.perform(get("/test/otp-delivery"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title")
                        .value("OTP delivery unavailable"))
                .andExpect(jsonPath("$.detail")
                        .value("The verification code could not be delivered. Please try again later."))
                .andExpect(jsonPath("$.errorCode")
                        .value("OTP_DELIVERY_UNAVAILABLE"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "smtp-secret"))));
    }

    @Test
    void returnsSafeForbiddenForMissingRiskOfficerAuthority()
            throws Exception {

        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail")
                        .value(
                                "The authenticated user is not authorized for this operation."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/missing-static-resource")
        void missingStaticResource() throws org.springframework.web.servlet.resource.NoResourceFoundException {
            throw new org.springframework.web.servlet.resource.NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "internal-resource-location");
        }

        @GetMapping("/test/business-rule")
        void businessRule() {
            throw new BusinessRuleException(
                    "INSUFFICIENT_AVAILABLE_BALANCE",
                    "Insufficient available balance");
        }

        @GetMapping("/test/invalid-argument")
        void invalidArgument() {
            throw new IllegalArgumentException(
                    "Sensitive internal implementation detail");
        }

        @GetMapping("/test/state-conflict")
        void stateConflict() {
            throw new InvalidStateTransitionException(
                    com.ofss.beans.TransactionState.RELEASED,
                    com.ofss.beans.TransactionState.CANCELLED);
        }

        @GetMapping("/test/idempotency-different")
        void idempotencyDifferent() {
            throw IdempotencyConflictException.differentRequest();
        }

        @GetMapping("/test/idempotency-in-progress")
        void idempotencyInProgress() {
            throw IdempotencyConflictException.requestInProgress();
        }

        @GetMapping("/test/idempotency-expired")
        void idempotencyExpired() {
            throw IdempotencyConflictException.expired();
        }

        @GetMapping("/test/otp-verification")
        void otpVerification() {
            throw new OtpVerificationFailureException(
                    "OTP_INVALID",
                    "The OTP code is incorrect",
                    2);
        }

        @GetMapping("/test/otp-delivery")
        void otpDelivery() {
            throw new OtpDeliveryException(
                    "smtp-secret",
                    new RuntimeException("provider-secret"));
        }

        @GetMapping("/test/access-denied")
        void accessDenied() {
            throw new org.springframework.security.access
                    .AccessDeniedException("Internal role detail");
        }
    }
}
