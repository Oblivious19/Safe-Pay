package com.ofss.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
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

class TransactionExceptionLogTest {

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T16:30:15.123456789+05:30");

    private static final OffsetDateTime UTC_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T11:00:15.123456Z");

    @Test
    void mapsCanonicalTableAndSequence() {
        assertThat(TransactionExceptionLog.class
                .getAnnotation(Entity.class)).isNotNull();

        Table table = TransactionExceptionLog.class
                .getAnnotation(Table.class);
        assertThat(table.name())
                .isEqualTo("TRANSACTION_EXCEPTION");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(table.uniqueConstraints()).singleElement()
                .satisfies(constraint -> {
                    assertThat(constraint.name())
                            .isEqualTo(
                                    "UK_TRANSACTION_EXCEPTION_REF");
                    assertThat(constraint.columnNames())
                            .containsExactly("EXCEPTION_REFERENCE");
                });

        SequenceGenerator sequence = TransactionExceptionLog.class
                .getAnnotation(SequenceGenerator.class);
        assertThat(sequence.name())
                .isEqualTo("transactionExceptionSequence");
        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_TRANSACTION_EXCEPTION_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void mapsRelationshipsEnumsAndVersion() throws Exception {
        assertRelationship(
                "transaction",
                "TRANSACTION_ID",
                false,
                false);
        assertRelationship(
                "posting",
                "POSTING_ID",
                true,
                false);
        assertRelationship(
                "resolvedByUser",
                "RESOLVED_BY_USER_ID",
                true,
                true);

        assertEnumColumn(
                "processingStage",
                "PROCESSING_STAGE",
                30);
        assertEnumColumn("status", "STATUS", 30);

        Field version = TransactionExceptionLog.class
                .getDeclaredField("versionNo");
        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(version.getAnnotation(Column.class).precision())
                .isEqualTo(10);
    }

    @Test
    void opensNormalizedUnresolvedException() {
        TransactionDb transaction = persistedTransaction(101L);

        TransactionExceptionLog exception =
                TransactionExceptionLog.open(
                        " EX-101 ",
                        transaction,
                        null,
                        TransactionProcessingStage.SETTLEMENT,
                        " database_timeout ",
                        " Temporary database timeout ",
                        true,
                        " correlation-101 ",
                        INPUT_TIME);

        assertThat(exception.getExceptionReference())
                .isEqualTo("EX-101");
        assertThat(exception.getTransaction())
                .isSameAs(transaction);
        assertThat(exception.getPosting()).isNull();
        assertThat(exception.getProcessingStage())
                .isEqualTo(TransactionProcessingStage.SETTLEMENT);
        assertThat(exception.getErrorCode())
                .isEqualTo("DATABASE_TIMEOUT");
        assertThat(exception.getErrorMessage())
                .isEqualTo("Temporary database timeout");
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getStatus())
                .isEqualTo(TransactionExceptionStatus.OPEN);
        assertThat(exception.getRetryCount()).isZero();
        assertThat(exception.getNextRetryAt()).isNull();
        assertThat(exception.getFirstOccurredAt())
                .isEqualTo(UTC_TIME);
        assertThat(exception.getLastOccurredAt())
                .isEqualTo(UTC_TIME);
    }

    @Test
    void rejectsUnpersistedTransactionAndMismatchedPosting() {
        TransactionDb unpersisted = persistedTransaction(null);

        assertThatThrownBy(() ->
                open(unpersisted, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction must already be persisted");

        TransactionDb transaction = persistedTransaction(101L);
        TransactionDb other = persistedTransaction(202L);
        LedgerPosting posting = mock(LedgerPosting.class);
        when(posting.getTransaction()).thenReturn(other);

        assertThatThrownBy(() ->
                open(transaction, posting, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("posting must belong to transaction");
    }

    @Test
    void schedulesStrictlySequentialRetry() {
        TransactionExceptionLog exception =
                open(persistedTransaction(101L), null, true);

        exception.scheduleRetry(
                1,
                INPUT_TIME.plusSeconds(1),
                INPUT_TIME.plusSeconds(6));

        assertThat(exception.getStatus())
                .isEqualTo(
                        TransactionExceptionStatus.RETRY_PENDING);
        assertThat(exception.getRetryCount()).isEqualTo(1);
        assertThat(exception.getNextRetryAt())
                .isEqualTo(UTC_TIME.plusSeconds(6));

        assertThatThrownBy(() ->
                exception.scheduleRetry(
                        3,
                        INPUT_TIME.plusSeconds(2),
                        INPUT_TIME.plusSeconds(32)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "retryAttempt must increment retryCount by one");
    }

    @Test
    void rejectsRetryForNonRetryableException() {
        TransactionExceptionLog exception =
                open(persistedTransaction(101L), null, false);

        assertThatThrownBy(() ->
                exception.scheduleRetry(
                        1,
                        INPUT_TIME.plusSeconds(1),
                        INPUT_TIME.plusSeconds(6)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "A non-retryable exception cannot be scheduled");
    }

    @Test
    void movesExhaustedRetriesToManualReviewWithoutNextRetry() {
        TransactionExceptionLog exception =
                open(persistedTransaction(101L), null, true);

        exception.scheduleRetry(
                1,
                INPUT_TIME.plusSeconds(1),
                INPUT_TIME.plusSeconds(6));

        assertThatThrownBy(() ->
                exception.moveToManualReview(
                        1,
                        INPUT_TIME.plusSeconds(6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "retryable exception requires exactly 3 completed retries before manual review");

        exception.scheduleRetry(
                2,
                INPUT_TIME.plusSeconds(6),
                INPUT_TIME.plusSeconds(36));
        exception.scheduleRetry(
                3,
                INPUT_TIME.plusSeconds(36),
                INPUT_TIME.plusSeconds(96));
        exception.moveToManualReview(
                3,
                INPUT_TIME.plusSeconds(96));

        assertThat(exception.getStatus())
                .isEqualTo(
                        TransactionExceptionStatus.MANUAL_REVIEW);
        assertThat(exception.getRetryCount()).isEqualTo(3);
        assertThat(exception.getNextRetryAt()).isNull();
    }

    @Test
    void resolvesOnlyWithPersistedActorAndNote() {
        TransactionExceptionLog exception =
                open(persistedTransaction(101L), null, true);
        User resolver = mock(User.class);
        when(resolver.getUserId()).thenReturn(77L);

        exception.resolve(
                resolver,
                " Reconciled by operator ",
                INPUT_TIME.plusMinutes(2));

        assertThat(exception.getStatus())
                .isEqualTo(TransactionExceptionStatus.RESOLVED);
        assertThat(exception.getResolvedByUser())
                .isSameAs(resolver);
        assertThat(exception.getResolutionNote())
                .isEqualTo("Reconciled by operator");
        assertThat(exception.getResolvedAt())
                .isEqualTo(UTC_TIME.plusMinutes(2));

        assertThatThrownBy(() ->
                exception.moveToManualReview(
                        3,
                        INPUT_TIME.plusMinutes(3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "A resolved transaction exception cannot change");
    }

    @Test
    void enumValuesExactlyMatchV6Constraints() {
        assertThat(TransactionProcessingStage.values())
                .containsExactly(
                        TransactionProcessingStage.AUTHORIZATION,
                        TransactionProcessingStage.RISK_ASSESSMENT,
                        TransactionProcessingStage.RESERVATION,
                        TransactionProcessingStage.OTP_VERIFICATION,
                        TransactionProcessingStage.RISK_REVIEW,
                        TransactionProcessingStage.RELEASE,
                        TransactionProcessingStage.SETTLEMENT,
                        TransactionProcessingStage.ACCOUNT_UPDATE,
                        TransactionProcessingStage.STATE_TRANSITION,
                        TransactionProcessingStage.SCHEDULER);

        assertThat(TransactionExceptionStatus.values())
                .containsExactly(
                        TransactionExceptionStatus.OPEN,
                        TransactionExceptionStatus.RETRY_PENDING,
                        TransactionExceptionStatus.MANUAL_REVIEW,
                        TransactionExceptionStatus.RESOLVED);
    }

    private static TransactionExceptionLog open(
            TransactionDb transaction,
            LedgerPosting posting,
            boolean retryable) {

        return TransactionExceptionLog.open(
                "EX-101",
                transaction,
                posting,
                TransactionProcessingStage.SETTLEMENT,
                "DATABASE_TIMEOUT",
                "Temporary database timeout",
                retryable,
                "correlation-101",
                INPUT_TIME);
    }

    private static TransactionDb persistedTransaction(Long id) {
        TransactionDb transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId()).thenReturn(id);
        return transaction;
    }

    private static void assertRelationship(
            String fieldName,
            String columnName,
            boolean optional,
            boolean updatable) throws Exception {

        Field field = TransactionExceptionLog.class
                .getDeclaredField(fieldName);
        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(relationship.optional()).isEqualTo(optional);
        assertThat(joinColumn.name()).isEqualTo(columnName);
        assertThat(joinColumn.nullable()).isEqualTo(optional);
        assertThat(joinColumn.updatable()).isEqualTo(updatable);
    }

    private static void assertEnumColumn(
            String fieldName,
            String columnName,
            int length) throws Exception {

        Field field = TransactionExceptionLog.class
                .getDeclaredField(fieldName);
        assertThat(field.getAnnotation(Enumerated.class).value())
                .isEqualTo(EnumType.STRING);

        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
    }
}
