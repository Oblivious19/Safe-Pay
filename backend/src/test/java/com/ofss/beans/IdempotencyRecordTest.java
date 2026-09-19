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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

class IdempotencyRecordTest {

    private static final OffsetDateTime INPUT_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T10:30:15.123456789+05:30");

    private static final OffsetDateTime UTC_TIME =
            OffsetDateTime.parse(
                    "2026-09-16T05:00:15.123456Z");

    private static final String REQUEST_HASH = "a".repeat(64);

    @Test
    void definesSchemaBackedStatusesAndCanonicalOperations() {
        assertThat(IdempotencyStatus.values()).containsExactly(
                IdempotencyStatus.IN_PROGRESS,
                IdempotencyStatus.COMPLETED,
                IdempotencyStatus.FAILED);

        assertThat(IdempotencyStatus.IN_PROGRESS.isTerminal())
                .isFalse();
        assertThat(IdempotencyStatus.COMPLETED.isTerminal())
                .isTrue();
        assertThat(IdempotencyStatus.FAILED.isTerminal())
                .isTrue();

        assertThat(IdempotencyOperation.values()).allSatisfy(
                operation -> {
                    assertThat(operation.name())
                            .isEqualTo(operation.name().toUpperCase());
                    assertThat(operation.name().length())
                            .isLessThanOrEqualTo(50);
                });

        assertThat(IdempotencyOperation.values()).contains(
                IdempotencyOperation.TRANSACTION_CREATE,
                IdempotencyOperation.TRANSACTION_AUTHORIZE,
                IdempotencyOperation.TRANSACTION_CANCEL,
                IdempotencyOperation.OTP_ISSUE,
                IdempotencyOperation.OTP_VERIFY,
                IdempotencyOperation.OTP_RESEND,
                IdempotencyOperation.RISK_REVIEW_APPROVE,
                IdempotencyOperation.RISK_REVIEW_REJECT,
                IdempotencyOperation
                        .RISK_REVIEW_REQUEST_VERIFICATION,
                IdempotencyOperation.TRANSACTION_SETTLE);
    }

    @Test
    void mapsCanonicalTableSequenceAndUniqueScope()
            throws Exception {

        assertThat(IdempotencyRecord.class
                .getAnnotation(Entity.class)).isNotNull();

        Table table = IdempotencyRecord.class
                .getAnnotation(Table.class);

        assertThat(table.name()).isEqualTo("IDEMPOTENCY_RECORD");
        assertThat(table.schema()).isEqualTo("SAFEPAY_OWNER");
        assertThat(table.uniqueConstraints()).singleElement()
                .satisfies(constraint -> {
                    assertThat(constraint.name())
                            .isEqualTo("UK_IDEMPOTENCY_REQUEST");
                    assertThat(constraint.columnNames())
                            .containsExactly(
                                    "USER_ID",
                                    "OPERATION_CODE",
                                    "IDEMPOTENCY_KEY");
                });

        SequenceGenerator sequence = IdempotencyRecord.class
                .getAnnotation(SequenceGenerator.class);

        assertThat(sequence.name())
                .isEqualTo("idempotencyRecordSequence");
        assertThat(sequence.sequenceName())
                .isEqualTo(
                        "SAFEPAY_OWNER.SEQ_IDEMPOTENCY_RECORD_ID");
        assertThat(sequence.allocationSize()).isEqualTo(1);
    }

    @Test
    void mapsRelationshipsLifecycleResponseAndVersion()
            throws Exception {

        Field user = IdempotencyRecord.class
                .getDeclaredField("user");
        ManyToOne userRelationship = user.getAnnotation(
                ManyToOne.class);
        JoinColumn userColumn = user.getAnnotation(
                JoinColumn.class);

        assertThat(userRelationship.fetch())
                .isEqualTo(FetchType.LAZY);
        assertThat(userRelationship.optional()).isFalse();
        assertThat(userColumn.name()).isEqualTo("USER_ID");
        assertThat(userColumn.nullable()).isFalse();
        assertThat(userColumn.updatable()).isFalse();

        Field transaction = IdempotencyRecord.class
                .getDeclaredField("transaction");
        assertThat(transaction.getAnnotation(ManyToOne.class)
                .fetch()).isEqualTo(FetchType.LAZY);
        assertThat(transaction.getAnnotation(JoinColumn.class)
                .name()).isEqualTo("TRANSACTION_ID");

        assertEnumColumn(
                "operationCode",
                "OPERATION_CODE",
                50);
        assertEnumColumn("status", "STATUS", 20);

        assertThat(IdempotencyRecord.class
                .getDeclaredField("responseBody")
                .getAnnotation(Lob.class)).isNotNull();

        Field version = IdempotencyRecord.class
                .getDeclaredField("versionNo");

        assertThat(version.getAnnotation(Version.class)).isNotNull();
        assertThat(version.getAnnotation(Column.class).name())
                .isEqualTo("VERSION_NO");
        assertThat(version.getAnnotation(Column.class).precision())
                .isEqualTo(10);
    }

    @Test
    void beginsNormalizedInProgressRecordWithoutChoosingRetention() {
        User user = persistedUser(7L);

        IdempotencyRecord record = IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "  Request-Key-AbC  ",
                "A".repeat(64),
                "  correlation-123  ",
                INPUT_TIME,
                INPUT_TIME.plusHours(3));

        assertThat(record.getUser()).isSameAs(user);
        assertThat(record.getOperationCode())
                .isEqualTo(
                        IdempotencyOperation.TRANSACTION_CREATE);
        assertThat(record.getIdempotencyKey())
                .isEqualTo("Request-Key-AbC");
        assertThat(record.getRequestHash())
                .isEqualTo(REQUEST_HASH);
        assertThat(record.getCorrelationId())
                .isEqualTo("correlation-123");
        assertThat(record.getStatus())
                .isEqualTo(IdempotencyStatus.IN_PROGRESS);
        assertThat(record.getHttpStatus()).isNull();
        assertThat(record.getResponseBody()).isNull();
        assertThat(record.getCompletedAt()).isNull();
        assertThat(record.getCreatedAt()).isEqualTo(UTC_TIME);
        assertThat(record.getExpiresAt())
                .isEqualTo(UTC_TIME.plusHours(3));
    }

    @Test
    void rejectsInvalidIdentityAndFingerprintFields() {
        User user = persistedUser(7L);

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                persistedUser(null),
                IdempotencyOperation.TRANSACTION_CREATE,
                "key",
                REQUEST_HASH,
                "correlation",
                INPUT_TIME,
                INPUT_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user must already be persisted");

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                null,
                "key",
                REQUEST_HASH,
                "correlation",
                INPUT_TIME,
                INPUT_TIME.plusMinutes(1)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operationCode is required");

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "   ",
                REQUEST_HASH,
                "correlation",
                INPUT_TIME,
                INPUT_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey is required");

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "key",
                "not-a-sha-256-hash",
                "correlation",
                INPUT_TIME,
                INPUT_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64-character hexadecimal");

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "key",
                REQUEST_HASH,
                "x".repeat(65),
                INPUT_TIME,
                INPUT_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId has an invalid length");
    }

    @Test
    void rejectsNonFutureExpiryWithoutInventingDuration() {
        User user = persistedUser(7L);

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "key",
                REQUEST_HASH,
                "correlation",
                INPUT_TIME,
                INPUT_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after createdAt");

        assertThatThrownBy(() -> IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "key",
                REQUEST_HASH,
                "correlation",
                INPUT_TIME,
                INPUT_TIME.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after createdAt");
    }

    @Test
    void comparesNormalizedSha256Values() {
        IdempotencyRecord record = validRecord();

        assertThat(record.matchesRequestHash(
                "A".repeat(64))).isTrue();
        assertThat(record.matchesRequestHash(
                "b".repeat(64))).isFalse();

        assertThatThrownBy(() ->
                record.matchesRequestHash("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linksOnlyPersistedOwnedTransaction() {
        User user = persistedUser(7L);
        IdempotencyRecord record = validRecord(user);
        TransactionDb transaction = persistedTransaction(
                101L,
                user);

        record.linkTransaction(transaction);
        record.linkTransaction(transaction);

        assertThat(record.getTransaction())
                .isSameAs(transaction);

        assertThatThrownBy(() -> record.linkTransaction(
                persistedTransaction(
                        102L,
                        persistedUser(8L))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "transaction must belong to idempotency user");
    }

    @Test
    void rejectsReplacementWithAnotherOwnedTransaction() {
        User user = persistedUser(7L);
        IdempotencyRecord record = validRecord(user);

        record.linkTransaction(persistedTransaction(101L, user));

        assertThatThrownBy(() -> record.linkTransaction(
                persistedTransaction(102L, user)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void completesOnceAndRetainsExactResponseIdentity() {
        IdempotencyRecord record = validRecord();
        String response = "{\"transactionId\":\"101\"}";
        OffsetDateTime completedAt = INPUT_TIME.plusSeconds(1);

        record.markCompleted(201, response, completedAt);

        assertThat(record.getStatus())
                .isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(record.getHttpStatus()).isEqualTo(201);
        assertThat(record.getResponseBody()).isEqualTo(response);
        assertThat(record.getCompletedAt())
                .isEqualTo(
                        UTC_TIME.plusSeconds(1));

        assertThatThrownBy(() -> record.markCompleted(
                201,
                response,
                completedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in-progress");
    }

    @Test
    void recordsFailureAndEnforcesDatabaseLifecycleBounds() {
        IdempotencyRecord record = validRecord();

        assertThatThrownBy(() -> record.markFailed(
                99,
                null,
                INPUT_TIME.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "httpStatus must be between 100 and 599");

        assertThatThrownBy(() -> record.markFailed(
                422,
                null,
                INPUT_TIME.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "completedAt cannot be before createdAt");

        record.markFailed(
                422,
                "{\"errorCode\":\"BUSINESS_RULE\"}",
                INPUT_TIME.plusSeconds(1));

        assertThat(record.getStatus())
                .isEqualTo(IdempotencyStatus.FAILED);
        assertThat(record.getCompletedAt()).isNotNull();
    }

    @Test
    void treatsExpiryBoundaryAsExpired() {
        IdempotencyRecord record = validRecord();

        assertThat(record.isExpiredAt(
                INPUT_TIME.plusMinutes(59))).isFalse();
        assertThat(record.isExpiredAt(
                INPUT_TIME.plusHours(1))).isTrue();
        assertThat(record.isExpiredAt(
                INPUT_TIME.plusHours(2))).isTrue();
    }

    private static IdempotencyRecord validRecord() {
        return validRecord(persistedUser(7L));
    }

    private static IdempotencyRecord validRecord(User user) {
        return IdempotencyRecord.begin(
                user,
                IdempotencyOperation.TRANSACTION_CREATE,
                "request-key",
                REQUEST_HASH,
                "correlation-123",
                INPUT_TIME,
                INPUT_TIME.plusHours(1));
    }

    private static User persistedUser(Long userId) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(userId);
        return user;
    }

    private static TransactionDb persistedTransaction(
            Long transactionId,
            User user) {

        TransactionDb transaction = mock(TransactionDb.class);
        when(transaction.getTransactionId())
                .thenReturn(transactionId);
        when(transaction.getCustomer()).thenReturn(user);
        return transaction;
    }

    private static void assertEnumColumn(
            String fieldName,
            String columnName,
            int length) throws Exception {

        Field field = IdempotencyRecord.class
                .getDeclaredField(fieldName);

        assertThat(field.getAnnotation(Enumerated.class).value())
                .isEqualTo(EnumType.STRING);

        Column column = field.getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
    }
}
