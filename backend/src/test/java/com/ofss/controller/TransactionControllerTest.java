package com.ofss.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ofss.beans.CurrencyCode;
import com.ofss.beans.IdempotencyOperation;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionState;
import com.ofss.common.CorrelationIdFilter;
import com.ofss.common.IdempotencyHeaders;
import com.ofss.common.api.PagedResponse;
import com.ofss.dto.transaction.AuthorizeTransactionRequest;
import com.ofss.dto.transaction.CreateTransactionRequest;
import com.ofss.dto.transaction.TransactionResponse;
import com.ofss.dto.transaction.TransactionRiskExplanationResponse;
import com.ofss.dto.transaction.TransactionSummaryResponse;
import com.ofss.excp.GlobalExceptionHandler;
import com.ofss.excp.InvalidStateTransitionException;
import com.ofss.security.SafePayPrincipal;
import com.ofss.services.IdempotencyExecutionResult;
import com.ofss.services.IdempotencyService;
import com.ofss.services.OperationContext;
import com.ofss.services.RequestFingerprintService;
import com.ofss.services.TransactionService;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    private static final Long CUSTOMER_ID = 7L;
    private static final Long TRANSACTION_ID = 1001L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.parse("2026-09-15T10:00:00Z");

    @Mock private TransactionService transactionService;
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
            Supplier<IdempotencyExecutionResult<TransactionResponse>>
                    action = invocation.getArgument(6);

            return action.get();
        }).when(idempotencyService).execute(
                anyLong(),
                any(IdempotencyOperation.class),
                any(),
                anyString(),
                anyString(),
                eq(TransactionResponse.class),
                any());

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new TransactionController(
                                transactionService,
                                idempotencyService,
                                fingerprintService))
                .setControllerAdvice(
                        new GlobalExceptionHandler(Clock.fixed(
                                Instant.parse("2026-09-15T10:00:00Z"),
                                ZoneOffset.UTC)))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void bindsHistoryFiltersWithoutTrustingCustomerParameter() throws Exception {
        var page = new PagedResponse<TransactionSummaryResponse>(List.of(), 0, 20, 0, 0, true, true);
        when(transactionService.listTransactions(CUSTOMER_ID, NOW, NOW.plusHours(1),
                TransactionState.CREATED, 501L, 0, 20)).thenReturn(page);
        mockMvc.perform(asCustomer(get("/api/v1/transactions"))
                        .param("from", NOW.toString()).param("to", NOW.plusHours(1).toString())
                        .param("state", "CREATED").param("sourceAccountId", "501").param("customerUserId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(transactionService).listTransactions(CUSTOMER_ID, NOW, NOW.plusHours(1),
                TransactionState.CREATED, 501L, 0, 20);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void malformedHistoryFiltersReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions").param("state", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/transactions").param("from", "2026-09-19"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService);
    }

    @Test
    void strictHighValueCategoryValidationRunsBeforeIdempotencyLookup() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .header(IdempotencyHeaders.IDEMPOTENCY_KEY, "old-high-value-key")
                        .content(validCreateJson().replace("25000.01", "100000.01")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService, idempotencyService, fingerprintService);
    }

    @Test
    void highValueFingerprintIncludesCategory() throws Exception {
        when(transactionService.createTransaction(eq(CUSTOMER_ID), any(CreateTransactionRequest.class),
                any(OperationContext.class))).thenReturn(transactionResponse(TransactionState.CREATED));
        String json = validCreateJson().replace("25000.01", "100000.01")
                .replace("\"purpose\":", "\"category\":\"MEDICAL\",\"purpose\":");
        mockMvc.perform(asCustomer(post("/api/v1/transactions")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        ArgumentCaptor<Object> canonical = ArgumentCaptor.forClass(Object.class);
        verify(fingerprintService).fingerprint(eq(IdempotencyOperation.TRANSACTION_CREATE),
                eq("/api/v1/transactions"), canonical.capture());
        assertThat(((java.util.Map<?, ?>) canonical.getValue()).containsKey("category")).isTrue();
        assertThat(((java.util.Map<?, ?>) canonical.getValue()).get("category")).isEqualTo("MEDICAL");
    }

    @Test
    void createsInstructionAndReturnsCanonicalLocation()
            throws Exception {

        when(transactionService.createTransaction(
                eq(CUSTOMER_ID),
                any(CreateTransactionRequest.class),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(TransactionState.CREATED));

        mockMvc.perform(asCustomer(post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/transactions/1001"))
                .andExpect(header().doesNotExist(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.transactionId").value("1001"));
    }

    @Test
    void authorizesOnlyThroughExplicitConfirmationEndpoint()
            throws Exception {

        when(transactionService.authorizeTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(AuthorizeTransactionRequest.class),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(TransactionState.PROTECTED));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/authorize"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PROTECTED"));

        verify(transactionService).authorizeTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(AuthorizeTransactionRequest.class),
                any(OperationContext.class));
    }

    @Test
    void listsTransactionsUsingSafeDefaultPageBounds()
            throws Exception {

        when(transactionService.listTransactions(
                CUSTOMER_ID,
                0,
                20))
                .thenReturn(pageResponse());

        mockMvc.perform(asCustomer(get("/api/v1/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].transactionId")
                        .value("1001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void forwardsExplicitPaginationWithoutClientControlledSorting()
            throws Exception {

        when(transactionService.listTransactions(
                CUSTOMER_ID,
                2,
                10))
                .thenReturn(new PagedResponse<>(
                        List.of(),
                        2,
                        10,
                        21,
                        3,
                        false,
                        true));

        mockMvc.perform(asCustomer(get("/api/v1/transactions")
                        .param("page", "2")
                        .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(21));
    }

    @Test
    void retrievesOwnedTransactionDetail() throws Exception {
        when(transactionService.getTransaction(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .thenReturn(transactionResponse(TransactionState.CREATED));

        mockMvc.perform(asCustomer(get(
                        "/api/v1/transactions/1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference")
                        .value("SP-CONTROLLER-TEST"))
                .andExpect(jsonPath("$.maskedSourceAccountNumber")
                        .value("************3456"));
    }

    @Test
    void retrievesPersistedCustomerSafeRiskExplanation()
            throws Exception {

        when(transactionService.getRiskExplanation(
                CUSTOMER_ID,
                TRANSACTION_ID))
                .thenReturn(riskExplanation());

        mockMvc.perform(asCustomer(get(
                        "/api/v1/transactions/1001/risk-explanation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskTier").value("HIGH"))
                .andExpect(jsonPath("$.explanation")
                        .value("Amount matched HIGH band."))
                .andExpect(jsonPath("$.riskPolicyId").doesNotExist());
    }

    @Test
    void cancelsOnlyThroughDedicatedMutationEndpoint()
            throws Exception {

        when(transactionService.cancelTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(TransactionState.CANCELLED));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }

    @Test
    void requiresIdempotencyKeyForEveryTransactionMutation()
            throws Exception {

        mockMvc.perform(asAuthenticatedCustomer(
                        post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        mockMvc.perform(asAuthenticatedCustomer(post(
                        "/api/v1/transactions/1001/authorize"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        mockMvc.perform(asAuthenticatedCustomer(post(
                        "/api/v1/transactions/1001/cancel")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsMultipleIdempotencyKeyHeadersBeforeEffect()
            throws Exception {

        mockMvc.perform(asAuthenticatedCustomer(
                        post("/api/v1/transactions"))
                        .header(
                                IdempotencyHeaders.IDEMPOTENCY_KEY,
                                "first-key",
                                "second-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void returnsReplayHeaderAndDoesNotRepeatCreateEffect()
            throws Exception {

        IdempotencyExecutionResult<TransactionResponse> replay =
                IdempotencyExecutionResult.replayed(
                        201,
                        transactionResponse(TransactionState.CREATED),
                        TRANSACTION_ID);

        doAnswer(invocation -> replay)
                .when(idempotencyService)
                .execute(
                        eq(CUSTOMER_ID),
                        eq(IdempotencyOperation.TRANSACTION_CREATE),
                        eq("controller-idempotency-key"),
                        anyString(),
                        anyString(),
                        eq(TransactionResponse.class),
                        any());

        mockMvc.perform(asCustomer(post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                        "true"))
                .andExpect(header().string(
                        "Location",
                        "/api/v1/transactions/1001"))
                .andExpect(jsonPath("$.transactionId")
                        .value("1001"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void replayDoesNotRepeatAuthorizationOrCancellationEffects()
            throws Exception {

        doAnswer(invocation -> IdempotencyExecutionResult.replayed(
                200,
                transactionResponse(TransactionState.PROTECTED),
                TRANSACTION_ID))
                .when(idempotencyService)
                .execute(
                        eq(CUSTOMER_ID),
                        eq(IdempotencyOperation.TRANSACTION_AUTHORIZE),
                        eq("controller-idempotency-key"),
                        anyString(),
                        anyString(),
                        eq(TransactionResponse.class),
                        any());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/authorize"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                        "true"))
                .andExpect(jsonPath("$.state")
                        .value("PROTECTED"));

        doAnswer(invocation -> IdempotencyExecutionResult.replayed(
                200,
                transactionResponse(TransactionState.CANCELLED),
                TRANSACTION_ID))
                .when(idempotencyService)
                .execute(
                        eq(CUSTOMER_ID),
                        eq(IdempotencyOperation.TRANSACTION_CANCEL),
                        eq("controller-idempotency-key"),
                        anyString(),
                        anyString(),
                        eq(TransactionResponse.class),
                        any());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/cancel")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        IdempotencyHeaders.IDEMPOTENCY_REPLAYED,
                        "true"))
                .andExpect(jsonPath("$.state")
                        .value("CANCELLED"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void usesSeparateCanonicalScopesForAllMutations()
            throws Exception {

        when(transactionService.createTransaction(
                eq(CUSTOMER_ID),
                any(CreateTransactionRequest.class),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(
                        TransactionState.CREATED));

        when(transactionService.authorizeTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(AuthorizeTransactionRequest.class),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(
                        TransactionState.PROTECTED));

        when(transactionService.cancelTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(
                        TransactionState.CANCELLED));

        mockMvc.perform(asCustomer(post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/authorize"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/cancel")))
                .andExpect(status().isOk());

        verify(fingerprintService).fingerprint(
                eq(IdempotencyOperation.TRANSACTION_CREATE),
                eq("/api/v1/transactions"),
                any());
        verify(fingerprintService).fingerprint(
                eq(IdempotencyOperation.TRANSACTION_AUTHORIZE),
                eq("/api/v1/transactions/1001/authorize"),
                any());
        verify(fingerprintService).fingerprint(
                eq(IdempotencyOperation.TRANSACTION_CANCEL),
                eq("/api/v1/transactions/1001/cancel"),
                eq(null));
    }

    @Test
    void normalizesEquivalentAmountsBeforeFingerprinting()
            throws Exception {

        when(transactionService.createTransaction(
                eq(CUSTOMER_ID),
                any(CreateTransactionRequest.class),
                any(OperationContext.class)))
                .thenReturn(transactionResponse(
                        TransactionState.CREATED));

        String firstRequest = validCreateJson()
                .replace("25000.01", "5000.0");

        String secondRequest = validCreateJson()
                .replace("25000.01", "5000.00");

        mockMvc.perform(asCustomer(post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(asCustomer(post("/api/v1/transactions"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest))
                .andExpect(status().isCreated());

        ArgumentCaptor<Object> logicalRequest =
                ArgumentCaptor.forClass(Object.class);

        verify(fingerprintService, times(2)).fingerprint(
                eq(IdempotencyOperation.TRANSACTION_CREATE),
                eq("/api/v1/transactions"),
                logicalRequest.capture());

        assertThat(logicalRequest.getAllValues())
                .allSatisfy(value -> {
                    @SuppressWarnings("unchecked")
                    var request = (java.util.Map<String, Object>) value;

                    assertThat(request.get("amount"))
                            .isEqualTo("5000.00");
                });
    }

    @Test
    void rejectsInvalidCreatePayloadBeforeCallingService()
            throws Exception {

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0.99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsMalformedAmountBeforeCallingService()
            throws Exception {

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId": 70,
                                  "beneficiaryId": 700,
                                  "amount": "not-money"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("MALFORMED_REQUEST"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void rejectsAuthorizationWithoutAffirmativeConfirmation()
            throws Exception {

        mockMvc.perform(post(
                        "/api/v1/transactions/1001/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("VALIDATION_FAILED"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void returnsConflictForIllegalCancellationState()
            throws Exception {

        when(transactionService.cancelTransaction(
                eq(CUSTOMER_ID),
                eq(TRANSACTION_ID),
                any(OperationContext.class)))
                .thenThrow(new InvalidStateTransitionException(
                        TransactionState.RELEASED,
                        TransactionState.CANCELLED));

        mockMvc.perform(asCustomer(post(
                        "/api/v1/transactions/1001/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode")
                        .value("STATE_TRANSITION_CONFLICT"));
    }

    @Test
    void failsClosedWithoutSafePayPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode")
                        .value("ACCESS_DENIED"));

        verifyNoInteractions(transactionService);
    }

    private MockHttpServletRequestBuilder asCustomer(
            MockHttpServletRequestBuilder request) {

        return asAuthenticatedCustomer(request)
                .header(
                        IdempotencyHeaders.IDEMPOTENCY_KEY,
                        "controller-idempotency-key");
    }

    private MockHttpServletRequestBuilder asAuthenticatedCustomer(
            MockHttpServletRequestBuilder request) {

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(CUSTOMER_ID);
        return request.principal(authentication);
    }

    private static String validCreateJson() {
        return """
                {
                  "sourceAccountId": 70,
                  "beneficiaryId": 700,
                  "amount": 25000.01,
                  "purpose": "Invoice payment",
                  "customerReference": "INV-100"
                }
                """;
    }

    private static TransactionResponse transactionResponse(
            TransactionState state) {

        boolean assessed = state != TransactionState.CREATED;

        return new TransactionResponse(
                "1001",
                "SP-CONTROLLER-TEST",
                "70",
                "************3456",
                "700",
                "Vendor One",
                "v***r@upi",
                new BigDecimal("25000.01"),
                CurrencyCode.INR,
                "Invoice payment",
                "INV-100",
                state,
                state == TransactionState.CANCELLED
                        ? "CUSTOMER_CANCELLED"
                        : null,
                state == TransactionState.PROTECTED
                        ? new BigDecimal("25000.01")
                        : new BigDecimal("0.00"),
                assessed ? RiskTier.HIGH : null,
                assessed ? "AMOUNT_ONLY_V1" : null,
                assessed ? 60L : null,
                assessed ? "Amount matched HIGH band." : null,
                NOW,
                assessed ? NOW : null,
                assessed ? NOW : null,
                state == TransactionState.PROTECTED
                        ? NOW.plusSeconds(60)
                        : null,
                null,
                null,
                null,
                state == TransactionState.CANCELLED ? NOW : null,
                null,
                NOW);
    }

    private static PagedResponse<TransactionSummaryResponse> pageResponse() {
        return new PagedResponse<>(
                List.of(new TransactionSummaryResponse(
                        "1001",
                        "SP-CONTROLLER-TEST",
                        "700",
                        "Vendor One",
                        "v***r@upi",
                        new BigDecimal("25000.01"),
                        CurrencyCode.INR,
                        TransactionState.CREATED,
                        null,
                        null,
                        NOW,
                        NOW)),
                0,
                20,
                1,
                1,
                true,
                true);
    }

    private static TransactionRiskExplanationResponse riskExplanation() {
        return new TransactionRiskExplanationResponse(
                "1001",
                "SP-CONTROLLER-TEST",
                TransactionState.PROTECTED,
                RiskTier.HIGH,
                "AMOUNT_ONLY_V1",
                60L,
                "Amount matched HIGH band.",
                NOW,
                NOW.plusSeconds(60));
    }
}
