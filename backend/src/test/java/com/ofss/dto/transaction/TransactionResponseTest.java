package com.ofss.dto.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ofss.beans.Account;
import com.ofss.beans.Beneficiary;
import com.ofss.beans.BeneficiaryPaymentMethod;
import com.ofss.beans.CurrencyCode;
import com.ofss.beans.RiskTier;
import com.ofss.beans.TransactionDb;
import com.ofss.beans.TransactionState;

class TransactionResponseTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-09-15T11:15:30Z");

    private static final OffsetDateTime ASSESSED_AT =
            OffsetDateTime.parse("2026-09-15T11:16:00Z");

    @Test
    void protectedCountdownClampsAtExpiryAndFailsClosedWithoutDeadline() {
        TransactionDb payment = transaction(BeneficiaryPaymentMethod.BANK_ACCOUNT);
        when(payment.getState()).thenReturn(TransactionState.PROTECTED);
        when(payment.getProtectedUntil()).thenReturn(ASSESSED_AT.plusSeconds(10));
        var response = TransactionResponse.from(payment);
        assertThat(response.withObservation(ASSESSED_AT).protectionRemainingMillis()).isEqualTo(10000L);
        assertThat(response.withObservation(ASSESSED_AT).canCancel()).isTrue();
        assertThat(response.withObservation(ASSESSED_AT.plusSeconds(10)).protectionRemainingMillis()).isZero();
        assertThat(response.withObservation(ASSESSED_AT.plusSeconds(10)).canCancel()).isFalse();
        assertThat(response.withObservation(ASSESSED_AT.plusSeconds(11)).protectionRemainingMillis()).isZero();
        assertThat(response.withObservation(ASSESSED_AT.plusSeconds(11)).canCancel()).isFalse();
        when(payment.getProtectedUntil()).thenReturn(null);
        var incomplete = TransactionResponse.from(payment).withObservation(ASSESSED_AT);
        assertThat(incomplete.protectionRemainingMillis()).isNull();
        assertThat(incomplete.canCancel()).isFalse();
    }

    @Test
    void untimedStatesReuseCanonicalCancellationRulesWithoutInventingCountdown() {
        TransactionDb payment = transaction(BeneficiaryPaymentMethod.BANK_ACCOUNT);
        for (TransactionState state : TransactionState.values()) {
            if (state == TransactionState.PROTECTED) continue;
            when(payment.getState()).thenReturn(state);
            var response = TransactionResponse.from(payment).withObservation(ASSESSED_AT);
            assertThat(response.protectionRemainingMillis()).isNull();
            assertThat(response.canCancel()).isEqualTo(state.allowsCustomerCancellation());
        }
    }

    @Test
    void getHintsSerializeWhileMutationAndOldCachedResponsesRetainOriginalShape() throws Exception {
        var mapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var mutation = TransactionResponse.from(transaction(BeneficiaryPaymentMethod.BANK_ACCOUNT));
        var cachedJson = mapper.valueToTree(mutation);
        for (String field : new String[]{"serverTime", "protectionRemainingMillis", "canCancel"}) {
            assertThat(cachedJson.has(field)).isFalse();
        }
        var replay = mapper.treeToValue(cachedJson, TransactionResponse.class);
        // JSON round-trips may normalize decimal scale (0.00 to 0) without changing value.
        assertThat(replay).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(mutation);
        com.fasterxml.jackson.databind.JsonNode replayJson = mapper.valueToTree(replay);
        assertThat(replayJson).isEqualTo(cachedJson);
        var detail = mapper.valueToTree(mutation.withObservation(ASSESSED_AT));
        assertThat(detail.hasNonNull("serverTime")).isTrue();
        assertThat(detail.get("canCancel").asBoolean()).isTrue();
        assertThat(detail.has("protectionRemainingMillis")).isFalse();
    }

    @Test
    void categoryJsonIsConditionalAndOldCachedJsonStillDeserializes() throws Exception {
        var mapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        TransactionDb payment = transaction(BeneficiaryPaymentMethod.BANK_ACCOUNT);
        for (String amount : new String[]{"5000.00", "100000.00"}) {
            when(payment.getAmount()).thenReturn(new BigDecimal(amount));
            var json = mapper.valueToTree(TransactionResponse.from(payment));
            assertThat(json.has("category")).isFalse();
            assertThat(mapper.valueToTree(TransactionSummaryResponse.from(payment)).has("category")).isFalse();
        }
        var lowerValue = TransactionResponse.from(payment);
        when(payment.getAmount()).thenReturn(new BigDecimal("100000.01"));
        var legacy = mapper.valueToTree(TransactionResponse.from(payment));
        assertThat(legacy.has("category")).isTrue();
        assertThat(legacy.get("category").isNull()).isTrue();
        var mixed = mapper.valueToTree(java.util.List.of(lowerValue, TransactionResponse.from(payment)));
        assertThat(mixed.get(0).has("category")).isFalse();
        assertThat(mixed.get(1).get("category").isNull()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("category");
        assertThat(mapper.treeToValue(legacy, TransactionResponse.class).category()).isNull();
        for (var category : com.ofss.beans.PaymentCategory.values()) {
            when(payment.getCategory()).thenReturn(category);
            var detail = mapper.valueToTree(TransactionResponse.from(payment));
            var summary = mapper.valueToTree(TransactionSummaryResponse.from(payment));
            assertThat(detail.get("category").asText()).isEqualTo(category.name());
            assertThat(summary.get("category").asText()).isEqualTo(category.name());
            assertThat(mapper.treeToValue(detail, TransactionResponse.class).category()).isEqualTo(category);
            assertThat(mapper.treeToValue(summary, TransactionSummaryResponse.class).category()).isEqualTo(category);
        }
    }

    @Test
    void mapsBankPaymentWithoutExposingRawIdentifiers() {
        TransactionDb transaction = transaction(
                BeneficiaryPaymentMethod.BANK_ACCOUNT);

        TransactionResponse response =
                TransactionResponse.from(transaction);

        assertThat(response.transactionId()).isEqualTo("101");
        assertThat(response.sourceAccountId()).isEqualTo("202");
        assertThat(response.maskedSourceAccountNumber())
                .isEqualTo("********9012");
        assertThat(response.beneficiaryId()).isEqualTo("303");
        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("********1098");
        assertThat(response.toString())
                .doesNotContain("123456789012")
                .doesNotContain("987654321098");
    }

    @Test
    void mapsUpiPaymentWithMaskedHandle() {
        TransactionDb transaction = transaction(
                BeneficiaryPaymentMethod.UPI);

        TransactionResponse response =
                TransactionResponse.from(transaction);

        assertThat(response.maskedDestinationIdentifier())
                .isEqualTo("m**********y@examplebank");
        assertThat(response.toString())
                .doesNotContain("merchant.pay@examplebank");
    }

    @Test
    void createsCompactTransactionSummary() {
        TransactionSummaryResponse summary =
                TransactionSummaryResponse.from(transaction(
                        BeneficiaryPaymentMethod.BANK_ACCOUNT));

        assertThat(summary.transactionId()).isEqualTo("101");
        assertThat(summary.transactionReference())
                .isEqualTo("TXN-2026-0001");
        assertThat(summary.beneficiaryName())
                .isEqualTo("Demo Beneficiary");
        assertThat(summary.amount())
                .isEqualByComparingTo("5000.01");
        assertThat(summary.state())
                .isEqualTo(TransactionState.CREATED);
    }

    @Test
    void rejectsResponseForUnpersistedTransaction() {
        TransactionDb transaction = mock(TransactionDb.class);

        assertThatThrownBy(() ->
                TransactionResponse.from(transaction))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction must already be persisted");
    }

    @Test
    void mapsOnlyCustomerSafeRiskExplanationFields() {
        TransactionDb transaction = transaction(
                BeneficiaryPaymentMethod.BANK_ACCOUNT);

        when(transaction.getState())
                .thenReturn(TransactionState.PROTECTED);
        when(transaction.getRiskTier()).thenReturn(RiskTier.MEDIUM);
        when(transaction.getPolicyVersion())
                .thenReturn("AMOUNT_ONLY_V1");
        when(transaction.getProtectionSeconds()).thenReturn(10L);
        when(transaction.getRiskExplanation())
                .thenReturn(
                        "The payment amount matched the SafePay V1 MEDIUM band.");
        when(transaction.getRiskAssessedAt())
                .thenReturn(ASSESSED_AT);
        when(transaction.getProtectedUntil())
                .thenReturn(ASSESSED_AT.plusSeconds(10));

        TransactionRiskExplanationResponse response =
                TransactionRiskExplanationResponse.from(transaction);

        assertThat(response.transactionId()).isEqualTo("101");
        assertThat(response.riskTier()).isEqualTo(RiskTier.MEDIUM);
        assertThat(response.policyVersion())
                .isEqualTo("AMOUNT_ONLY_V1");
        assertThat(response.protectionSeconds()).isEqualTo(10L);
        assertThat(response.explanation()).contains("MEDIUM");
        assertThat(response.riskAssessedAt())
                .isEqualTo(ASSESSED_AT);
    }

    @Test
    void rejectsRiskExplanationBeforeAssessment() {
        TransactionDb transaction = transaction(
                BeneficiaryPaymentMethod.BANK_ACCOUNT);

        assertThatThrownBy(() ->
                TransactionRiskExplanationResponse.from(transaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "transaction has not been risk assessed");
    }

    private static TransactionDb transaction(
            BeneficiaryPaymentMethod paymentMethod) {

        TransactionDb transaction = mock(TransactionDb.class);
        Account account = mock(Account.class);
        Beneficiary beneficiary = mock(Beneficiary.class);

        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getTransactionReference())
                .thenReturn("TXN-2026-0001");
        when(transaction.getSourceAccount()).thenReturn(account);
        when(transaction.getBeneficiary()).thenReturn(beneficiary);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("5000.01"));
        when(transaction.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        when(transaction.getPurpose())
                .thenReturn("Vendor payment");
        when(transaction.getCustomerReference())
                .thenReturn("INV-100");
        when(transaction.getState())
                .thenReturn(TransactionState.CREATED);
        when(transaction.getReservedAmount())
                .thenReturn(new BigDecimal("0.00"));
        when(transaction.getCreatedAt()).thenReturn(CREATED_AT);
        when(transaction.getUpdatedAt()).thenReturn(CREATED_AT);

        when(account.getAccountId()).thenReturn(202L);
        when(account.getAccountNumber())
                .thenReturn("123456789012");

        when(beneficiary.getBeneficiaryId()).thenReturn(303L);
        when(beneficiary.getBeneficiaryName())
                .thenReturn("Demo Beneficiary");
        when(beneficiary.getPaymentMethod())
                .thenReturn(paymentMethod);

        if (paymentMethod
                == BeneficiaryPaymentMethod.BANK_ACCOUNT) {
            when(beneficiary.getBankAccountNumber())
                    .thenReturn("987654321098");
        } else {
            when(beneficiary.getUpiId())
                    .thenReturn("merchant.pay@examplebank");
        }

        return transaction;
    }
}
