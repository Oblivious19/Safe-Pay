package com.ofss.controller;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.RoleName;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.riskreview.RequiredRiskReviewDecisionRequest;
import com.ofss.dto.riskreview.RiskReviewDecisionRequest;
import com.ofss.dto.riskreview.RiskReviewDetailResponse;
import com.ofss.dto.riskreview.RiskReviewNoteRequest;
import com.ofss.dto.riskreview.RiskReviewNoteResponse;
import com.ofss.dto.riskreview.RiskReviewSummaryResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.RequestFingerprintService;
import com.ofss.services.RiskReviewService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/risk-reviews")
@PreAuthorize("hasAuthority('RISK_OFFICER')")
public class RiskReviewController {

    private final RiskReviewService riskReviewService;
    private final IdempotencyService idempotencyService;
    private final RequestFingerprintService fingerprintService;

    public RiskReviewController(
            RiskReviewService riskReviewService,
            IdempotencyService idempotencyService,
            RequestFingerprintService fingerprintService) {

        this.riskReviewService = Objects.requireNonNull(
                riskReviewService,
                "riskReviewService is required");
        this.idempotencyService = Objects.requireNonNull(
                idempotencyService,
                "idempotencyService is required");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService,
                "fingerprintService is required");
    }

    @GetMapping
    public ResponseEntity<PagedResponse<RiskReviewSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) com.ofss.beans.PaymentCategory category,
            @RequestParam(defaultValue = "PRIORITY") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long officerId = riskOfficerId(authentication);
        if (category != null || !"PRIORITY".equals(sort)) {
            return ResponseEntity.ok(riskReviewService.listPending(officerId, category, sort, page, size));
        }
        return ResponseEntity.ok(
                riskReviewService.listPending(
                        officerId,
                        page,
                        size));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<RiskReviewDetailResponse> get(
            Authentication authentication,
            @PathVariable("reviewId") Long reviewId) {

        return ResponseEntity.ok(
                riskReviewService.getReview(
                        riskOfficerId(authentication),
                        reviewId));
    }

    @PostMapping("/{reviewId}/approve")
    public ResponseEntity<RiskReviewDetailResponse> approve(
            Authentication authentication,
            @PathVariable("reviewId") Long reviewId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody(required = false)
                    RiskReviewDecisionRequest request) {

        RiskReviewDecisionRequest logicalRequest = request == null
                ? new RiskReviewDecisionRequest(null)
                : request;

        return executeDecision(
                authentication,
                reviewId,
                idempotencyKey,
                servletRequest,
                IdempotencyOperation.RISK_REVIEW_APPROVE,
                "approve",
                logicalRequest,
                (officerId, correlationId, requestKey) ->
                        riskReviewService.approve(
                                officerId,
                                reviewId,
                                logicalRequest.reason(),
                                correlationId,
                                requestKey));
    }

    @PostMapping("/{reviewId}/reject")
    public ResponseEntity<RiskReviewDetailResponse> reject(
            Authentication authentication,
            @PathVariable("reviewId") Long reviewId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody
                    RequiredRiskReviewDecisionRequest request) {

        return executeDecision(
                authentication,
                reviewId,
                idempotencyKey,
                servletRequest,
                IdempotencyOperation.RISK_REVIEW_REJECT,
                "reject",
                request,
                (officerId, correlationId, requestKey) ->
                        riskReviewService.reject(
                                officerId,
                                reviewId,
                                request.reason(),
                                correlationId,
                                requestKey));
    }

    @PostMapping("/{reviewId}/request-verification")
    public ResponseEntity<RiskReviewDetailResponse> requestVerification(
            Authentication authentication,
            @PathVariable("reviewId") Long reviewId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody
                    RequiredRiskReviewDecisionRequest request) {

        return executeDecision(
                authentication,
                reviewId,
                idempotencyKey,
                servletRequest,
                IdempotencyOperation
                        .RISK_REVIEW_REQUEST_VERIFICATION,
                "request-verification",
                request,
                (officerId, correlationId, requestKey) ->
                        riskReviewService.requestReverification(
                                officerId,
                                reviewId,
                                request.reason(),
                                correlationId,
                                requestKey));
    }

    @PostMapping("/{reviewId}/notes")
    public ResponseEntity<RiskReviewNoteResponse> addNote(
            Authentication authentication,
            @PathVariable("reviewId") Long reviewId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody RiskReviewNoteRequest request) {

        Long officerId = riskOfficerId(authentication);
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String correlationId = correlationId(servletRequest);
        String target = target(reviewId, "notes");
        String requestHash = fingerprintService.fingerprint(
                IdempotencyOperation.RISK_REVIEW_ADD_NOTE,
                target,
                request);

        IdempotencyExecutionResult<RiskReviewNoteResponse> result =
                idempotencyService.execute(
                        officerId,
                        IdempotencyOperation.RISK_REVIEW_ADD_NOTE,
                        requestKey,
                        requestHash,
                        correlationId,
                        RiskReviewNoteResponse.class,
                        () -> {
                            RiskReviewNoteResponse response =
                                    riskReviewService.addNote(
                                            officerId,
                                            reviewId,
                                            request.note(),
                                            correlationId,
                                            requestKey);

                            return IdempotencyExecutionResult.executed(
                                    201,
                                    response,
                                    positiveLong(
                                            response.transactionId(),
                                            "transactionId"));
                        });

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(result.responseBody());
    }

    private ResponseEntity<RiskReviewDetailResponse> executeDecision(
            Authentication authentication,
            Long reviewId,
            String idempotencyKey,
            HttpServletRequest servletRequest,
            IdempotencyOperation operation,
            String actionPath,
            Object logicalRequest,
            DecisionAction action) {

        Long officerId = riskOfficerId(authentication);
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String correlationId = correlationId(servletRequest);
        String requestHash = fingerprintService.fingerprint(
                operation,
                target(reviewId, actionPath),
                logicalRequest);

        IdempotencyExecutionResult<RiskReviewDetailResponse> result =
                idempotencyService.execute(
                        officerId,
                        operation,
                        requestKey,
                        requestHash,
                        correlationId,
                        RiskReviewDetailResponse.class,
                        () -> {
                            RiskReviewDetailResponse response =
                                    action.execute(
                                            officerId,
                                            correlationId,
                                            requestKey);

                            return IdempotencyExecutionResult.executed(
                                    200,
                                    response,
                                    positiveLong(
                                            response.review()
                                                    .transactionId(),
                                            "transactionId"));
                        });

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(result.responseBody());
    }

    private static String target(Long reviewId, String actionPath) {
        return "/api/v1/admin/risk-reviews/"
                + reviewId
                + "/"
                + actionPath;
    }

    private static String requireSingleIdempotencyKey(
            HttpServletRequest servletRequest,
            String boundHeaderValue) {

        List<String> values = Collections.list(
                servletRequest.getHeaders(
                        IdempotencyHeaders.IDEMPOTENCY_KEY));

        if (values.size() != 1 || boundHeaderValue == null) {
            throw new IllegalArgumentException(
                    "Exactly one Idempotency-Key header is required");
        }

        return values.getFirst();
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

    private static Long riskOfficerId(Authentication authentication) {
        if (!AuthenticatedUser.roles(authentication)
                .contains(RoleName.RISK_OFFICER)) {
            throw new AccessDeniedException(
                    "RISK_OFFICER authority is required");
        }
        return AuthenticatedUser.userId(authentication);
    }

    private static Long positiveLong(String value, String fieldName) {
        try {
            Long parsed = Long.valueOf(value);
            if (parsed <= 0L) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    fieldName + " is invalid",
                    exception);
        }
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

    @FunctionalInterface
    private interface DecisionAction {
        RiskReviewDetailResponse execute(
                Long officerId,
                String correlationId,
                String idempotencyKey);
    }
}
