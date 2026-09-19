package com.ofss.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.dto.otp.OtpChallengeResponse;
import com.ofss.dto.otp.OtpVerificationResponse;
import com.ofss.dto.otp.VerifyOtpRequest;
import com.ofss.excp.BusinessRuleException;
import com.ofss.excp.OtpVerificationFailureException;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.OtpChallengeResult;
import com.ofss.services.OtpService;
import com.ofss.services.OtpVerificationResult;
import com.ofss.services.OperationContext;
import com.ofss.services.RequestFingerprintService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class VerificationController {

    private final OtpService otpService;
    private final IdempotencyService idempotencyService;
    private final RequestFingerprintService fingerprintService;

    public VerificationController(
            OtpService otpService,
            IdempotencyService idempotencyService,
            RequestFingerprintService fingerprintService) {

        this.otpService = Objects.requireNonNull(
                otpService,
                "otpService is required");
        this.idempotencyService = Objects.requireNonNull(
                idempotencyService,
                "idempotencyService is required");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService,
                "fingerprintService is required");
    }

    @PostMapping("/{transactionId}/otp")
    public ResponseEntity<OtpChallengeResponse> issue(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest) {

        return executeChallengeOperation(
                authentication,
                transactionId,
                idempotencyKey,
                servletRequest,
                IdempotencyOperation.OTP_ISSUE,
                "/api/v1/transactions/" + transactionId + "/otp",
                () -> otpService.issue(
                        authenticatedUserId(authentication),
                        transactionId,
                        OperationContext.request(
                                correlationId(servletRequest),
                                idempotencyKey)));
    }

    @PostMapping("/{transactionId}/otp/resend")
    public ResponseEntity<OtpChallengeResponse> resend(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest) {

        return executeChallengeOperation(
                authentication,
                transactionId,
                idempotencyKey,
                servletRequest,
                IdempotencyOperation.OTP_RESEND,
                "/api/v1/transactions/"
                        + transactionId
                        + "/otp/resend",
                () -> otpService.resend(
                        authenticatedUserId(authentication),
                        transactionId,
                        OperationContext.request(
                                correlationId(servletRequest),
                                idempotencyKey)));
    }

    @PostMapping("/{transactionId}/otp/verify")
    public ResponseEntity<OtpVerificationResponse> verify(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody VerifyOtpRequest request) {

        Long userId = authenticatedUserId(authentication);
        String requestTarget = "/api/v1/transactions/"
                + transactionId
                + "/otp/verify";
        String requestHash = fingerprintService.fingerprint(
                IdempotencyOperation.OTP_VERIFY,
                requestTarget,
                Map.of(
                        "challengeId",
                        request.challengeId(),
                        "otp",
                        request.otp()));
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String requestCorrelationId = correlationId(servletRequest);

        IdempotencyExecutionResult<OtpVerificationResult> result =
                idempotencyService.execute(
                        userId,
                        IdempotencyOperation.OTP_VERIFY,
                        requestKey,
                        requestHash,
                        requestCorrelationId,
                        OtpVerificationResult.class,
                        () -> {
                            OtpVerificationResult verification =
                                    otpService.verify(
                                            userId,
                                            transactionId,
                                            request.challengeId(),
                                            request.otp(),
                                            OperationContext.request(
                                                    requestCorrelationId,
                                                    requestKey));

                            return IdempotencyExecutionResult.executed(
                                    verification.verified() ? 200 : 422,
                                    verification,
                                    transactionId);
                        });

        OtpVerificationResult verification = Objects.requireNonNull(
                result.responseBody(),
                "OTP verification result is required");

        if (!verification.verified()) {
            throw new OtpVerificationFailureException(
                    verification.errorCode(),
                    verification.errorMessage(),
                    verification.response().remainingAttempts());
        }

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(verification.response());
    }

    private ResponseEntity<OtpChallengeResponse>
            executeChallengeOperation(
                    Authentication authentication,
                    Long transactionId,
                    String idempotencyKey,
                    HttpServletRequest servletRequest,
                    IdempotencyOperation operation,
                    String requestTarget,
                    java.util.function.Supplier<OtpChallengeResult>
                            action) {

        Long userId = authenticatedUserId(authentication);
        String requestHash = fingerprintService.fingerprint(
                operation,
                requestTarget,
                null);

        IdempotencyExecutionResult<OtpChallengeResult> result =
                idempotencyService.execute(
                        userId,
                        operation,
                        requireSingleIdempotencyKey(
                                servletRequest,
                                idempotencyKey),
                        requestHash,
                        correlationId(servletRequest),
                        OtpChallengeResult.class,
                        () -> {
                            OtpChallengeResult challenge = action.get();

                            return IdempotencyExecutionResult.executed(
                                    challenge.accepted() ? 202 : 422,
                                    challenge,
                                    transactionId);
                        });

        OtpChallengeResult challenge = Objects.requireNonNull(
                result.responseBody(),
                "OTP challenge result is required");

        if (!challenge.accepted()) {
            throw new BusinessRuleException(
                    challenge.errorCode(),
                    challenge.errorMessage());
        }

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(challenge.response());
    }

    private static String requireSingleIdempotencyKey(
            HttpServletRequest servletRequest,
            String boundHeaderValue) {

        List<String> headerValues = Collections.list(
                servletRequest.getHeaders(
                        IdempotencyHeaders.IDEMPOTENCY_KEY));

        if (headerValues.size() != 1
                || boundHeaderValue == null) {
            throw new IllegalArgumentException(
                    "Exactly one Idempotency-Key header is required");
        }

        return headerValues.getFirst();
    }

    private static String correlationId(
            HttpServletRequest servletRequest) {

        Object value = servletRequest.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE);

        if (value instanceof String correlationId
                && !correlationId.isBlank()) {
            return correlationId;
        }

        throw new IllegalStateException(
                "Request correlation ID is unavailable");
    }

    private static ResponseEntity.BodyBuilder addReplayHeader(
            ResponseEntity.BodyBuilder response,
            IdempotencyExecutionResult<?> result) {

        if (result.replayed()) {
            response.header(
                    IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                    "true");
        }

        return response;
    }

    private static Long authenticatedUserId(
            Authentication authentication) {

        return AuthenticatedUser.userId(authentication);
    }
}
