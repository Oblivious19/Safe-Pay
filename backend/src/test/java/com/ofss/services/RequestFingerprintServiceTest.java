package com.ofss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ofss.beans.IdempotencyOperation;

class RequestFingerprintServiceTest {

    private ObjectMapper objectMapper;
    private RequestFingerprintService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .findAndRegisterModules();
        service = new RequestFingerprintService(objectMapper);
    }

    @Test
    void returnsLowercaseSha256Fingerprint() {
        String fingerprint = service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                Map.of("amount", "5000.00"));

        assertThat(fingerprint)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void ignoresJsonWhitespaceAndNestedPropertyOrder()
            throws Exception {

        JsonNode first = objectMapper.readTree(
                """
                {
                  "sourceAccountId": 10,
                  "beneficiary": {
                    "id": 20,
                    "method": "UPI"
                  },
                  "amount": "5000.00"
                }
                """);

        JsonNode second = objectMapper.readTree(
                "{\"amount\":\"5000.00\",\"beneficiary\":{"
                        + "\"method\":\"UPI\",\"id\":20},"
                        + "\"sourceAccountId\":10}");

        assertThat(service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                first)).isEqualTo(service.fingerprint(
                        IdempotencyOperation.TRANSACTION_CREATE,
                        "/api/v1/transactions",
                        second));
    }

    @Test
    void ignoresMapInsertionOrderRecursively() {
        Map<String, Object> first = Map.of(
                "amount",
                "25000.01",
                "beneficiary",
                Map.of("name", "Vendor", "id", 20L));

        Map<String, Object> second = Map.of(
                "beneficiary",
                Map.of("id", 20L, "name", "Vendor"),
                "amount",
                "25000.01");

        assertThat(service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                first)).isEqualTo(service.fingerprint(
                        IdempotencyOperation.TRANSACTION_CREATE,
                        "/api/v1/transactions",
                        second));
    }

    @Test
    void preservesArrayOrderAsPartOfLogicalRequest() {
        String first = service.fingerprint(
                IdempotencyOperation.RISK_REVIEW_ADD_NOTE,
                "/api/v1/admin/risk-reviews/7/notes",
                List.of("first", "second"));

        String second = service.fingerprint(
                IdempotencyOperation.RISK_REVIEW_ADD_NOTE,
                "/api/v1/admin/risk-reviews/7/notes",
                List.of("second", "first"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void operationCodeIsPartOfFingerprintScope() {
        Object request = Map.of("confirmed", true);

        String authorize = service.fingerprint(
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                "/api/v1/transactions/101/authorize",
                request);

        String cancel = service.fingerprint(
                IdempotencyOperation.TRANSACTION_CANCEL,
                "/api/v1/transactions/101/authorize",
                request);

        assertThat(authorize).isNotEqualTo(cancel);
    }

    @Test
    void concreteRequestTargetIsPartOfFingerprintScope() {
        Object request = Map.of("confirmed", true);

        String firstTransaction = service.fingerprint(
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                "/api/v1/transactions/101/authorize",
                request);

        String secondTransaction = service.fingerprint(
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                "/api/v1/transactions/102/authorize",
                request);

        assertThat(firstTransaction)
                .isNotEqualTo(secondTransaction);
    }

    @Test
    void logicalPayloadAndNullBodyRemainDistinct() {
        String firstPayload = service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                Map.of("amount", "5000.00"));

        String secondPayload = service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                Map.of("amount", "5000.01"));

        String nullBody = service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "/api/v1/transactions",
                null);

        assertThat(firstPayload).isNotEqualTo(secondPayload);
        assertThat(firstPayload).isNotEqualTo(nullBody);
    }

    @Test
    void rejectsMissingScopeWithoutHashingRequestData() {
        assertThatThrownBy(() -> service.fingerprint(
                null,
                "/api/v1/transactions",
                Map.of("secret", "must-not-appear")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operationCode is required")
                .hasMessageNotContaining("must-not-appear");

        assertThatThrownBy(() -> service.fingerprint(
                IdempotencyOperation.TRANSACTION_CREATE,
                "   ",
                Map.of("secret", "must-not-appear")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestTarget is required")
                .hasMessageNotContaining("must-not-appear");
    }

    @Test
    void otpVerificationFingerprintNeverContainsRawCode() {
        String fingerprint = service.fingerprint(
                IdempotencyOperation.OTP_VERIFY,
                "/api/v1/transactions/101/otp/verify",
                Map.of(
                        "challengeId",
                        501L,
                        "otp",
                        "123456"));

        assertThat(fingerprint)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain("123456");
    }

    @Test
    void otpIssueAndResendHaveDistinctIdempotencyScopes() {
        Object request = Map.of("transactionId", 101L);

        String issue = service.fingerprint(
                IdempotencyOperation.OTP_ISSUE,
                "/api/v1/transactions/101/otp",
                request);
        String resend = service.fingerprint(
                IdempotencyOperation.OTP_RESEND,
                "/api/v1/transactions/101/otp/resend",
                request);

        assertThat(issue).isNotEqualTo(resend);
    }
}
