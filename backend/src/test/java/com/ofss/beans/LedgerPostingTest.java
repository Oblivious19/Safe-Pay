package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

class LedgerPostingTest {

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T16:30:15.123456789+05:30");

    private static final OffsetDateTime UTC_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T11:00:15.123456Z");

    @Test
    void mapsCanonicalTableAndSequence() {
        assertThat(LedgerPosting.class.getAnnotation(Entity.class))
                .isNotNull();

        Table table = LedgerPosting.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("LEDGER_POSTING");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(table.uniqueConstraints())
                .extracting(constraint -> constraint.name())
                .containsExactly(
                        "UK_LEDGER_POSTING_REFERENCE",
                        "UK_LEDGER_POSTING_IDEMPOTENCY",
                        "UK_LEDGER_POSTING_TRANSACTION");

        SequenceGenerator sequence = LedgerPosting.class
                .getAnnotation(SequenceGenerator.class);
        assertThat(sequence.name())
                .isEqualTo("ledgerPostingSequence");
        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_LEDGER_POSTING_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void mapsTransactionEnumsMoneyAndVersion() throws Exception {
        Field transaction = LedgerPosting.class
                .getDeclaredField("transaction");
        ManyToOne relationship = transaction.getAnnotation(
                ManyToOne.class);
        JoinColumn joinColumn = transaction.getAnnotation(
                JoinColumn.class);

        assertThat(relationship.fetch())
                .isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isTrue();
        assertThat(joinColumn.name())
                .isEqualTo("TRANSACTION_ID");
        assertThat(joinColumn.nullable()).isTrue();
        assertThat(joinColumn.updatable()).isFalse();

        assertEnumColumn("postingType", "POSTING_TYPE", 30);
        assertEnumColumn("currencyCode", "CURRENCY_CODE", 3);
        assertEnumColumn("status", "STATUS", 20);

        Column amount = LedgerPosting.class
                .getDeclaredField("amount")
                .getAnnotation(Column.class);
        assertThat(amount.precision()).isEqualTo(18);
        assertThat(amount.scale()).isEqualTo(2);

        Field version = LedgerPosting.class
                .getDeclaredField("versionNo");
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(version.getAnnotation(Column.class).precision())
                .isEqualTo(10);
    }

    @Test
    void createsPendingPaymentPostingFromCanonicalTransaction() {
        TransactionDb transaction = releasedTransaction();

        LedgerPosting posting =
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        " PAYMENT-SETTLEMENT-101 ",
                        " TRANSACTION_SETTLE:TX-101 ",
                        INPUT_TIME);

        assertThat(posting.getPostingReference())
                .isEqualTo("PAYMENT-SETTLEMENT-101");
        assertThat(posting.getPostingType())
                .isEqualTo(
                        LedgerPostingType.PAYMENT_SETTLEMENT);
        assertThat(posting.getTransaction())
                .isSameAs(transaction);
        assertThat(posting.getSourceSystem())
                .isEqualTo("SAFEPAY");
        assertThat(posting.getIdempotencyKey())
                .isEqualTo("TRANSACTION_SETTLE:TX-101");
        assertThat(posting.getAmount())
                .isEqualByComparingTo("250.00");
        assertThat(posting.getCurrencyCode())
                .isEqualTo(CurrencyCode.INR);
        assertThat(posting.getExpectedEntryCount()).isEqualTo(2);
        assertThat(posting.getStatus())
                .isEqualTo(LedgerPostingStatus.PENDING);
        assertThat(posting.getCreatedAt()).isEqualTo(UTC_TIME);
        assertThat(posting.getUpdatedAt()).isEqualTo(UTC_TIME);
        assertThat(posting.getPostedAt()).isNull();
        assertThat(posting.getFailedAt()).isNull();
        assertThat(posting.getFailureCode()).isNull();
    }

    @Test
    void rejectsUnpersistedOrNonReleasedTransaction() {
        TransactionDb transaction = releasedTransaction();
        when(transaction.getTransactionId()).thenReturn(null);

        assertThatThrownBy(() ->
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        "posting",
                        "key",
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction must already be persisted");

        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getState())
                .thenReturn(TransactionState.PROTECTED);

        assertThatThrownBy(() ->
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        "posting",
                        "key",
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction must be RELEASED");
    }

    @Test
    void rejectsInvalidIdentityAndCurrency() {
        TransactionDb transaction = releasedTransaction();

        assertThatThrownBy(() ->
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        "   ",
                        "key",
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("postingReference is required");

        when(transaction.getCurrencyCode()).thenReturn(null);

        assertThatThrownBy(() ->
                LedgerPosting.createPaymentSettlement(
                        transaction,
                        "posting",
                        "key",
                        INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction must use INR");
    }

    @Test
    void marksPendingPostingPostedExactlyOnce() {
        LedgerPosting posting = validPosting();

        posting.markPosted(INPUT_TIME.plusSeconds(1));

        assertThat(posting.getStatus())
                .isEqualTo(LedgerPostingStatus.POSTED);
        assertThat(posting.getPostedAt())
                .isEqualTo(UTC_TIME.plusSeconds(1));
        assertThat(posting.getUpdatedAt())
                .isEqualTo(UTC_TIME.plusSeconds(1));
        assertThat(posting.getFailedAt()).isNull();

        assertThatThrownBy(() ->
                posting.markPosted(INPUT_TIME.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Only a pending ledger posting can change");
    }

    @Test
    void marksPendingPostingFailedWithNormalizedCode() {
        LedgerPosting posting = validPosting();

        posting.markFailed(
                " clearing_account_inactive ",
                INPUT_TIME.plusSeconds(1));

        assertThat(posting.getStatus())
                .isEqualTo(LedgerPostingStatus.FAILED);
        assertThat(posting.getFailureCode())
                .isEqualTo("CLEARING_ACCOUNT_INACTIVE");
        assertThat(posting.getFailedAt())
                .isEqualTo(UTC_TIME.plusSeconds(1));
        assertThat(posting.getPostedAt()).isNull();
    }

    @Test
    void rejectsLifecycleTimestampBeforeCreation() {
        LedgerPosting posting = validPosting();

        assertThatThrownBy(() ->
                posting.markPosted(INPUT_TIME.minusNanos(1_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "postedAt cannot be before createdAt");
    }

    private static LedgerPosting validPosting() {
        return LedgerPosting.createPaymentSettlement(
                releasedTransaction(),
                "PAYMENT-SETTLEMENT-101",
                "TRANSACTION_SETTLE:TX-101",
                INPUT_TIME);
    }

    private static TransactionDb releasedTransaction() {
        TransactionDb transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(101L);
        when(transaction.getState())
                .thenReturn(TransactionState.RELEASED);
        when(transaction.getAmount())
                .thenReturn(new BigDecimal("250.00"));
        when(transaction.getCurrencyCode())
                .thenReturn(CurrencyCode.INR);
        return transaction;
    }

    private static void assertEnumColumn(
            String fieldName,
            String columnName,
            int length) throws Exception {

        Field field = LedgerPosting.class
                .getDeclaredField(fieldName);

        assertThat(field.getAnnotation(Enumerated.class).value())
                .isEqualTo(EnumType.STRING);

        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
    }
}
