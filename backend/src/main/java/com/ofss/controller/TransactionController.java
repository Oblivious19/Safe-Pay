package com.ofss.controller;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.TransactionState;
import com.ofss.common.api.PagedResponse;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.common.MoneyUtility;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.security.AuthenticatedUser;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.OperationContext;
import com.ofss.services.RequestFingerprintService;
import com.ofss.services.TransactionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;
    private final RequestFingerprintService fingerprintService;

    public TransactionController(
            TransactionService transactionService,
            IdempotencyService idempotencyService,
            RequestFingerprintService fingerprintService) {

        this.transactionService = Objects.requireNonNull(
                transactionService,
                "transactionService is required");
        this.idempotencyService = Objects.requireNonNull(
                idempotencyService,
                "idempotencyService is required");
        this.fingerprintService = Objects.requireNonNull(
                fingerprintService,
                "fingerprintService is required");
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            Authentication authentication,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreateTransactionRequest request) {

        Long userId = authenticatedUserId(authentication);
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String requestHash = fingerprintService.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                canonicalCreateRequest(request));
        String requestCorrelationId = correlationId(servletRequest);

        IdempotencyExecutionResult<TransactionResponse> result =
                idempotencyService.execute(
                        userId,
                        IdempotencyOperation.TRANSACTION_CREATE,
                        requestKey,
                        requestHash,
                        requestCorrelationId,
                        TransactionResponse.class,
                        () -> {
                            TransactionResponse response =
                                    transactionService
                                            .createTransaction(
                                                    userId,
                                                    request,
                                                    OperationContext.request(
                                                            requestCorrelationId,
                                                            requestKey));

                            return IdempotencyExecutionResult
                                    .executed(
                                            201,
                                            response,
                                            requireTransactionId(
                                                    response));
                        });

        URI location = URI.create(
                "/api/v1/transactions/"
                        + requireTransactionId(result));

        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(result.httpStatus())
                .location(location);

        return addReplayHeader(response, result)
                .body(result.responseBody());
    }

    @PostMapping("/{transactionId}/authorize")
    public ResponseEntity<TransactionResponse> authorize(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest,
            @Valid @RequestBody AuthorizeTransactionRequest request) {

        Long userId = authenticatedUserId(authentication);
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String requestTarget = "/api/v1/transactions/"
                + transactionId
                + "/authorize";

        String requestHash = fingerprintService.fingerprint(
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                requestTarget,
                Map.of("confirmed", request.confirmed()));
        String requestCorrelationId = correlationId(servletRequest);

        IdempotencyExecutionResult<TransactionResponse> result =
                idempotencyService.execute(
                        userId,
                        IdempotencyOperation.TRANSACTION_AUTHORIZE,
                        requestKey,
                        requestHash,
                        requestCorrelationId,
                        TransactionResponse.class,
                        () -> IdempotencyExecutionResult.executed(
                                200,
                                transactionService
                                        .authorizeTransaction(
                                                userId,
                                                transactionId,
                                                request,
                                                OperationContext.request(
                                                        requestCorrelationId,
                                                        requestKey)),
                                transactionId));

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(result.responseBody());
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TransactionSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) TransactionState state,
            @RequestParam(required = false) Long sourceAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (from != null || to != null || state != null || sourceAccountId != null) {
            return ResponseEntity.ok(transactionService.listTransactions(
                    authenticatedUserId(authentication), from, to, state, sourceAccountId, page, size));
        }
        return ResponseEntity.ok(
                transactionService.listTransactions(
                        authenticatedUserId(authentication),
                        page,
                        size));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> get(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId) {

        return ResponseEntity.ok(
                transactionService.getTransaction(
                        authenticatedUserId(authentication),
                        transactionId));
    }

    @GetMapping("/{transactionId}/risk-explanation")
    public ResponseEntity<TransactionRiskExplanationResponse>
            getRiskExplanation(
                    Authentication authentication,
                    @PathVariable("transactionId") Long transactionId) {

        return ResponseEntity.ok(
                transactionService.getRiskExplanation(
                        authenticatedUserId(authentication),
                        transactionId));
    }

    @PostMapping("/{transactionId}/cancel")
    public ResponseEntity<TransactionResponse> cancel(
            Authentication authentication,
            @PathVariable("transactionId") Long transactionId,
            @RequestHeader(
                    value = IdempotencyHeaders.IDEMPOTENCY_KEY,
                    required = false)
                    String idempotencyKey,
            HttpServletRequest servletRequest) {

        Long userId = authenticatedUserId(authentication);
        String requestKey = requireSingleIdempotencyKey(
                servletRequest,
                idempotencyKey);
        String requestTarget = "/api/v1/transactions/"
                + transactionId
                + "/cancel";

        String requestHash = fingerprintService.fingerprint(
                IdempotencyOperation.TRANSACTION_CANCEL,
                requestTarget,
                null);
        String requestCorrelationId = correlationId(servletRequest);

        IdempotencyExecutionResult<TransactionResponse> result =
                idempotencyService.execute(
                        userId,
                        IdempotencyOperation.TRANSACTION_CANCEL,
                        requestKey,
                        requestHash,
                        requestCorrelationId,
                        TransactionResponse.class,
                        () -> IdempotencyExecutionResult.executed(
                                200,
                                transactionService
                                        .cancelTransaction(
                                                userId,
                                                transactionId,
                                                OperationContext.request(
                                                        requestCorrelationId,
                                                        requestKey)),
                                transactionId));

        return addReplayHeader(
                ResponseEntity.status(result.httpStatus()),
                result)
                .body(result.responseBody());
    }

    private Map<String, Object> canonicalCreateRequest(
            CreateTransactionRequest request) {

        request.validateCategory();
        Map<String, Object> canonicalRequest =
                new LinkedHashMap<>();

        canonicalRequest.put(
                "sourceAccountId",
                request.sourceAccountId());
        canonicalRequest.put(
                "beneficiaryId",
                request.beneficiaryId());
        canonicalRequest.put(
                "amount",
                MoneyUtility.requireValidTransactionAmount(
                                request.amount())
                        .toPlainString());
        canonicalRequest.put("purpose", request.purpose());
        canonicalRequest.put(
                "customerReference",
                request.customerReference());
        if (request.category() != null) {
            canonicalRequest.put("category", request.category().name());
        }

        return canonicalRequest;
    }

    private String requireSingleIdempotencyKey(
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

    private String correlationId(
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

    private static Long requireTransactionId(
            TransactionResponse response) {

        Objects.requireNonNull(
                response,
                "transaction response is required");

        try {
            Long transactionId = Long.valueOf(
                    response.transactionId());

            if (transactionId <= 0L) {
                throw new NumberFormatException(
                        "transaction ID is not positive");
            }

            return transactionId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Transaction response has an invalid identifier",
                    exception);
        }
    }

    private static Long requireTransactionId(
            IdempotencyExecutionResult<?> result) {

        return Objects.requireNonNull(
                result.transactionId(),
                "idempotent transaction ID is required");
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

    private Long authenticatedUserId(
            Authentication authentication) {

        return AuthenticatedUser.userId(authentication);
    }
}
